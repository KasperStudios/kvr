package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hand ray vs. window planes. Pure math on {@link Vec3}: no client state, fully unit-tested.
 * Back faces are not clickable — a panel seen from behind is just glass.
 */
public final class SpatialRaycast {

    private SpatialRaycast() {}

    public record Ray(Vec3 origin, Vec3 dir) {}

    /** Hit on an infinite window plane; local metres from the centre, +x right, +y down. */
    public record PlaneHit(double distance, double localX, double localY) {}

    public enum Region { CONTENT, GRAB_BAR, ANCHOR_TOGGLE }

    /** {@code px/py} are window GUI pixels (may lie outside the content for the grab bar). */
    public record RayHit(SpatialWindow window, Region region, double distance, double px, double py, double localX, double localY) {}

    @Nullable
    public static PlaneHit intersectPlane(Ray ray, SpatialPose pose) {
        Vec3 n = pose.normal();
        double denom = ray.dir().dot(n);
        if (denom > -1e-4) return null; // parallel or looking at the back face
        double t = pose.center().subtract(ray.origin()).dot(n) / denom;
        if (t <= 0) return null;
        Vec3 d = ray.origin().add(ray.dir().scale(t)).subtract(pose.center());
        return new PlaneHit(t, d.dot(pose.right()), -d.dot(pose.up()));
    }

    public static Optional<RayHit> intersect(Ray ray, SpatialWindow w) {
        SpatialPose pose = w.pose();
        if (pose == null || w.scale() < 0.5f) return Optional.empty();
        PlaneHit h = intersectPlane(ray, pose);
        if (h == null) return Optional.empty();
        double pxPerM = SpatialWindow.PX_PER_METER * w.scale();
        double px = h.localX() * pxPerM + w.width() / 2.0;
        double py = h.localY() * pxPerM + w.height() / 2.0;
        Region region = regionAt(w, px, py);
        return region == null ? Optional.empty() : Optional.of(new RayHit(w, region, h.distance(), px, py, h.localX(), h.localY()));
    }

    @Nullable
    static Region regionAt(SpatialWindow w, double px, double py) {
        if (px >= 0 && py >= 0 && px < w.width() && py < w.height()) return Region.CONTENT;
        if (!w.hasGrabBar()) return null;
        double barTop = w.height() + SpatialWindow.GRAB_GAP - SpatialWindow.GRAB_HIT_PAD;
        double barBottom = w.height() + SpatialWindow.GRAB_GAP + SpatialWindow.GRAB_H + SpatialWindow.GRAB_HIT_PAD;
        if (py < barTop || py > barBottom) return null;
        double cx = w.width() / 2.0;
        double half = SpatialWindow.GRAB_W / 2.0;
        if (px >= cx - half - SpatialWindow.GRAB_HIT_PAD && px <= cx + half + SpatialWindow.GRAB_HIT_PAD) return Region.GRAB_BAR;
        double tx = cx + half + SpatialWindow.TOGGLE_GAP;
        if (px >= tx - 2 && px <= tx + SpatialWindow.TOGGLE_SIZE + 2) return Region.ANCHOR_TOGGLE;
        return null;
    }
}
