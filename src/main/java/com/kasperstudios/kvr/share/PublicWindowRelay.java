package com.kasperstudios.kvr.share;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.hack.HackGlasses;
import com.kasperstudios.kvr.hack.SpyRelay;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.platform.KVRServer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Public windows (gear menu → Visibility → Public) materialise for other players. The owner's client
 * already reports its Public windows at 2 Hz ({@code kvr:window_summary}, see {@link SpyRelay});
 * this relay forwards them to every player within {@code public_window_range} blocks in the same
 * dimension ({@code kvr:public_windows}, S2C). Viewers need no glasses: it is a panel in the world.
 *
 * <p>What travels is the summary — pose, size, title, text (≤ 256 chars) — not pixels; viewers draw
 * it as a glass panel ({@code PublicWindowRenderer}). Pixel mirroring is a later ticket.
 * Private entries in the same summary are filtered out here ({@link WindowSummary#publicView}); only
 * {@code SpyRelay} hands them to a HackGlasses wearer.
 */
public final class PublicWindowRelay {

    public static final ResourceLocation VIEW = id("public_windows");
    /** Nearest owners per viewer per update (bandwidth cap: 8 × 16 windows × ~350 bytes at 2 Hz). */
    static final int MAX_OWNERS = 8;

    private PublicWindowRelay() {}

    /** Common init, after {@code SpyRelay.init()} (it owns the C2S summary channel). */
    public static void init() {
        KVRServer.get().onServerTick(PublicWindowRelay::tick);
    }

    static void tick(MinecraftServer server) {
        // offset from SpyRelay's tick so both relays do not send in the same tick
        if ((server.getTickCount() + HackGlasses.SYNC_TICKS / 2) % HackGlasses.SYNC_TICKS != 0) return;
        int range = KvrConfig.server().publicWindowRange();
        if (range <= 0) return;
        double r2 = (double) range * range;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer viewer : players) {
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer owner : players) {
                if (owner == viewer || owner.level() != viewer.level() || owner.isSpectator()) continue;
                if (owner.distanceToSqr(viewer) <= r2 && SpyRelay.latest(owner.getUUID()) != null) near.add(owner);
            }
            near.sort(Comparator.comparingDouble(o -> o.distanceToSqr(viewer)));
            for (int i = 0; i < near.size() && i < MAX_OWNERS; i++) send(viewer, near.get(i), range);
        }
    }

    private static void send(ServerPlayer viewer, ServerPlayer owner, int range) {
        WindowSummary all = SpyRelay.latest(owner.getUUID());
        if (all == null) return;
        Vec3 eye = owner.getEyePosition();
        WindowSummary visible = all.publicView(eye.x, eye.y, eye.z, range);
        String name = owner.getName().getString();
        // sent even when empty: switching a window to Private removes it for viewers within 0.5 s
        KVRNetwork.sendToPlayer(viewer, VIEW, buf -> {
            buf.writeUUID(owner.getUUID());
            buf.writeUtf(name, 32);
            visible.write(buf);
        });
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
