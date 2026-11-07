package com.kasperstudios.kvr.spatial.input;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everyone else's spatial hand (ADR-001: the Alt pointer drives the arm). The local player's own arm
 * comes straight from {@link SpatialCursor}; other players are drawn from what the server relayed, so
 * a row of people waving their hands in Alt mode is visible to the whole server, not just to each of
 * them.
 *
 * <p>Keyed by entity id (not UUID) because {@code PlayerModel.setupAnim} only has the entity.
 * Entries expire: a client that stops reporting (Alt off, logout, dimension change) must not leave a
 * frozen arm behind.
 */
public final class HandPose {

    /** Degrees, same convention as {@link SpatialCursor#yawOffset()} / {@link SpatialCursor#pitchOffset()}. */
    public record Pose(boolean active, float yaw, float pitch) {
        public static final Pose IDLE = new Pose(false, 0f, 0f);
    }

    private static final Map<Integer, Entry> POSES = new ConcurrentHashMap<>();
    /** Longest a remote pose may live without a fresh report (reports come at {@link #SEND_TICKS}). */
    private static final long TTL_MS = 1500;

    private record Entry(Pose pose, long at) {}

    private HandPose() {}

    public static void put(int entityId, Pose pose) {
        if (!pose.active()) {
            POSES.remove(entityId);
            return;
        }
        POSES.put(entityId, new Entry(pose, System.currentTimeMillis()));
    }

    /** Pose of a remote player, or {@link Pose#IDLE} when they are not in spatial mode. */
    public static Pose get(int entityId) {
        Entry e = POSES.get(entityId);
        if (e == null) return Pose.IDLE;
        if (System.currentTimeMillis() - e.at() > TTL_MS) {
            POSES.remove(entityId);
            return Pose.IDLE;
        }
        return e.pose();
    }

    public static boolean active(int entityId) {
        return get(entityId).active();
    }

    public static void clear() {
        POSES.clear();
    }

    /** Server-side: what a player last reported, so the relay does not have to re-read packets. */
    public record Sample(UUID player, Pose pose) {}
}
