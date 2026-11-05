package com.kasperstudios.kvr.spatial.anchor;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;

/**
 * Body-relative window (ADR-002 \"Окно с игроком\"): keeps a world-axis offset from the feet, so it
 * walks with the player but does not swing in front of the eyes when the head turns. The window
 * always turns to face the eye, and its position is smoothed so steps do not make it jitter.
 */
public final class BodyAnchor implements SpatialAnchor {

    private static final double FOLLOW_RATE = 10.0;

    private final Vec3 offset;
    private Vec3 smoothed;

    public BodyAnchor(Vec3 offset) {
        this.offset = offset;
    }

    static BodyAnchor at(SpatialPose current, PlayerView view) {
        BodyAnchor a = new BodyAnchor(current.center().subtract(view.feet()));
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
}
