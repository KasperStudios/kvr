package com.kasperstudios.kvr.spatial.anchor;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;

/**
 * Decides where a window lives (ADR-002). There is no HUD in KVR: every surface — apps, the dock,
 * notifications, the gesture wheel — is a window in the world with one of these anchors.
 *
 * <ul>
 *   <li>{@link WorldAnchor} — fixed world coordinates, a monitor on the wall.</li>
 *   <li>{@link BodyAnchor} — follows the body position, ignores head yaw/pitch.</li>
 *   <li>{@link LazyFollowAnchor} — Quest-style system panels: stay put, recentre when out of view.</li>
 * </ul>
 */
public sealed interface SpatialAnchor permits WorldAnchor, BodyAnchor, LazyFollowAnchor {

    enum Mode { WORLD, BODY, FOLLOW }

    /** This frame's pose. Anchors may keep smoothing state between calls; {@code dt} is in seconds. */
    SpatialPose resolve(PlayerView view, float dt);

    /** Same anchor type, re-based so the window stays exactly where the grab released it. */
    SpatialAnchor rebase(SpatialPose released, PlayerView view);

    Mode mode();

    /**
     * Switch anchor type from the gear menu (or {@code kvr.window.anchor}). World freezes the window
     * where it is; Body keeps its direction from the player but pulls it within arm's reach.
     */
    static SpatialAnchor of(Mode mode, SpatialPose current, PlayerView view) {
        return switch (mode) {
            case WORLD -> new WorldAnchor(current);
            case BODY -> BodyAnchor.carry(current, view);
            case FOLLOW -> LazyFollowAnchor.at(current, view);
        };
    }
}
