package com.kasperstudios.kvr.messenger;

import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.vfs.VfsException;
import com.kasperstudios.kvr.vfs.VfsPath;
import com.kasperstudios.kvr.vfs.VfsServer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server relay of the messenger. Both players must wear KVR glasses with a running OS. An attachment
 * is read from the sender's VFS ({@code /home} only) and written into the recipient's
 * {@code /home/inbox/} — inside the recipient's own quota, so a full disk refuses the file.
 * Flood guard: 5 messages per 5 seconds per player. Common init: {@code MessengerServer.init()}.
 */
public final class MessengerServer {

    private static final int WINDOW_TICKS = 100;
    private static final int MAX_PER_WINDOW = 5;
    private static final Map<UUID, int[]> RATE = new HashMap<>();

    private MessengerServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(MessengerProtocol.SEND, (player, buf) -> {
            MessengerProtocol.Send msg = MessengerProtocol.Send.read(buf);
            MinecraftServer server = player.getServer();
            if (server != null) server.execute(() -> {
                MessengerProtocol.Ack ack = handle(server, player, msg);
                KVRNetwork.sendToPlayer(player, MessengerProtocol.ACK, ack::write);
            });
        });
    }

    static MessengerProtocol.Ack handle(MinecraftServer server, ServerPlayer from, MessengerProtocol.Send msg) {
        if (!allowed(server, from)) return new MessengerProtocol.Ack(msg.req(), false, "RATE_LIMIT");
        Optional<VfsServer.Context> src = VfsServer.context(from);
        if (src.isEmpty()) return new MessengerProtocol.Ack(msg.req(), false, "NO_GLASSES");
        ServerPlayer to = server.getPlayerList().getPlayerByName(msg.to());
        if (to == null || to == from) return new MessengerProtocol.Ack(msg.req(), false, "OFFLINE");
        Optional<VfsServer.Context> dst = VfsServer.context(to);
        if (dst.isEmpty()) return new MessengerProtocol.Ack(msg.req(), false, "RECIPIENT_GLASSES_OFF");

        String delivered = "";
        if (!msg.attach().isEmpty()) {
            try {
                String path = VfsPath.normalize(msg.attach());
                if (!path.startsWith("/home/") || path.startsWith("/home/inbox/")) throw new VfsException(VfsException.Code.DENIED, path);
                String text = src.get().store().read(path);
                String name = path.substring(path.lastIndexOf('/') + 1);
                delivered = freeName(dst.get(), "/home/inbox/" + from.getName().getString() + "-" + name);
                dst.get().store().write(delivered, text, dst.get().quota());
            } catch (VfsException e) {
                return new MessengerProtocol.Ack(msg.req(), false, e.code() == VfsException.Code.QUOTA_BYTES || e.code() == VfsException.Code.QUOTA_FILES ? "RECIPIENT_FULL" : e.code().name());
            }
        }
        MessengerProtocol.Receive rec = new MessengerProtocol.Receive(from.getName().getString(), msg.text(), delivered);
        KVRNetwork.sendToPlayer(to, MessengerProtocol.RECEIVE, rec::write);
        return new MessengerProtocol.Ack(msg.req(), true, "");
    }

    private static String freeName(VfsServer.Context ctx, String wanted) throws VfsException {
        String base = VfsPath.normalize(wanted);
        String candidate = base;
        for (int i = 1; i < 50; i++) {
            try {
                ctx.store().read(candidate);
            } catch (VfsException e) {
                if (e.code() == VfsException.Code.NOT_FOUND) return candidate;
                throw e;
            }
            int dot = base.lastIndexOf('.');
            candidate = dot > base.lastIndexOf('/') ? base.substring(0, dot) + "-" + i + base.substring(dot) : base + "-" + i;
        }
        throw new VfsException(VfsException.Code.QUOTA_FILES, "inbox name collision");
    }

    private static boolean allowed(MinecraftServer server, ServerPlayer p) {
        int window = server.getTickCount() / WINDOW_TICKS;
        int[] r = RATE.computeIfAbsent(p.getUUID(), k -> new int[] {window, 0});
        if (r[0] != window) {
            r[0] = window;
            r[1] = 0;
        }
        return ++r[1] <= MAX_PER_WINDOW;
    }
}
