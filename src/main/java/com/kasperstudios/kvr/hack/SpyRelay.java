package com.kasperstudios.kvr.hack;

import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.platform.KVRServer;
import com.kasperstudios.kvr.power.GlassesPower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Server relay of the MITM spy view. Every KVR client reports its windows at 2 Hz
 * ({@link #SUMMARY}); every 10 ticks each HackGlasses wearer gets the summary of the player in the
 * centre of their view within 32 blocks and line of sight ({@link #SPY_VIEW}).
 */
public final class SpyRelay {

    public static final ResourceLocation SUMMARY = id("window_summary");
    public static final ResourceLocation SPY_VIEW = id("spy_view");
    private static final int STALE_TICKS = 100;

    private record Stored(WindowSummary summary, int tick) {}

    private static final Map<UUID, Stored> SUMMARIES = new HashMap<>();

    private SpyRelay() {}

    /** Latest report of a player (server thread), shared with {@code PublicWindowRelay}. */
    @org.jetbrains.annotations.Nullable
    public static WindowSummary latest(UUID player) {
        Stored s = SUMMARIES.get(player);
        return s == null ? null : s.summary();
    }

    /** Common init (both sides), after {@code KVRServer} and {@code KVRNetwork} are installed. */
    public static void init() {
        KVRNetwork.registerServerReceiver(SUMMARY, (player, buf) -> {
            WindowSummary s = WindowSummary.read(buf);
            MinecraftServer server = player.getServer();
            if (server != null) server.execute(() -> SUMMARIES.put(player.getUUID(), new Stored(s, server.getTickCount())));
        });
        KVRNetwork.registerServerReceiver(HackProtocol.C2S_HACK_INPUT, (hacker, buf) -> {
            HackProtocol.InputInjection p = HackProtocol.InputInjection.read(buf);
            MinecraftServer server = hacker.getServer();
            if (server != null) server.execute(() -> handleHackInput(hacker, p));
        });
        KVRServer.get().onServerTick(SpyRelay::tick);
    }

    private static void handleHackInput(ServerPlayer hacker, HackProtocol.InputInjection p) {
        if (!HackGlasses.isWearing(hacker)) return;
        MinecraftServer server = hacker.getServer();
        if (server == null) return;
        ServerPlayer victim = server.getPlayerList().getPlayer(p.victim());
        if (victim == null || victim.level() != hacker.level() || victim.isSpectator()) return;
        if (hacker.distanceToSqr(victim) > HackGlasses.SPY_RANGE * HackGlasses.SPY_RANGE) return;
        if (!hacker.hasLineOfSight(victim)) return;

        // Relay injection packet to the victim
        KVRNetwork.sendToPlayer(victim, HackProtocol.S2C_INJECT_INPUT, p::write);
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % HackGlasses.SYNC_TICKS != 0) return;
        SUMMARIES.values().removeIf(s -> now - s.tick() > STALE_TICKS);

        for (ServerPlayer hacker : server.getPlayerList().getPlayers()) {
            if (!HackGlasses.isWearing(hacker)) continue;
            sendTargets(hacker);
            ServerPlayer victim = lookTarget(hacker);
            if (victim == null) continue;
            Stored s = SUMMARIES.get(victim.getUUID());
            if (s == null) continue;
            String name = victim.getName().getString();
            KVRNetwork.sendToPlayer(hacker, SPY_VIEW, buf -> {
                buf.writeUUID(victim.getUUID());
                buf.writeUtf(name, 32);
                s.summary().write(buf);
            });
        }
    }

    /**
     * Detection (SPEC 4): every KVR-wearing player in range and in the same dimension shows up on the
     * hacker's radar, even before the hand looks at them. Empty list = nobody around, so the radar clears.
     */
    private static void sendTargets(ServerPlayer hacker) {
        List<HackProtocol.Target> found = new ArrayList<>();
        double r2 = HackGlasses.SPY_RANGE * HackGlasses.SPY_RANGE;
        for (ServerPlayer other : hacker.getServer().getPlayerList().getPlayers()) {
            if (other == hacker || other.level() != hacker.level() || other.isSpectator()) continue;
            if (!GlassesPower.isGlasses(other.getItemBySlot(EquipmentSlot.HEAD))) continue;
            if (hacker.distanceToSqr(other) > r2) continue;
            Vec3 at = other.position();
            found.add(new HackProtocol.Target(other.getUUID(), other.getName().getString(), at.x, at.y + 1.4, at.z));
        }
        KVRNetwork.sendToPlayer(hacker, HackProtocol.S2C_HACK_TARGETS, buf -> {
            buf.writeVarInt(found.size());
            for (HackProtocol.Target t : found) t.write(buf);
        });
    }

    private static ServerPlayer lookTarget(ServerPlayer hacker) {
        ServerPlayer best = null;
        double bestScore = -1;
        for (ServerPlayer other : hacker.getServer().getPlayerList().getPlayers()) {
            if (other == hacker || other.level() != hacker.level() || other.isSpectator()) continue;
            double score = SpyMath.score(hacker.getEyePosition(), hacker.getViewVector(1f), other.getEyePosition(), HackGlasses.SPY_RANGE, HackGlasses.SPY_CONE_DEG);
            if (score > bestScore && hacker.hasLineOfSight(other)) {
                best = other;
                bestScore = score;
            }
        }
        return best;
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
