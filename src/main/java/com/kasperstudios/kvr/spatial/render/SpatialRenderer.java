package com.kasperstudios.kvr.spatial.render;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every window as a quad in the world after translucent blocks, plus its grab bar and the
 * hand pointer where the ray touches a panel (in VR the pointer lives on the surface, not in a HUD).
 *
 * <p>Same convention as vanilla entity renderers: vertices relative to the camera, multiplied by the
 * pose stack the loader event provides (on 1.20.x it carries the camera rotation, on 1.21 the
 * rotation sits in the model-view matrix) — so this class has no version branches at all.
 */
public final class SpatialRenderer {

    private static final double POINTER = 0.012;
    private static final int WHITE = 0xFFFFFFFF;
    /** Blood-red multiply for the overload strobe (approved by the Creator). */
    private static final int ALARM_RED = 0xFFFF3B3B;
    private static long lastFrame;

    private SpatialRenderer() {}

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        if (!KVRSpatial.ready() || camera.getEntity() == null) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        WindowManager windows = KVRSpatial.windows();
        windows.update(PlayerView.capture(camera, partialTick), dt);
        Optional<SpatialRaycast.RayHit> hover = KVRSpatial.input().hover();
        List<SpatialWindow> list = windows.drawable();
        if (list.isEmpty() && KVRSpatial.input().ray().isEmpty()) return;

        // 1) offscreen: each window paints its GUI into its own texture
        for (SpatialWindow w : list) {
            boolean over = hover.isPresent() && hover.get().window() == w && hover.get().region() == SpatialRaycast.Region.CONTENT;
            w.paint(over ? (int) hover.get().px() : -1, over ? (int) hover.get().py() : -1, partialTick);
        }

        // 2) world pass, far to near so the glass blends correctly
        Vec3 cam = camera.getPosition();
        list.sort(Comparator.comparingDouble((SpatialWindow w) -> w.pose().center().distanceToSqr(cam)).reversed());
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (SpatialWindow w : list) {
            drawWindow(pose, buffers, w, cam);
            if (w.hasGrabBar()) drawGrabBar(pose, buffers, w, cam, hover);
            if (w.hasGrabBar() && hover.isPresent() && hover.get().window() == w) drawHoverFrame(pose, buffers, w, cam);
        }
        drawPointer(pose, buffers, camera, cam, hover);
        RenderCompat.flush(buffers);
    }

    /**
     * Thin frame around the window under the hand, so it is obvious which window a click (and, for
     * desktop apps, the keyboard) goes to. Cyan normally, red when the window is locked.
     */
    private static void drawHoverFrame(PoseStack pose, MultiBufferSource buffers, SpatialWindow w, Vec3 cam) {
        SpatialPose p = w.pose();
        double t = 1.5 / SpatialWindow.PX_PER_METER;
        double hw = w.widthMeters();
        double hh = w.heightMeters();
        // a hair in front of the glass, so the frame never z-fights with the window quad
        Vec3 c = p.center().subtract(cam).add(p.normal().scale(0.002));
        int color = w.isLocked() ? 0xC8F43F5E : 0xB467E8F9;
        rect(pose, buffers, c.add(p.up().scale(hh / 2)), p, hw + t, t, color);
        rect(pose, buffers, c.subtract(p.up().scale(hh / 2)), p, hw + t, t, color);
        rect(pose, buffers, c.subtract(p.right().scale(hw / 2)), p, t, hh + t, color);
        rect(pose, buffers, c.add(p.right().scale(hw / 2)), p, t, hh + t, color);
    }

    private static void drawWindow(PoseStack pose, MultiBufferSource buffers, SpatialWindow w, Vec3 cam) {
        ResourceLocation tex = w.texture();
        if (tex == null) return;
        SpatialPose p = w.pose();
        Vec3 c = p.center().subtract(cam);
        Vec3 r = p.right().scale(w.widthMeters() / 2);
        Vec3 u = p.up().scale(w.heightMeters() / 2);
        // framebuffer textures are bottom-up: v = 1 is the top row of the window
        int tint = com.kasperstudios.kvr.power.client.PowerClient.strobeOn() ? ALARM_RED : WHITE;
        RenderCompat.quad(pose, buffers, tex, c.subtract(r).add(u), c.subtract(r).subtract(u), c.add(r).subtract(u), c.add(r).add(u), 0f, 1f, 1f, 0f, tint);
    }

    private static void drawGrabBar(PoseStack pose, MultiBufferSource buffers, SpatialWindow w, Vec3 cam, Optional<SpatialRaycast.RayHit> hover) {
        SpatialPose p = w.pose();
        double m = 1.0 / SpatialWindow.PX_PER_METER * w.scale();
        Vec3 base = p.center().subtract(cam).add(p.up().scale(-(w.height() / 2.0 + SpatialWindow.GRAB_GAP + SpatialWindow.GRAB_H / 2.0) * m));

        boolean hot = KVRSpatial.input().holding(w) || hover.filter(h -> h.window() == w && h.region() == SpatialRaycast.Region.GRAB_BAR).isPresent();
        // the handle itself tells the state: red = locked (cannot be grabbed), cyan = Body, white = World/Follow
        int color;
        if (w.isLocked()) color = 0xB4F43F5E;
        else if (w.anchor().mode() == SpatialAnchor.Mode.BODY) color = hot ? 0xEB67E8F9 : 0xA067E8F9;
        else color = hot ? 0xEBFFFFFF : 0x96FFFFFF;
        boolean grow = hot && !w.isLocked();
        rect(pose, buffers, base, p, SpatialWindow.GRAB_W * m * (grow ? 1.15 : 1.0), SpatialWindow.GRAB_H * m, color);
    }

    private static void drawPointer(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam, Optional<SpatialRaycast.RayHit> hover) {
        Optional<SpatialRaycast.Ray> ray = KVRSpatial.input().ray();
        if (ray.isEmpty()) return;
        double dist = hover.map(SpatialRaycast.RayHit::distance).orElse(2.0) - 0.004;
        Vec3 at = ray.get().origin().add(ray.get().dir().scale(dist)).subtract(cam);
        Vec3 left = new Vec3(camera.getLeftVector()).scale(POINTER);
        Vec3 up = new Vec3(camera.getUpVector()).scale(POINTER);
        int color = hover.isPresent() ? WHITE : 0xAAFFFFFF;
        RenderCompat.quad(pose, buffers, GlassStyle.GRAB_HANDLE_TEXTURE, at.add(left).add(up), at.add(left).subtract(up), at.subtract(left).subtract(up), at.subtract(left).add(up), 0f, 0f, 1f, 1f, color);
    }

    private static void rect(PoseStack pose, MultiBufferSource buffers, Vec3 center, SpatialPose p, double w, double h, int argb) {
        Vec3 r = p.right().scale(w / 2);
        Vec3 u = p.up().scale(h / 2);
        RenderCompat.quad(pose, buffers, GlassStyle.GRAB_HANDLE_TEXTURE, center.subtract(r).add(u), center.subtract(r).subtract(u), center.add(r).subtract(u), center.add(r).add(u), 0f, 0f, 1f, 1f, argb);
    }
}
