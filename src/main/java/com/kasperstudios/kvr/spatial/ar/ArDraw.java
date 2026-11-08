package com.kasperstudios.kvr.spatial.ar;

import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 0033 — flat-colour world primitives for the cocoon and AR vision, on top of {@link RenderCompat#quad} (same
 * full-bright, translucent, double-sided, no-depth-write render type as the windows), so this class has no version
 * branches. All points are camera-relative.
 *
 * <p>Solid colour = every vertex samples the centre texel of {@link #WHITE}. The grab handle is a white pill, its
 * centre is opaque white. If a build ever ships a non-white handle, point {@link #WHITE} at a 1×1 white PNG.
 */
public final class ArDraw {

    public static final ResourceLocation WHITE = GlassStyle.GRAB_HANDLE_TEXTURE;
    private static final float C = 0.5f;

    private ArDraw() {}

    /** Quad tl → bl → br → tr. */
    public static void quad(PoseStack pose, MultiBufferSource buffers, Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, int argb) {
        if ((argb >>> 24) == 0) return;
        RenderCompat.quad(pose, buffers, WHITE, tl, bl, br, tr, C, C, C, C, argb);
    }

    /** Thin ribbon a → b, turned to face the camera (camera = origin). */
    public static void line(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, double width, int argb) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid);
        double len = side.length();
        if (len < 1e-9) return;
        side = side.scale(width / 2 / len);
        quad(pose, buffers, a.add(side), a.subtract(side), b.subtract(side), b.add(side), argb);
    }

    /** Ribbon lying flat on a horizontal plane (floor grid, redstone overlays). */
    public static void flatLine(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, double width, int argb) {
        Vec3 d = b.subtract(a);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        double len = side.length();
        if (len < 1e-9) return;
        side = side.scale(width / 2 / len);
        quad(pose, buffers, a.add(side), a.subtract(side), b.subtract(side), b.add(side), argb);
    }

    /** Camera-facing square of half-size {@code r} (markers, pulses). */
    public static void billboard(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, double r, int argb) {
        Vec3 left = new Vec3(camera.getLeftVector()).scale(r);
        Vec3 up = new Vec3(camera.getUpVector()).scale(r);
        quad(pose, buffers, at.add(left).add(up), at.add(left).subtract(up), at.subtract(left).subtract(up), at.subtract(left).add(up), argb);
    }

    /** Horizontal ring (rednet waves, horizon line). */
    public static void ring(PoseStack pose, MultiBufferSource buffers, Vec3 center, double radius, int segments, double width, int argb) {
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            Vec3 p = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (prev != null) line(pose, buffers, prev, p, width, argb);
            prev = p;
        }
    }

    /** Camera-facing text centred on {@code at}; {@code scale} = blocks per GUI pixel (0.02 ≈ nametag). */
    public static void label(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, String text, double scale, int argb) {
        Vec3 right = new Vec3(camera.getLeftVector()).reverse();
        Vec3 down = new Vec3(camera.getUpVector()).reverse();
        Vec3 normal = new Vec3(camera.getLookVector()).reverse();
        Matrix4f m = plane(pose, at, right, down, normal, scale);
        RenderCompat.text(buffers, m, text, -RenderCompat.textWidth(text) / 2f, -4f, argb);
    }

    /** Health / progress bar above a point, facing the camera; {@code frac} 0..1. */
    public static void bar(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, double w, double h, float frac, int fill, int back) {
        Vec3 right = new Vec3(camera.getLeftVector()).reverse();
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 r = right.scale(w / 2);
        Vec3 u = up.scale(h / 2);
        quad(pose, buffers, at.subtract(r).add(u), at.subtract(r).subtract(u), at.add(r).subtract(u), at.add(r).add(u), back);
        double f = Math.max(0, Math.min(1, frac));
        Vec3 l = at.subtract(r);
        Vec3 e = l.add(right.scale(w * f));
        Vec3 nudge = new Vec3(camera.getLookVector()).scale(-0.002);
        quad(pose, buffers, l.add(u).add(nudge), l.subtract(u).add(nudge), e.subtract(u).add(nudge), e.add(u).add(nudge), fill);
    }

    /** Same matrix as GhostRenderer: GUI pixels (x right, y down) onto a camera-relative plane. */
    static Matrix4f plane(PoseStack pose, Vec3 origin, Vec3 right, Vec3 down, Vec3 normal, double s) {
        Matrix4f basis = new Matrix4f(
            (float) (right.x * s), (float) (right.y * s), (float) (right.z * s), 0f,
            (float) (down.x * s), (float) (down.y * s), (float) (down.z * s), 0f,
            (float) (normal.x * s), (float) (normal.y * s), (float) (normal.z * s), 0f,
            (float) origin.x, (float) origin.y, (float) origin.z, 1f);
        return new Matrix4f(pose.last().pose()).mul(basis);
    }

    public static int alpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** Mix towards white by {@code k} (neon edge highlight). */
    public static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * k);
        g += Math.round((255 - g) * k);
        b += Math.round((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }
}
