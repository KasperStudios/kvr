package com.kasperstudios.kvr.spatial.anchor;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;

/**
 * Body-relative window (ADR-002 "Окно с игроком"): keeps a world-axis offset from the feet, so it
 * walks with the player but does not swing in front of the eyes when the head turns. The window
 * always turns to face the eye, and its position is smoothed so steps do not make it jitter.
 *
 * <p>Switching a window to Body from the gear menu uses {@link #carry}: the window keeps its
 * direction from the player but is pulled within {@link #MAX_REACH} — a monitor left 20 blocks away
 * does not start flying around the world 20 blocks from you.
 */
public final class BodyAnchor implements SpatialAnchor {

    private static final double FOLLOW_RATE = 10.0;
    /** Horizontal distance from the feet a carried window keeps (metres). */
    static final double MIN_REACH = 0.6;
    static final double MAX_REACH = 2.0;
    /** Height above the feet: from about the knees to a little over the head. */
    static final double MIN_HEIGHT = 0.4;
    static final double MAX_HEIGHT = 2.4;

    private final Vec3 offset;
    private Vec3 smoothed;

    public BodyAnchor(Vec3 offset) {
        this.offset = offset;
    }

    /** Exactly where it is (grab release). */
    static BodyAnchor at(SpatialPose current, PlayerView view) {
        BodyAnchor a = new BodyAnchor(current.center().subtract(view.feet()));
        a.smoothed = current.center();
        return a;
    }

    /** Menu switch World → Body: same direction from the player, clamped to arm's reach; glides there. */
    static BodyAnchor carry(SpatialPose current, PlayerView view) {
        Vec3 off = current.center().subtract(view.feet());
        double flat = Math.sqrt(off.x * off.x + off.z * off.z);
        double reach = Math.max(MIN_REACH, Math.min(MAX_REACH, flat));
        Vec3 dir = flat < 1e-4 ? view.horizontalLook() : new Vec3(off.x / flat, 0, off.z / flat);
        double y = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, off.y));
        BodyAnchor a = new BodyAnchor(new Vec3(dir.x * reach, y, dir.z * reach));
        a.smoothed = current.center();
        return a;
    }

    @Override
    public SpatialPose resolve(PlayerView view, float dt) {
        Vec3 target = view.feet().add(offset);
        smoothed = smoothed == null ? target : smoothed.lerp(target, 1 - Math.exp(-FOLLOW_RATE * dt));
        return SpatialPose.facing(smoothed, view.eye());
    }

    @Override
    public SpatialAnchor rebase(SpatialPose released, PlayerView view) {
        return at(released, view);
    }

    @Override
    public Mode mode() {
        return Mode.BODY;
    }

    public Vec3 offset() {
        return offset;
    }
}
