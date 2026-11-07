package com.kasperstudios.kvr.session;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Client-side session hygiene (0030): when the connection to a server ends (disconnect, kick, leaving a single
 * player world, switching servers) every registered system drops its per-server state, so nothing from the old
 * server (modem online, call ringing, desktop bridge) leaks into the next one and no packet goes to a server that
 * never heard of the channel.
 *
 * <p>Detection is by connection identity on every client tick ({@code Minecraft#getConnection()} changes on
 * disconnect and on every new join; dimension changes keep the same connection). Works on every loader and node
 * without a disconnect event in the facade. Systems register with {@link #onLeave} from their {@code initClient()}.
 */
public final class ClientSession {

    private static final List<Runnable> ON_LEAVE = new ArrayList<>();
    private static boolean installed;
    private static Object connection;

    private ClientSession() {}

    /** Idempotent; the first caller hooks the client tick. */
    public static synchronized void onLeave(Runnable reset) {
        ON_LEAVE.add(reset);
        if (!installed) {
            installed = true;
            KVRClient.get().onClientTickStart(ClientSession::tick);
        }
    }

    private static void tick() {
        Object now = Minecraft.getInstance().getConnection();
        if (now == connection) return;
        Object before = connection;
        connection = now;
        if (before != null) leave();
    }

    private static void leave() {
        for (Runnable r : ON_LEAVE) {
            try {
                r.run();
            } catch (RuntimeException | LinkageError e) {
                KVR.logger().warn("KVR: session reset failed: {}", e.toString());
            }
        }
    }
}
