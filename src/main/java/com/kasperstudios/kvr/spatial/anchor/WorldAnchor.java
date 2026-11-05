package com.kasperstudios.kvr.spatial.anchor;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;

/** Window frozen in world coordinates, e.g. hanging over a chest or on a base wall. */
public final class WorldAnchor implements SpatialAnchor {

    private final SpatialPose pose;

    public WorldAnchor(SpatialPose pose) {
        this.pose = pose;
    }

    @Override
    public SpatialPose resolve(PlayerView view, float dt) {
        return pose;
    }

    @Override
    public SpatialAnchor rebase(SpatialPose released, PlayerView view) {
        return new WorldAnchor(released);
    }

    @Override
    public Mode mode() {
        return Mode.WORLD;
    }
}
