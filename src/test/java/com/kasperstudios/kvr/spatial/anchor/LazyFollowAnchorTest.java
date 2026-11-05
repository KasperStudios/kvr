package com.kasperstudios.kvr.spatial.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LazyFollowAnchorTest {

    private static final Vec3 EYE = new Vec3(0, 1.62, 0);

    @Test
    void firstFrameSnapsInFrontAtDistanceAndHeight() {
        LazyFollowAnchor a = new LazyFollowAnchor(1.0, -0.5, 35);
        SpatialPose p = a.resolve(PlayerView.at(EYE, 0f, 0f), 0.016f);
        assertEquals(0.0, p.center().x, 1e-6);
        assertEquals(1.12, p.center().y, 1e-6);
        assertEquals(1.0, p.center().z, 1e-6);
    }

    @Test
    void smallHeadTurnDoesNotMoveThePanel() {
        LazyFollowAnchor a = new LazyFollowAnchor(1.0, 0, 35);
        SpatialPose before = a.resolve(PlayerView.at(EYE, 0f, 0f), 0.016f);
        SpatialPose after = a.resolve(PlayerView.at(EYE, 20f, 0f), 0.016f);
        assertEquals(before.center(), after.center());
        assertFalse(a.isRecentering());
    }

    @Test
    void turningAwayGlidesThePanelBackInFront() {
        LazyFollowAnchor a = new LazyFollowAnchor(1.0, 0, 35);
        a.resolve(PlayerView.at(EYE, 0f, 0f), 0.016f);
        PlayerView turned = PlayerView.at(EYE, 90f, 0f);
        a.resolve(turned, 0.1f);
        assertTrue(a.isRecentering());
        SpatialPose p = null;
        for (int i = 0; i < 60; i++) p = a.resolve(turned, 0.1f);
        Vec3 expected = EYE.add(turned.horizontalLook());
        assertEquals(0.0, p.center().distanceTo(expected), 0.02);
        assertFalse(a.isRecentering());
    }
}
