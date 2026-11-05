package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * \"Holding the window in the hand\" (ADR-002): LMB on the grab bar attaches the window to the ray at
 * the distance where it was grabbed; the mouse moves it in X/Y, the wheel pushes it away or pulls
 * it closer; releasing LMB drops it and re-bases its anchor at that spot.
 */
public final class GrabController {

    private static final double MIN_DISTANCE = 0.4;
    private static final double MAX_DISTANCE = 6.0;
    private static final double SCROLL_STEP = 0.08;

    @Nullable private SpatialWindow held;
    private double distance;
    private double grabX;
    private double grabY;

    public boolean holding() {
        return held != null;
    }

    @Nullable
    public SpatialWindow held() {
        return held;
    }

    public void begin(SpatialRaycast.RayHit hit) {
        held = hit.window();
        distance = hit.distance();
        grabX = hit.localX();
        grabY = hit.localY();
    }

    public void drag(SpatialRaycast.Ray ray, PlayerView view) {
        if (held == null) return;
        Vec3 point = ray.origin().add(ray.dir().scale(distance));
        SpatialPose facing = SpatialPose.facing(point, view.eye());
        // keep the exact spot under the hand: centre = grab point - right*x + up*y
        Vec3 center = point.subtract(facing.right().scale(grabX)).add(facing.up().scale(grabY));
        held.hold(SpatialPose.facing(center, view.eye()));
    }

    public boolean scroll(double amount) {
        if (held == null) return false;
        distance = Mth.clamp(distance + amount * SCROLL_STEP, MIN_DISTANCE, MAX_DISTANCE);
        return true;
    }

    public void end(PlayerView view) {
        if (held == null) return;
        held.release(view);
        held = null;
    }
}
