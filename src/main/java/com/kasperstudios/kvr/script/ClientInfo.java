package com.kasperstudios.kvr.script;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Snapshot of client facts scripts may read (coordinates, dimension, online players), refreshed on
 * the client thread every tick. Script threads only read this immutable record — never the
 * {@code Minecraft} singleton directly.
 */
public final class ClientInfo {

    public record Snapshot(int x, int y, int z, String dimension, String name, List<String> online, String language) {}

    private static volatile Snapshot snap = new Snapshot(0, 0, 0, "", "", List.of(), "en_us");
    private static int ticks;

    private ClientInfo() {}

    /** Client tick (called from KVRSpatial). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockPos p = mc.player.blockPosition();
        String self = mc.player.getName().getString();
        List<String> online = ticks++ % 20 == 0 || snap.online().isEmpty() ? online(mc, self) : snap.online();
        snap = new Snapshot(p.getX(), p.getY(), p.getZ(), com.kasperstudios.kvr.compat.VersionCompat.keyId(mc.level.dimension()).toString(), self, online, mc.options.languageCode);
    }

    public static Snapshot get() {
        return snap;
    }

    private static List<String> online(Minecraft mc, String self) {
        if (mc.getConnection() == null) return List.of();
        return mc.getConnection().getOnlinePlayers().stream()
            .map(info -> com.kasperstudios.kvr.compat.VersionCompat.profileName(info.getProfile()))
            .filter(n -> !n.equals(self))
            .sorted()
            .toList();
    }
}
