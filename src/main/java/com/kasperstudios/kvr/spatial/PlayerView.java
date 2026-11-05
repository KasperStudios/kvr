package com.kasperstudios.kvr.spatial;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/**
 * Snapshot of the player's head and body for one frame. Anchors read only this, never the
 * {@code Minecraft} singleton, so they stay testable without a running client.
 */
public record PlayerView(Vec3 eye, float yaw, float pitch, Vec3 feet) {

    private static final double EYE_HEIGHT = 1.62;

    public static PlayerView capture(Camera camera, float partialTick) {
        Vec3 feet = camera.getEntity().getPosition(partialTick);
        return new PlayerView(camera.getPosition(), camera.getYRot(), camera.getXRot(), feet);
    }

    /** Test / tooling factory: a standing player looking along yaw/pitch. */
    public static PlayerView at(Vec3 eye, float yaw, float pitch) {
        return new PlayerView(eye, yaw, pitch, eye.subtract(0, EYE_HEIGHT, 0));
    }

    public Vec3 look() {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    /** Look direction flattened to the horizon: where the body is facing. */
    public Vec3 horizontalLook() {
        return Vec3.directionFromRotation(0f, yaw);
    }
}
