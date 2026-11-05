package com.kasperstudios.kvr.spatial.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.kasperstudios.kvr.spatial.SpatialPose;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpatialRaycastTest {

    /** Window 2 m south of the origin, facing a viewer at the origin. */
    private static final SpatialPose WINDOW = SpatialPose.facing(new Vec3(0, 0, 2), Vec3.ZERO);

    @Test
    void straightAheadHitsTheCentre() {
        SpatialRaycast.PlaneHit hit = SpatialRaycast.intersectPlane(new SpatialRaycast.Ray(Vec3.ZERO, new Vec3(0, 0, 1)), WINDOW);
        assertNotNull(hit);
        assertEquals(2.0, hit.distance(), 1e-6);
        assertEquals(0.0, hit.localX(), 1e-6);
        assertEquals(0.0, hit.localY(), 1e-6);
    }

    @Test
    void viewersRightIsPositiveLocalX() {
        // facing +Z (south), +X is east (the viewer's left hand), -X is west (viewer's right hand).
        // forward = (0, 0, 1), WORLD_UP = (0, 1, 0).
        // forward x up = (0*0 - 1*1, 1*0 - 0*0, 0*1 - 0*0) = (-1, 0, 0).
        // So right() vector is (-1, 0, 0).
        // Ray towards -X: d = (-0.2, 0, 0). d.dot(right()) = (-0.2)*(-1) = +0.2.
        SpatialRaycast.PlaneHit hit = SpatialRaycast.intersectPlane(new SpatialRaycast.Ray(Vec3.ZERO, new Vec3(-0.2, 0, 2).normalize()), WINDOW);
        assertNotNull(hit);
        assertEquals(0.2, hit.localX(), 1e-4);
    }

    @Test
    void upIsNegativeLocalY() {
        SpatialRaycast.PlaneHit hit = SpatialRaycast.intersectPlane(new SpatialRaycast.Ray(Vec3.ZERO, new Vec3(0, 0.3, 2).normalize()), WINDOW);
        assertNotNull(hit);
        assertEquals(-0.3, hit.localY(), 1e-6);
    }

    @Test
    void lookingAwayMisses() {
        assertNull(SpatialRaycast.intersectPlane(new SpatialRaycast.Ray(Vec3.ZERO, new Vec3(0, 0, -1)), WINDOW));
    }

    @Test
    void backFaceIsNotClickable() {
        assertNull(SpatialRaycast.intersectPlane(new SpatialRaycast.Ray(new Vec3(0, 0, 4), new Vec3(0, 0, -1)), WINDOW));
    }
}
