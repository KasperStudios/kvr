package com.kasperstudios.kvr.spatial.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Body anchoring from the gear menu: travels with the player, stays within reach, ignores the head. */
class BodyAnchorTest {

    private static final Vec3 EYE = new Vec3(0, 65.62, 0);

    private static SpatialPose settle(SpatialAnchor a, PlayerView v) {
        SpatialPose p = null;
        for (int i = 0; i < 120; i++) p = a.resolve(v, 0.05f);
        return p;
    }

    private static double flatDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Test
    void farWorldWindowIsPulledWithinReachInTheSameDirection() {
        PlayerView v = PlayerView.at(EYE, 0f, 0f);
        SpatialPose far = SpatialPose.facing(new Vec3(0, 65.5, 20), EYE);
        SpatialAnchor a = SpatialAnchor.of(SpatialAnchor.Mode.BODY, far, v);
        assertEquals(SpatialAnchor.Mode.BODY, a.mode());
        SpatialPose p = settle(a, v);
        assertEquals(BodyAnchor.MAX_REACH, flatDistance(p.center(), v.feet()), 1e-3);
        assertTrue(p.center().z > 0, "keeps the direction the window was in");
    }

    @Test
    void windowWithinReachDoesNotJumpWhenSwitched() {
        PlayerView v = PlayerView.at(EYE, 0f, 0f);
        Vec3 spot = new Vec3(0.3, 65.4, 0.9);
        SpatialAnchor a = SpatialAnchor.of(SpatialAnchor.Mode.BODY, SpatialPose.facing(spot, EYE), v);
        assertEquals(0.0, a.resolve(v, 0.05f).center().distanceTo(spot), 1e-6);
        assertEquals(0.0, settle(a, v).center().distanceTo(spot), 1e-6);
    }

    @Test
    void walksWithThePlayerButIgnoresHeadTurns() {
        PlayerView v = PlayerView.at(EYE, 0f, 0f);
        Vec3 spot = new Vec3(0.3, 65.4, 0.9);
        SpatialAnchor a = SpatialAnchor.of(SpatialAnchor.Mode.BODY, SpatialPose.facing(spot, EYE), v);
        settle(a, v);

        SpatialPose turned = settle(a, PlayerView.at(EYE, 120f, 30f));
        assertEquals(0.0, turned.center().distanceTo(spot), 1e-6, "head turn must not swing the window in front of the eyes");

        Vec3 walked = EYE.add(10, 0, -4);
        SpatialPose after = settle(a, PlayerView.at(walked, 0f, 0f));
        assertEquals(0.0, after.center().distanceTo(spot.add(10, 0, -4)), 1e-4, "same offset after walking");
    }

    @Test
    void heightIsClampedBetweenKneesAndJustOverTheHead() {
        PlayerView v = PlayerView.at(EYE, 0f, 0f);
        SpatialAnchor high = SpatialAnchor.of(SpatialAnchor.Mode.BODY, SpatialPose.facing(new Vec3(0, 90, 1), EYE), v);
        SpatialAnchor low = SpatialAnchor.of(SpatialAnchor.Mode.BODY, SpatialPose.facing(new Vec3(0, 50, 1), EYE), v);
        assertEquals(v.feet().y + BodyAnchor.MAX_HEIGHT, settle(high, v).center().y, 1e-3);
        assertEquals(v.feet().y + BodyAnchor.MIN_HEIGHT, settle(low, v).center().y, 1e-3);
    }
}
