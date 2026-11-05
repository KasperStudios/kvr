package com.kasperstudios.kvr.spatial.render;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Draws every window as a textured quad in the world after translucent blocks, plus its grab bar
 * and the hand pointer where the ray touches a panel (in VR the pointer lives on the surface, not
 * in a HUD). Hooked through {@code KVRClient.onWorldRender}, so this class never sees a loader.
 */
public final class SpatialRenderer {

    private static final float POINTER = 0.012f;
    private static long lastFrame;

    private SpatialRenderer() {}

    /** @param view camera rotation matrix of the level pass (vertices are camera-relative) */
    public static void render(Matrix4f view, Camera camera, float partialTick) {
        if (!KVRSpatial.ready() || camera.getEntity() == null) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;

        WindowManager windows = KVRSpatial.windows();
        PlayerView pv = PlayerView.capture(camera, partialTick);
        windows.update(pv, dt);
        Optional<SpatialRaycast.RayHit> hover = KVRSpatial.input().hover();

        List<SpatialWindow> list = windows.drawable();
        // 1) offscreen: each window paints its GUI into its own texture
        for (SpatialWindow w : list) {
            boolean over = hover.isPresent() && hover.get().window() == w && hover.get().region() == SpatialRaycast.Region.CONTENT;
            w.paint(over ? (int) hover.get().px() : -1, over ? (int) hover.get().py() : -1, partialTick);
        }

        // 2) world pass, far to near for correct blending of the glass
        Vec3 cam = camera.getPosition();
        list.sort(Comparator.comparingDouble((SpatialWindow w) -> w.pose().center().distanceToSqr(cam)).reversed());

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        mv.mul(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        try {
            for (SpatialWindow w : list) {
                drawWindow(w, cam);
                if (w.hasGrabBar()) drawGrabBar(w, cam, hover);
            }
            drawPointer(camera, cam, hover);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawWindow(SpatialWindow w, Vec3 cam) {
        SpatialPose p = w.pose();
        Vec3 c = p.center().subtract(cam);
        Vec3 r = p.right().scale(w.widthMeters() / 2);
        Vec3 u = p.up().scale(w.heightMeters() / 2);

        RenderSystem.setShaderTexture(0, w.textureId());
        //? if >=1.21.2 {
        /*RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_TEX_COLOR);
        *///?} else {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        //?}
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // FBO textures are bottom-up: v = 1 is the top row of the window
        tex(b, c.subtract(r).add(u), 0f, 1f);
        tex(b, c.subtract(r).subtract(u), 0f, 0f);
        tex(b, c.add(r).subtract(u), 1f, 0f);
        tex(b, c.add(r).add(u), 1f, 1f);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void drawGrabBar(SpatialWindow w, Vec3 cam, Optional<SpatialRaycast.RayHit> hover) {
        SpatialPose p = w.pose();
        double m = 1.0 / SpatialWindow.PX_PER_METER * w.scale();
        Vec3 down = p.up().scale(-(w.height() / 2.0 + SpatialWindow.GRAB_GAP + SpatialWindow.GRAB_H / 2.0) * m);
        Vec3 base = p.center().subtract(cam).add(down);

        boolean barHot = KVRSpatial.input().holding(w) || hover.filter(h -> h.window() == w && h.region() == SpatialRaycast.Region.GRAB_BAR).isPresent();
        int alpha = barHot ? 235 : 150;
        rect(base, p, SpatialWindow.GRAB_W * m * (barHot ? 1.15 : 1.0), SpatialWindow.GRAB_H * m, 255, 255, 255, alpha);

        // anchor toggle: cyan = Body, white = World
        double tx = (SpatialWindow.GRAB_W / 2.0 + SpatialWindow.TOGGLE_GAP + SpatialWindow.TOGGLE_SIZE / 2.0) * m;
        boolean body = w.anchor().mode() == SpatialAnchor.Mode.BODY;
        rect(base.add(p.right().scale(tx)), p, SpatialWindow.TOGGLE_SIZE * m * 0.6, SpatialWindow.TOGGLE_SIZE * m * 0.6, body ? 103 : 255, body ? 232 : 255, body ? 249 : 255, 210);
    }

    private static void drawPointer(Camera camera, Vec3 cam, Optional<SpatialRaycast.RayHit> hover) {
        Optional<SpatialRaycast.Ray> ray = KVRSpatial.input().ray();
        if (ray.isEmpty()) return;
        double dist = hover.map(SpatialRaycast.RayHit::distance).orElse(2.0) - 0.004;
        Vec3 at = ray.get().origin().add(ray.get().dir().scale(dist)).subtract(cam);
        Vec3 left = new Vec3(camera.getLeftVector()).scale(POINTER);
        Vec3 up = new Vec3(camera.getUpVector()).scale(POINTER);
        int a = hover.isPresent() ? 255 : 170;
        quadColor(at.add(left).add(up), at.add(left).subtract(up), at.subtract(left).subtract(up), at.subtract(left).add(up), 255, 255, 255, a);
    }

    private static void rect(Vec3 center, SpatialPose p, double w, double h, int r, int g, int b, int a) {
        Vec3 rr = p.right().scale(w / 2);
        Vec3 uu = p.up().scale(h / 2);
        quadColor(center.subtract(rr).add(uu), center.subtract(rr).subtract(uu), center.add(rr).subtract(uu), center.add(rr).add(uu), r, g, b, a);
    }

    private static void quadColor(Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, int r, int g, int b, int a) {
        //? if >=1.21.2 {
        /*RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_COLOR);
        *///?} else {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        //?}
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Vec3 v : new Vec3[] {v0, v1, v2, v3}) bb.addVertex((float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, a);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void tex(BufferBuilder b, Vec3 v, float u, float vv) {
        b.addVertex((float) v.x, (float) v.y, (float) v.z).setUv(u, vv).setColor(255, 255, 255, 255);
    }
}
