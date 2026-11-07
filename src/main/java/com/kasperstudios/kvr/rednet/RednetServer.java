package com.kasperstudios.kvr.rednet;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.compat.VersionCompat;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.platform.KVRServer;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.session.ServerSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of "glasses = mobile Rednet node" (SPEC 6.2). Once a second every online player is checked:
 * worn glasses with a running OS + a CC modem in the slot → the glasses are attached to the CC wireless network
 * as a receiver at the player's eyes (wireless: 64 blocks in the dimension, ender: everywhere). Taking the glasses
 * off, powering down or removing the modem detaches them.
 *
 * <p>Wire format = CC: Tweaked {@code rednet.lua}, so ordinary Lua programs talk to the glasses:
 * a message is the table {@code {nMessageID, nRecipient, nSender, message, sProtocol}} sent on
 * {@code recipient % 65500} (or 65535 = broadcast) plus a copy on 65533 for repeaters; the reply channel is our id.
 * We accept our id channel (with {@code nRecipient == id}) and the broadcast channel, de-duplicated by
 * {@code nMessageID} like rednet.run does. Raw {@code modem.transmit} packets that are not rednet are ignored.
 *
 * <p>Abuse limits: 10 sends per second per glasses, {@link RednetPayload} size limits both ways.
 * Common init: {@code RednetServer.init()} (in {@code KVRCommonBootstrap}).
 */
public final class RednetServer {

    private static final int MAX_PER_SECOND = 10;
    private static final int SEEN = 256;

    /** Server thread only. */
    private static final Map<UUID, Node> NODES = new HashMap<>();
    /** Last state sent to each player (object identity: a re-login is a new ServerPlayer → resend). */
    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private record Sent(ServerPlayer player, RednetProtocol.State state) {}

    private static final ServerSession.Binding SESSION = new ServerSession.Binding();

    private RednetServer() {}

    public static void init() {
        KVRRednet.tryInstallComputerCraft();
        KVRNetwork.registerServerReceiver(RednetProtocol.SEND, (player, buf) -> {
            RednetProtocol.Send msg = RednetProtocol.Send.read(buf);
            MinecraftServer server = VersionCompat.server(player);
            if (server != null) server.execute(() -> send(player, msg));
        });
        KVRServer.get().onServerTick(ServerSession.guard("Rednet", RednetServer::tick));
    }

    /** Another server in this JVM (single player: next world): detach everything of the previous one. */
    static void resetAll() {
        for (Node n : NODES.values()) n.detach();
        NODES.clear();
        SENT.clear();
    }

    // --- attach / detach -----------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (SESSION.rebind(server)) resetAll();
        for (Node n : NODES.values()) n.refresh(); // CC reads the position from its own threads
        if (server.getTickCount() % 20 != 0) return;
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(p.getUUID());
            try {
                sync(p);
            } catch (RuntimeException | LinkageError e) {
                // one player's broken stack / CC hiccup must not stop the others
                KVR.logger().debug("KVR Rednet: sync failed for {}: {}", p.getName().getString(), e.toString());
            }
        }
        for (Iterator<Map.Entry<UUID, Node>> it = NODES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Node> e = it.next();
            if (!online.contains(e.getKey())) {
                e.getValue().detach();
                it.remove();
            }
        }
        SENT.keySet().retainAll(online);
    }

    private static void sync(ServerPlayer p) {
        ItemStack glasses = p.getItemBySlot(EquipmentSlot.HEAD);
        boolean active = GlassesPower.build(glasses).isPresent() && GlassesPower.active(glasses, p.level().getGameTime());
        Optional<GlassesModem.Modem> modem = active ? GlassesModem.read(glasses) : Optional.empty();
        Optional<KVRRednet.Provider> provider = KVRRednet.provider();

        Node node = NODES.get(p.getUUID());
        boolean want = provider.isPresent() && modem.isPresent();
        if (node != null && (!want || node.player != p || node.id != modem.get().id()
            || node.ender != (modem.get().kind() == GlassesModem.Kind.ENDER))) {
            node.detach();
            NODES.remove(p.getUUID());
            node = null;
        }
        if (want && node == null) {
            node = new Node(p, modem.get().id(), modem.get().kind() == GlassesModem.Kind.ENDER);
            try {
                node.endpoint = provider.get().attach(p, node, node::inbound);
                NODES.put(p.getUUID(), node);
            } catch (RuntimeException | LinkageError e) {
                KVR.logger().warn("KVR Rednet: attach failed for {}: {}", p.getName().getString(), e.toString());
            }
        }

        int id = modem.map(GlassesModem.Modem::id).orElseGet(() -> glasses.isEmpty() ? -1 : GlassesModem.rememberedId(glasses));
        RednetProtocol.State st = new RednetProtocol.State(provider.isPresent(), modem.isPresent(),
            modem.map(m -> m.kind() == GlassesModem.Kind.ENDER).orElse(false), id);
        Sent last = SENT.get(p.getUUID());
        if (last == null || last.player() != p || !last.state().equals(st)) {
            // vanilla client / no KVR on it: nothing to tell (and no unknown payload that would kick it)
            if (KVRNetwork.sendIfAccepted(p, RednetProtocol.STATE, st::write)) SENT.put(p.getUUID(), new Sent(p, st));
        }
    }

    // --- glasses → network ---------------------------------------------------------------------

    static void send(ServerPlayer player, RednetProtocol.Send msg) {
        Node node = NODES.get(player.getUUID());
        if (node == null || node.player != player || node.endpoint == null) return;
        if (!node.allow(VersionCompat.server(player).getTickCount() / 20)) return;
        if (msg.json().length() > RednetPayload.MAX_JSON) return;
        Object lua;
        try {
            lua = RednetPayload.toLua(JsonParser.parseString(msg.json()));
        } catch (JsonParseException | RednetPayload.TooBig e) {
            return;
        }
        boolean broadcast = msg.to() < 0;
        int recipient = broadcast ? RednetProtocol.CHANNEL_BROADCAST : msg.to();
        double messageId = 1 + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE - 1);
        Map<Object, Object> wrapper = new HashMap<>();
        wrapper.put("nMessageID", messageId);
        wrapper.put("nRecipient", (double) recipient);
        wrapper.put("nSender", (double) node.id);
        if (lua != null) wrapper.put("message", lua);
        String protocol = clamp(msg.protocol(), RednetProtocol.MAX_PROTOCOL);
        if (!protocol.isEmpty()) wrapper.put("sProtocol", protocol);
        node.firstSeen(messageId); // a repeater's echo of our own message is not "received"
        int channel = broadcast ? RednetProtocol.CHANNEL_BROADCAST : recipient % RednetProtocol.MAX_ID_CHANNELS;
        try {
            node.endpoint.transmit(channel, node.id, wrapper);
            node.endpoint.transmit(RednetProtocol.CHANNEL_REPEAT, node.id, wrapper);
        } catch (RuntimeException | LinkageError e) {
            KVR.logger().debug("KVR Rednet: transmit failed: {}", e.toString());
        }
    }

    // --- network → glasses ---------------------------------------------------------------------

    /** Server thread. Decodes the rednet.lua table and forwards it to the player. */
    static void deliver(Node node, int channel, int replyChannel, JsonElement json, double distance) {
        if (NODES.get(node.player.getUUID()) != node || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        Optional<Double> messageId = num(o, "nMessageID");
        if (messageId.isEmpty()) return; // a raw modem.transmit, not rednet
        boolean broadcast = channel == RednetProtocol.CHANNEL_BROADCAST;
        if (!broadcast) {
            if (channel != node.id % RednetProtocol.MAX_ID_CHANNELS) return;
            if (num(o, "nRecipient").map(r -> r != node.id).orElse(true)) return;
        }
        if (!node.firstSeen(messageId.get())) return;
        int sender = num(o, "nSender").map(Double::intValue).orElse(replyChannel);
        JsonElement p = o.get("sProtocol");
        String protocol = p != null && p.isJsonPrimitive() ? clamp(p.getAsString(), RednetProtocol.MAX_PROTOCOL) : "";
        JsonElement message = o.has("message") ? o.get("message") : JsonNull.INSTANCE;
        String text = message.toString();
        if (text.length() > RednetPayload.MAX_JSON) return;
        RednetProtocol.Receive r = new RednetProtocol.Receive(sender, protocol, text, broadcast, distance < 0 ? -1 : (int) Math.round(distance));
        KVRNetwork.sendIfAccepted(node.player, RednetProtocol.RECEIVE, r::write);
    }

    private static Optional<Double> num(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return Optional.empty();
        double d = e.getAsDouble();
        return Double.isNaN(d) ? Optional.empty() : Optional.of(d);
    }

    private static String clamp(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // --- node ----------------------------------------------------------------------------------

    static final class Node implements KVRRednet.Placement {
        final ServerPlayer player;
        final MinecraftServer server;
        final int id;
        final boolean ender;
        volatile Level level;
        volatile Vec3 position;
        /** Read from CC computer threads in {@link #inbound}. */
        volatile KVRRednet.Endpoint endpoint;
        private final Map<Double, Boolean> seen = new LinkedHashMap<>(64, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Double, Boolean> eldest) {
                return size() > SEEN;
            }
        };
        private int rateSecond = -1;
        private int rateCount;

        Node(ServerPlayer player, int id, boolean ender) {
            this.player = player;
            this.server = VersionCompat.server(player);
            this.id = id;
            this.ender = ender;
            refresh();
        }

        void refresh() {
            level = player.level();
            position = player.getEyePosition();
        }

        @Override public Level level() { return level; }
        @Override public Vec3 position() { return position; }
        @Override public boolean ender() { return ender; }
        @Override public int rednetId() { return id; }

        /** Any thread: convert now (CC's map is immutable after transmit), deliver on the server thread. */
        void inbound(int channel, int replyChannel, Object payload, double distance) {
            if (endpoint == null) return; // detached meanwhile (player left, server stopping)
            JsonElement json;
            try {
                json = RednetPayload.fromLua(payload);
            } catch (RednetPayload.TooBig e) {
                return;
            }
            server.execute(() -> deliver(this, channel, replyChannel, json, distance));
        }

        boolean firstSeen(double messageId) {
            return seen.put(messageId, Boolean.TRUE) == null;
        }

        boolean allow(int second) {
            if (second != rateSecond) {
                rateSecond = second;
                rateCount = 0;
            }
            return ++rateCount <= MAX_PER_SECOND;
        }

        void detach() {
            KVRRednet.Endpoint e = endpoint;
            endpoint = null;
            if (e != null) {
                try {
                    e.detach();
                } catch (RuntimeException | LinkageError ex) {
                    KVR.logger().debug("KVR Rednet: detach failed: {}", ex.toString());
                }
            }
        }
    }
}
