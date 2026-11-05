package com.kasperstudios.kvr.spatial.anchor;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;

/**
 * Quest-style "lazy follow" for system panels (dock, notifications): the panel stays where it is
 * while it is comfortably in view, and only when the player turns away by more than
 * {@code maxAngleDeg} (or walks off) does it glide back in front at the configured distance/height.
 * This is what makes the dock feel like part of the room instead of a HUD glued to the face.
 */
public final class LazyFollowAnchor implements SpatialAnchor {

    private static final double RECENTER_RATE = 6.0;
    private static final double DISTANCE_SLACK = 0.35;
    private static final double SETTLED = 0.01;

    private final double distance;
    private final double verticalOffset;
    private final double maxAngleDeg;
    private Vec3 current;
    private boolean recentering;

    /**
     * @param distance       horizontal metres in front of the eye
     * @param verticalOffset metres relative to eye height (negative = below, the dock sits low)
     * @param maxAngleDeg    how far the player may turn before the panel follows
     */
    public LazyFollowAnchor(double distance, double verticalOffset, double maxAngleDeg) {
        this.distance = distance;
        this.verticalOffset = verticalOffset;
        this.maxAngleDeg = maxAngleDeg;
    }

    static LazyFollowAnchor at(SpatialPose current, PlayerView view) {
        Vec3 d = current.center().subtract(view.eye());
        LazyFollowAnchor a = new LazyFollowAnchor(Math.sqrt(d.x * d.x + d.z * d.z), d.y, 35.0);
        a.current = current.center();
        return a;
    }

    @Override
    public SpatialPose resolve(PlayerView view, float dt) {
        Vec3 desired = desired(view);
        if (current == null) {
            current = desired;
        } else {
            if (!recentering && outOfComfort(view)) recentering = true;
            if (recentering) {
                current = current.lerp(desired, 1 - Math.exp(-RECENTER_RATE * dt));
                if (current.distanceTo(desired) < SETTLED) recentering = false;
            }
        }
        return SpatialPose.facing(current, view.eye());
    }

    private Vec3 desired(PlayerView view) {
        return view.eye().add(view.horizontalLook().scale(distance)).add(0, verticalOffset, 0);
    }

    private boolean outOfComfort(PlayerView view) {
        Vec3 to = current.subtract(view.eye());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        double len = flat.length();
        if (len < 1e-4) return true;
        double cos = Math.max(-1, Math.min(1, flat.scale(1 / len).dot(view.horizontalLook())));
        return Math.toDegrees(Math.acos(cos)) > maxAngleDeg || Math.abs(len - distance) > DISTANCE_SLACK;
    }

    public boolean isRecentering() {
        return recentering;
    }

    @Override
    public SpatialAnchor rebase(SpatialPose released, PlayerView view) {
        return at(released, view);
    }

    @Override
    public Mode mode() {
        return Mode.FOLLOW;
    }
}
