package com.kasperstudios.kvr.spatial.ar;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.client.PowerClient;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0033 — AR vision data (Creator: "always on while the glasses are worn and lit", not only in the cocoon).
 * Collected on the client tick, drawn by {@link ArRenderer} every frame.
 * <ul>
 *   <li><b>Redstone</b> — every block with a signal {@code POWER > 0} (wire, pressure plates, daylight sensor,
 *       sculk, target…) with its level, and every {@code POWERED=true} component (repeater, comparator, lever,
 *       button, rails, observer…). Scan radius grows with the GPU tier: 4 + 2·T (6…14 blocks), ±4 vertically,
 *       every 10 ticks; at most {@link #MAX_REDSTONE} markers.</li>
 *   <li><b>Rednet ether</b> — TX/RX pulses pushed by {@code RednetClient} (any thread).</li>
 *   <li><b>Mobs</b> — living entities within 24 blocks; the one the visor looks at (≤ 7° off the view axis, in line
 *       of sight) gets the hitbox outline and the health bar, damaged ones nearby a thin bar.</li>
 *   <li><b>Block Inspector</b> (0034a, replaces the floor grid) — {@link ArInspector}.</li>
 * </ul>
 */
public final class ArScanner {

    public static final int MAX_REDSTONE = 384;
    public static final double MOB_RANGE = 24;
    private static final double LOOK_COS = Math.cos(Math.toRadians(7));
    private static final int SCAN_EVERY = 10;
    private static final int MAX_PULSES = 8;

    /** A powered block: {@code power} 1..15 for signal blocks, -1 for on/off components. */
    public record Signal(BlockPos pos, int power, boolean wire) {}

    /** A Rednet packet seen by the visor. {@code distance} is blocks to the sender (RX), -1 for TX. */
    public record Pulse(boolean outgoing, int peer, String protocol, int distance, long at) {}

    private static volatile List<Signal> signals = List.of();
    private static volatile List<LivingEntity> mobs = List.of();
    @Nullable private static volatile LivingEntity target;
    private static final Deque<Pulse> pulses = new ConcurrentLinkedDeque<>();
    private static int tick;

    private ArScanner() {}

    // --- events --------------------------------------------------------------------------------

    /** Thread-safe: called by RednetClient on send (script threads) and on receive. */
    public static void rednet(boolean outgoing, int peer, String protocol, int distance) {
        pulses.addLast(new Pulse(outgoing, peer, protocol == null ? "" : protocol, distance, System.currentTimeMillis()));
        while (pulses.size() > MAX_PULSES) pulses.pollFirst();
    }

    // --- tick ----------------------------------------------------------------------------------

    /** Client tick; {@code on} = glasses worn and the display lit. */
    public static void tick(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (!on || player == null || level == null) {
            if (!signals.isEmpty()) signals = List.of();
            if (!mobs.isEmpty()) mobs = List.of();
            target = null;
            ArInspector.tick(false);
            return;
        }
        KvrSpaceConfig.Prefs prefs = KvrSpaceConfig.get();

        boolean windowUnderRay = com.kasperstudios.kvr.spatial.KVRSpatial.ready()
            && com.kasperstudios.kvr.spatial.KVRSpatial.input().hover().isPresent();
        ArInspector.tick(prefs.arInspector() && !com.kasperstudios.kvr.spatial.space.VirtualSpaces.inSpace() && !windowUnderRay);

        if (prefs.arRedstone()) {
            if (tick++ % SCAN_EVERY == 0) signals = scanRedstone(level, player.blockPosition(), radius());
        } else if (!signals.isEmpty()) {
            signals = List.of();
        }

        if (prefs.arMobs()) scanMobs(level, player);
        else {
            mobs = List.of();
            target = null;
        }
    }

    /** 4 + 2·GPU tier: T1 sees 6 blocks of redstone, T5 sees 14. */
    public static int radius() {
        int gpu = GlassesPower.build(PowerClient.worn()).map(PowerBuild::gpu).orElse(1);
        return 4 + 2 * Math.max(1, Math.min(5, gpu));
    }

    private static List<Signal> scanRedstone(ClientLevel level, BlockPos center, int r) {
        List<Signal> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.hasProperty(BlockStateProperties.POWER)) {
                        int power = s.getValue(BlockStateProperties.POWER);
                        if (power > 0) out.add(new Signal(p.immutable(), power, s.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)));
                    } else if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED)) {
                        out.add(new Signal(p.immutable(), -1, false));
                    }
                    if (out.size() >= MAX_REDSTONE) return out;
                }
            }
        }
        return out;
    }

    private static void scanMobs(ClientLevel level, LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(MOB_RANGE);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
            e -> e != player && e.isAlive() && !e.isInvisibleTo(player) && e.distanceToSqr(player) <= MOB_RANGE * MOB_RANGE);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        LivingEntity best = null;
        double bestCos = LOOK_COS;
        for (LivingEntity e : found) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double len = to.length();
            if (len < 1e-6) continue;
            double cos = to.dot(look) / len;
            // big mobs: allow the angle the hitbox itself covers
            double grace = Math.min(0.5, e.getBbWidth() / len);
            if (cos + grace * 0.15 > bestCos && player.hasLineOfSight(e)) {
                best = e;
                bestCos = cos + grace * 0.15;
            }
        }
        mobs = found.size() > 32 ? List.copyOf(found.subList(0, 32)) : List.copyOf(found);
        target = best;
    }

    // --- reads (render thread) -----------------------------------------------------------------

    public static List<Signal> signals() {
        return signals;
    }

    public static List<LivingEntity> mobs() {
        return mobs;
    }

    @Nullable
    public static LivingEntity target() {
        return target;
    }

    /** Pulses younger than {@code maxAgeMs}; older ones are dropped. */
    public static List<Pulse> pulses(long maxAgeMs) {
        long now = System.currentTimeMillis();
        while (true) {
            Pulse first = pulses.peekFirst();
            if (first == null || now - first.at() <= maxAgeMs) break;
            pulses.pollFirst();
        }
        return List.copyOf(pulses);
    }
}
