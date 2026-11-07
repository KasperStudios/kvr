package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.platform.KVRServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server relay of the spatial hand (ADR-001). The Alt pointer is part of the player's avatar, not a
 * private HUD: everyone around must see the arm swing at the window the owner is touching.
 *
 * <p>Each client reports its own pose (yaw/pitch offsets) plus whether Alt mode is on
 * ({@link #REPORT}); the server keeps the latest sample and broadcasts it to everyone in range who can
 * see the player ({@link #VIEW}). Rate-limited to {@link HandPose#SEND_TICKS}: the arm does not need
 * 20 Hz over the wire, and interpolated rendering hides the gaps.
 */
public final class HandPoseRelay {

    public static final ResourceLocation REPORT = id("hand_report");
    public static final ResourceLocation VIEW = id("hand_view");

    /** How often a client sends its own pose and the server forwards it (ticks). */
    public static final int SEND_TICKS = 2;
    /** Range for broadcasting a hand pose (blocks). Matches the server's entity view range. */
    public static final double RANGE = 64.0;
    private static final int STALE_TICKS = 100;

    private record Stored(float yaw, float pitch, boolean active, int tick) {}

    private static final Map<UUID, Stored> LATEST = new HashMap<>();

    private HandPoseRelay() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(REPORT, (player, buf) -> {
            boolean active = buf.readBoolean();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            MinecraftServer server = com.kasperstudios.kvr.compat.VersionCompat.server(player);
            if (server != null) {
                server.execute(() -> LATEST.put(player.getUUID(), new Stored(yaw, pitch, active, server.getTickCount())));
            }
        });
        KVRServer.get().onServerTick(HandPoseRelay::tick);
    }

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        LATEST.values().removeIf(s -> now - s.tick() > STALE_TICKS);
        if (now % SEND_TICKS != 0) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer owner : players) {
            Stored s = LATEST.get(owner.getUUID());
            if (s == null) continue;
            for (ServerPlayer viewer : players) {
                if (viewer == owner || viewer.level() != owner.level()) continue;
                if (viewer.distanceToSqr(owner) > RANGE * RANGE) continue;
                KVRNetwork.sendToPlayer(viewer, VIEW, buf -> {
                    buf.writeVarInt(owner.getId());
                    buf.writeBoolean(s.active());
                    buf.writeFloat(s.yaw());
                    buf.writeFloat(s.pitch());
                });
            }
        }
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
