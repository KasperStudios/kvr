package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Alt toggle (ADR-001): one press frees the crosshair from the screen centre and turns it into the
 * hand pointer, the next press brings it back. WASD, jumping and E keep working.
 *
 * <p>The mouse moves the pointer inside a soft deadzone (~76% of the view); past its edge the
 * excess turns the camera, so the player can reach anything around without leaving the mode.
 * Raw mouse deltas come from {@code MouseHandlerMixin}.
 */
public final class SpatialCursor {

    private static final float DEADZONE = 0.76f;
    /** Entity#turn multiplies by 0.15; vanilla mouse code feeds it degrees / 0.15. */
    private static final double TURN_SCALE = 0.15;

    private final KeyMapping toggleKey = new KeyMapping("key.kvr.spatial_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.kvr");
    private boolean active;
    private float yawOffset;
    private float pitchOffset;

    public KeyMapping toggleKey() {
        return toggleKey;
    }

    public void tick() {
        while (toggleKey.consumeClick()) setActive(!active);
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            yawOffset = 0f;
            pitchOffset = 0f;
        }
    }

    /** @return true when the movement was used for the pointer and vanilla must not turn the camera */
    public boolean consumeMouse(double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.screen != null || mc.player == null) return false;

        double s = mc.options.sensitivity().get() * 0.6 + 0.2;
        double k = s * s * s * 8.0 * TURN_SCALE;
        yawOffset += (float) (dx * k);
        pitchOffset += (float) (dy * k);

        double halfY = mc.options.fov().get() / 2.0;
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfX = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfY)) * aspect));
        float limitX = (float) (halfX * DEADZONE);
        float limitY = (float) (halfY * DEADZONE);

        float panYaw = excess(yawOffset, limitX);
        float panPitch = excess(pitchOffset, limitY);
        if (panYaw != 0f || panPitch != 0f) {
            mc.player.turn(panYaw / TURN_SCALE, panPitch / TURN_SCALE);
            yawOffset -= panYaw;
            pitchOffset -= panPitch;
        }
        return true;
    }

    private static float excess(float value, float limit) {
        if (value > limit) return value - limit;
        if (value < -limit) return value + limit;
        return 0f;
    }

    /** The hand ray: from the eye through the pointer. */
    public SpatialRaycast.Ray ray(PlayerView view) {
        Vec3 dir = Vec3.directionFromRotation(view.pitch() + pitchOffset, view.yaw() + yawOffset);
        return new SpatialRaycast.Ray(view.eye(), dir);
    }
}
