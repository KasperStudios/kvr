package com.kasperstudios.kvr.spatial.input;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandPoseTest {

    @BeforeEach
    void setUp() {
        HandPose.clear();
    }

    @Test
    void missingEntityReturnsIdle() {
        HandPose.Pose p = HandPose.get(42);
        assertFalse(p.active());
        assertEquals(0f, p.yaw());
        assertEquals(0f, p.pitch());
    }

    @Test
    void activePoseIsStoredAndReturned() {
        HandPose.put(42, new HandPose.Pose(true, 15.5f, -20.0f));
        HandPose.Pose p = HandPose.get(42);
        assertTrue(p.active());
        assertEquals(15.5f, p.yaw(), 1e-4);
        assertEquals(-20.0f, p.pitch(), 1e-4);
    }

    @Test
    void inactivePoseClearsTheEntry() {
        HandPose.put(42, new HandPose.Pose(true, 15.5f, -20.0f));
        assertTrue(HandPose.active(42));
        HandPose.put(42, new HandPose.Pose(false, 0f, 0f));
        assertFalse(HandPose.active(42));
    }
}
