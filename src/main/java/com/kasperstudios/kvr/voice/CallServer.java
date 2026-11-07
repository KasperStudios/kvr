package com.kasperstudios.kvr.voice;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.compat.VersionCompat;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.platform.KVRServer;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.session.ServerSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Calls (SPEC 6.3, app {@code kvr.calls}): the server keeps the call, the voice mod carries the audio.
 *
 * <ul>
 *   <li>INVITE: caller and callee need worn glasses with a running OS and a connected voice client; the callee
 *       rings for 30 s (MISSED / NO_ANSWER after that). Up to {@link CallProtocol#MAX_MEMBERS} participants, more
 *       can be added during the call (ADD).</li>
 *   <li>The first ACCEPT creates the private voice group and moves everyone who joined into it; later accepts join it.</li>
 *   <li>Leaving: HANGUP, glasses off / taken off, logout, voice client gone, or leaving the group in the SVC menu.
 *       When one participant is left (and nobody is ringing) the call ends for him too and the group is removed.</li>
 *   <li>Every change, and "who is talking" (checked 5×/s), is pushed to each participant as a personal
 *       {@link CallProtocol.State}.</li>
 * </ul>
 * Players are put OUT of any group when the call ends (their pre-call group is not restored — SVC may already
 * have deleted it). Common init: {@code CallServer.init()}.
 */
public final class CallServer {

    static final int RING_TICKS = 30 * 20;
    private static final int RATE_PER_SECOND = 5;

    /** Server thread only. */
    private static final Map<Integer, Call> CALLS = new HashMap<>();
    private static final Map<UUID, Call> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, int[]> RATE = new HashMap<>();
    private static final ServerSession.Binding SESSION = new ServerSession.Binding();
    private static int seq;

    private static final class Call {
        final int id;
        final String callerName;
        final Map<UUID, String> names = new LinkedHashMap<>();
        final Set<UUID> joined = new LinkedHashSet<>();
        /** Ringing invitee → tick when the ring times out. */
        final Map<UUID, Integer> ringing = new LinkedHashMap<>();
        @Nullable UUID group;
        Set<UUID> speaking = Set.of();

        Call(int id, String callerName) {
            this.id = id;
            this.callerName = callerName;
        }
    }

    private CallServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(CallProtocol.OP, (player, buf) -> {
            CallProtocol.Request r = CallProtocol.Request.read(buf);
            MinecraftServer server = VersionCompat.server(player);
            if (server != null) server.execute(() -> handle(server, player, r));
        });
        KVRServer.get().onServerTick(ServerSession.guard("Calls", CallServer::tick));
    }

    /** Another server in this JVM: forget the previous one's calls (no backend calls: its voice server is gone). */
    static void resetAll() {
        CALLS.clear();
        BY_PLAYER.clear();
        RATE.clear();
    }

    // --- voice backend, never fatal ------------------------------------------------------------

    /** SVC may throw for a player who disconnected a moment ago; a failed move must not break the call logic. */
    private static void voiceJoin(UUID player, @Nullable UUID group) {
        KVRVoice.backend().ifPresent(v -> {
            try {
                v.join(player, group);
            } catch (RuntimeException | LinkageError e) {
                KVR.logger().debug("KVR calls: voice join({}, {}) failed: {}", player, group, e.toString());
            }
        });
    }

    private static void voiceRemoveGroup(UUID group) {
        KVRVoice.backend().ifPresent(v -> {
            try {
                v.removeGroup(group);
            } catch (RuntimeException | LinkageError e) {
                KVR.logger().debug("KVR calls: removeGroup failed: {}", e.toString());
            }
        });
    }

    // --- requests ------------------------------------------------------------------------------

    static void handle(MinecraftServer server, ServerPlayer p, CallProtocol.Request r) {
        UUID me = p.getUUID();
        Call call = BY_PLAYER.get(me);
        if (!allowed(server, me)) {
            if (call == null) notify(p, "RATE_LIMIT");
            return;
        }
        Optional<KVRVoice.Backend> voice = KVRVoice.backend();
        switch (r.op()) {
            case INVITE, ADD -> {
                if (call != null) {
                    if (call.joined.contains(me)) {
                        String fail = invite(server, call, p, r.names(), voice.orElse(null));
                        push(server, call, me, fail == null ? "" : fail);
                    }
                    return; // still ringing: accept or decline first
                }
                if (r.op() == CallProtocol.Op.ADD) return;
                if (voice.isEmpty()) {
                    notify(p, "NO_VOICE");
                    return;
                }
                String why = unfit(p, voice.get());
                if (why != null) {
                    notify(p, why);
                    return;
                }
                Call c = new Call(++seq, p.getName().getString());
                CALLS.put(c.id, c);
                BY_PLAYER.put(me, c);
                c.names.put(me, c.callerName);
                c.joined.add(me);
                String fail = invite(server, c, p, r.names(), voice.get());
                if (c.ringing.isEmpty()) end(server, c, fail == null ? "OFFLINE" : fail);
                else push(server, c, me, fail == null ? "" : fail);
            }
            case ACCEPT -> {
                if (call == null || call.id != r.callId() || !call.ringing.containsKey(me)) return;
                if (voice.isEmpty()) {
                    end(server, call, "NO_VOICE");
                    return;
                }
                String why = unfit(p, voice.get());
                if (why != null) {
                    drop(server, call, me, why, "PEER_" + why);
                    return;
                }
                call.ringing.remove(me);
                call.joined.add(me);
                if (call.group == null) {
                    UUID group;
                    try {
                        group = voice.get().createGroup("KVR call " + call.id);
                    } catch (RuntimeException | LinkageError e) {
                        KVR.logger().warn("KVR calls: voice group could not be created: {}", e.toString());
                        end(server, call, "NO_VOICE");
                        return;
                    }
                    call.group = group;
                    for (UUID u : call.joined) voiceJoin(u, group);
                } else {
                    voiceJoin(me, call.group);
                }
                push(server, call, null, "");
            }
            case DECLINE -> {
                if (call != null && call.id == r.callId() && call.ringing.containsKey(me)) drop(server, call, me, "DECLINED", "DECLINED");
            }
            case HANGUP -> {
                if (call != null && call.id == r.callId()) drop(server, call, me, "HUNG_UP", "HUNG_UP");
            }
        }
    }

    /** Rings everyone it can; returns the first failure (for the inviter's status line) or null. */
    @Nullable
    private static String invite(MinecraftServer server, Call call, ServerPlayer inviter, List<String> names, @Nullable KVRVoice.Backend voice) {
        if (voice == null) return "NO_VOICE";
        String fail = null;
        for (String n : names) {
            if (call.names.size() >= CallProtocol.MAX_MEMBERS) return fail == null ? "FULL" : fail;
            ServerPlayer t = server.getPlayerList().getPlayerByName(n);
            String why;
            if (t == null || t == inviter) why = "OFFLINE";
            else if (BY_PLAYER.containsKey(t.getUUID())) why = "BUSY";
            else {
                String u = unfit(t, voice);
                why = u == null ? null : "PEER_" + u;
            }
            if (why != null) {
                if (fail == null) fail = why;
                continue;
            }
            call.names.put(t.getUUID(), t.getName().getString());
            call.ringing.put(t.getUUID(), server.getTickCount() + RING_TICKS);
            BY_PLAYER.put(t.getUUID(), call);
        }
        return fail;
    }

    // --- leaving / ending ----------------------------------------------------------------------

    /** One participant leaves (or stops ringing); ends the call if it no longer makes sense. */
    private static void drop(MinecraftServer server, Call call, UUID who, String reasonSelf, String reasonOthers) {
        boolean wasJoined = call.joined.remove(who);
        call.ringing.remove(who);
        if (BY_PLAYER.get(who) == call) BY_PLAYER.remove(who);
        if (wasJoined && call.group != null) voiceJoin(who, null);
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) send(p, new CallProtocol.State(call.id, CallProtocol.Status.ENDED, call.callerName, members(call), reasonSelf));
        call.names.remove(who);
        if (call.joined.isEmpty() || (call.joined.size() == 1 && call.ringing.isEmpty())) end(server, call, reasonOthers);
        else push(server, call, null, "");
    }

    private static void end(MinecraftServer server, Call call, String reason) {
        CALLS.remove(call.id);
        List<CallProtocol.Member> members = members(call);
        for (UUID u : new ArrayList<>(call.names.keySet())) {
            if (BY_PLAYER.get(u) == call) BY_PLAYER.remove(u);
            if (call.joined.contains(u) && call.group != null) voiceJoin(u, null);
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) send(p, new CallProtocol.State(call.id, CallProtocol.Status.ENDED, call.callerName, members, reason));
        }
        if (call.group != null) voiceRemoveGroup(call.group);
        call.joined.clear();
        call.ringing.clear();
    }

    // --- tick ----------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (SESSION.rebind(server)) resetAll();
        int now = server.getTickCount();
        if (now % 200 == 0) {
            // players who left: rate counters and "speaking" stamps (calls drop them below)
            java.util.Set<UUID> online = ServerSession.online(server);
            RATE.keySet().retainAll(online);
            KVRVoice.retainOnly(online);
        }
        if (CALLS.isEmpty()) return;
        boolean second = now % 20 == 0;
        boolean talk = now % 4 == 0;
        for (Call call : new ArrayList<>(CALLS.values())) {
            Optional<KVRVoice.Backend> voice = KVRVoice.backend();
            if (voice.isEmpty()) {
                end(server, call, "NO_VOICE");
                continue;
            }
            if (second) {
                for (UUID u : new ArrayList<>(call.ringing.keySet())) {
                    if (!CALLS.containsKey(call.id)) break;
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p == null || unfit(p, voice.get()) != null) drop(server, call, u, "NO_GLASSES", "OFFLINE");
                    else if (call.ringing.getOrDefault(u, now) <= now) drop(server, call, u, "MISSED", "NO_ANSWER");
                }
                for (UUID u : new ArrayList<>(call.joined)) {
                    if (!CALLS.containsKey(call.id)) break;
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    String why = p == null ? "OFFLINE" : unfit(p, voice.get());
                    if (why == null && call.group != null && !call.group.equals(groupOf(voice.get(), u))) why = "LEFT_GROUP";
                    if (why != null) drop(server, call, u, why, "HUNG_UP");
                }
            }
            if (talk && CALLS.containsKey(call.id) && call.group != null) {
                Set<UUID> s = new HashSet<>();
                for (UUID u : call.joined) if (KVRVoice.speaking(u)) s.add(u);
                if (!s.equals(call.speaking)) {
                    call.speaking = s;
                    push(server, call, null, "");
                }
            }
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Null if the player can be in a call, else the reason. */
    @Nullable
    private static String unfit(ServerPlayer p, KVRVoice.Backend voice) {
        ItemStack glasses = p.getItemBySlot(EquipmentSlot.HEAD);
        if (GlassesPower.build(glasses).isEmpty() || !GlassesPower.active(glasses, p.level().getGameTime())) return "NO_GLASSES";
        boolean connected;
        try {
            connected = voice.connected(p.getUUID());
        } catch (RuntimeException | LinkageError e) {
            connected = false;
        }
        if (!connected) return "NO_VOICE_CLIENT";
        return null;
    }

    @Nullable
    private static UUID groupOf(KVRVoice.Backend voice, UUID player) {
        try {
            return voice.groupOf(player);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static List<CallProtocol.Member> members(Call call) {
        List<CallProtocol.Member> out = new ArrayList<>();
        call.names.forEach((u, n) -> out.add(new CallProtocol.Member(u, n, call.joined.contains(u), call.speaking.contains(u))));
        return List.copyOf(out);
    }

    /** Personal state to every participant; {@code note} only to {@code noteTo} (e.g. "BUSY" after an ADD). */
    private static void push(MinecraftServer server, Call call, @Nullable UUID noteTo, String note) {
        List<CallProtocol.Member> members = members(call);
        for (UUID u : call.names.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            CallProtocol.Status st = call.ringing.containsKey(u) ? CallProtocol.Status.RINGING_IN
                : call.group == null ? CallProtocol.Status.RINGING_OUT : CallProtocol.Status.ACTIVE;
            send(p, new CallProtocol.State(call.id, st, call.callerName, members, u.equals(noteTo) ? note : ""));
        }
    }

    private static void notify(ServerPlayer p, String reason) {
        send(p, new CallProtocol.State(0, CallProtocol.Status.ENDED, "", List.of(), reason));
    }

    private static void send(ServerPlayer p, CallProtocol.State s) {
        KVRNetwork.sendIfAccepted(p, CallProtocol.STATE, s::write);
    }

    private static boolean allowed(MinecraftServer server, UUID player) {
        int second = server.getTickCount() / 20;
        int[] r = RATE.computeIfAbsent(player, k -> new int[] {second, 0});
        if (r[0] != second) {
            r[0] = second;
            r[1] = 0;
        }
        return ++r[1] <= RATE_PER_SECOND;
    }
}
