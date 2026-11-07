package com.kasperstudios.kvr.session;

import com.kasperstudios.kvr.KVR;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side session hygiene for the static state of KVR systems (0030).
 *
 * <ul>
 *   <li><b>Server identity.</b> In single player every world you open is a NEW {@link MinecraftServer} in the same
 *       JVM, while our maps are static. {@link Binding#rebind} tells a system "this is another server", so it drops
 *       nodes, calls and rate counters of the previous one (they still hold dead {@link ServerPlayer} objects).</li>
 *   <li><b>Players that left.</b> {@link #online} gives the UUIDs online right now, for {@code retainAll}.</li>
 *   <li><b>Never crash the server from a tick.</b> An exception thrown out of a server-tick callback crashes the
 *       whole server; {@link #guard} logs it (at most once per 10 s per system) and keeps the server running.</li>
 * </ul>
 */
public final class ServerSession {

    private ServerSession() {}

    /** One per system: remembers which server its static state belongs to. */
    public static final class Binding {
        private MinecraftServer bound;

        /** True when {@code server} is not the one the state belongs to (first call included): reset now. */
        public boolean rebind(MinecraftServer server) {
            if (bound == server) return false;
            bound = server;
            return true;
        }
    }

    public static Set<UUID> online(MinecraftServer server) {
        Set<UUID> out = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) out.add(p.getUUID());
        return out;
    }

    /** Wraps a server-tick callback so one bad tick (a mod API that changed, a player that just left) is logged, not fatal. */
    public static Consumer<MinecraftServer> guard(String system, Consumer<MinecraftServer> tick) {
        long[] lastLog = {0};
        return server -> {
            try {
                tick.accept(server);
            } catch (RuntimeException | LinkageError e) {
                long now = System.currentTimeMillis();
                if (now - lastLog[0] > 10_000) {
                    lastLog[0] = now;
                    KVR.logger().error("KVR {}: server tick failed, continuing", system, e);
                }
            }
        };
    }
}
