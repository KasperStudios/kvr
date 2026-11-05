package com.kasperstudios.kvr.spatial;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where a spatial window is this frame: its centre in world space and the direction it faces.
 *
 * <p>Yaw/pitch use Minecraft's convention (degrees, yaw 0 = south, positive pitch looks down), so a
 * window with the viewer's own yaw/pitch is seen straight on. Everything else (normal, right, up) is
 * derived, which keeps the record tiny and makes interpolation trivial.
 */
public record SpatialPose(Vec3 center, float yaw, float pitch) {

    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);
    private static final float MAX_PITCH = 85f;

    /** From the viewer into the window. */
    public Vec3 forward() {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /** Out of the window's front face, towards the viewer. */
    public Vec3 normal() {
        return forward().reverse();
    }

    /** Window +X as the viewer sees it. */
    public Vec3 right() {
        return forward().cross(WORLD_UP).normalize();
    }

    /** Window +Y as the viewer sees it. */
    public Vec3 up() {
        return right().cross(forward()).normalize();
    }

    /** A pose at {@code center} turned to face {@code eye} (billboard). */
    public static SpatialPose facing(Vec3 center, Vec3 eye) {
        Vec3 d = center.subtract(eye);
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        float pitch = Mth.clamp((float) (-(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG)), -MAX_PITCH, MAX_PITCH);
        return new SpatialPose(center, yaw, pitch);
    }

    public SpatialPose lerp(SpatialPose to, double t) {
        float k = (float) t;
        return new SpatialPose(center.lerp(to.center, t), Mth.rotLerp(k, yaw, to.yaw), Mth.lerp(k, pitch, to.pitch));
    }
}
