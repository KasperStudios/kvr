package com.kasperstudios.kvr.spatial.space;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.spatial.ar.ArDraw;
import com.kasperstudios.kvr.spatial.render.CocoonRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The cocoon around the player while a Virtual Space is active (Creator's spec): about 4 blocks wide and 4 tall, an
 * elongated spindle with sharper top and bottom poles.
 *
 * <p>0034: the shell is OPAQUE — a dark cyber void that hides the real world completely (blocks, floor, mobs, sky),
 * with the neon grid, the scan band and the glowing poles on top. Drawn with {@link CocoonRenderType} (depth ALWAYS
 * + depth write), so windows and AR overlays inside the cocoon still render normally and nothing outside leaks in.
 *
 * <p>Profile: {@code r(t) = R · (1 − |t|^2.2)^0.9}, t ∈ [−1, 1] along the height → pointed tips (a sphere is
 * exponent 0.5 → blunt). Centre 1.4 blocks above the feet: tips at −0.6 and +3.4. The camera is always inside a
 * convex shell, so every view ray meets exactly one wall — submission order is enough, no sorting.
 */
public final class CocoonRenderer {

    private static final double RADIUS = 2.0;
    private static final double HALF_HEIGHT = 2.0;
    private static final double CENTER_Y = 1.4;
    private static final int RINGS = 16;
    private static final int MERIDIANS = 32;
    private static final double LINE = 0.014;
    /** Deep space colour of the shell: equator and poles, before the cocoon tint. */
    private static final int VOID_EQUATOR = 0x070B14;
    private static final int VOID_POLE = 0x010205;

    private CocoonRenderer() {}

    static void render(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
        KvrSpaceConfig.Prefs prefs = KvrSpaceConfig.get();
        if (!prefs.cocoon()) return;
        float level = VirtualSpaces.cocoonLevel();
        if (level <= 0f) return;
        Entity e = camera.getEntity();
        if (e == null) return;

        float neon = prefs.opacityPct() / 100f;         // 0034: the setting now drives the neon, the shell is solid
        float pulse = VirtualSpaces.switchPulse();
        int rgb = prefs.cocoonRgb();
        int edge = ArDraw.lighten(rgb, 0.30f + 0.4f * pulse);
        Vec3 c = e.getPosition(partialTick).add(0, CENTER_Y, 0).subtract(camera.getPosition());
        double grow = 0.82 + 0.18 * easeOut(level);
        double R = RADIUS * grow;
        double H = HALF_HEIGHT * grow;
        double time = System.currentTimeMillis() / 1000.0;
        double spin = time * 0.05;

        // 1) solid shell: dark void, faint cocoon tint towards the equator; alpha only during grow/fade
        int equator = mix(VOID_EQUATOR, rgb, 0.10f);
        int poleCol = mix(VOID_POLE, rgb, 0.03f);
        int shellA = Math.round(255 * level);
        for (int i = 0; i < RINGS; i++) {
            double t0 = -1 + 2.0 * i / RINGS;
            double t1 = -1 + 2.0 * (i + 1) / RINGS;
            int c0 = (shellA << 24) | mix(poleCol, equator, (float) (1 - Math.abs(t0)));
            int c1 = (shellA << 24) | mix(poleCol, equator, (float) (1 - Math.abs(t1)));
            for (int j = 0; j < MERIDIANS; j++) {
                double a0 = Math.PI * 2 * j / MERIDIANS;
                double a1 = Math.PI * 2 * (j + 1) / MERIDIANS;
                CocoonRenderType.quad(pose, buffers,
                    at(c, R, H, t1, a0), at(c, R, H, t0, a0), at(c, R, H, t0, a1), at(c, R, H, t1, a1),
                    c1, c0, c0, c1);
            }
        }

        // 2) neon grid on top of the shell: rings brighter at the equator, meridians drift slowly
        for (int i = 1; i < RINGS; i++) {
            double t = -1 + 2.0 * i / RINGS;
            int col = ArDraw.alpha(edge, clamp((0.25f + 0.35f * (float) (1 - Math.abs(t)) + 0.3f * pulse) * neon * level));
            for (int j = 0; j < MERIDIANS; j++) {
                double a0 = spin + Math.PI * 2 * j / MERIDIANS;
                double a1 = spin + Math.PI * 2 * (j + 1) / MERIDIANS;
                CocoonRenderType.line(pose, buffers, at(c, R, H, t, a0), at(c, R, H, t, a1), LINE, col);
            }
        }
        int meridian = ArDraw.alpha(edge, clamp((0.35f + 0.3f * pulse) * neon * level));
        for (int j = 0; j < MERIDIANS; j++) {
            double a = spin + Math.PI * 2 * j / MERIDIANS;
            for (int i = 0; i < RINGS; i++) {
                double t0 = -1 + 2.0 * i / RINGS;
                double t1 = -1 + 2.0 * (i + 1) / RINGS;
                CocoonRenderType.line(pose, buffers, at(c, R, H, t0, a), at(c, R, H, t1, a), LINE, meridian);
            }
        }

        // 3) scan band sliding between the poles, with a soft glow under it
        double ts = Math.sin(time * 0.8) * 0.85;
        int glow = ArDraw.alpha(rgb, clamp(0.18f * neon * level));
        int band = ArDraw.alpha(ArDraw.lighten(rgb, 0.6f), clamp((0.6f + 0.4f * neon) * level));
        int segs = MERIDIANS * 2;
        for (int j = 0; j < segs; j++) {
            double a0 = Math.PI * 2 * j / segs;
            double a1 = Math.PI * 2 * (j + 1) / segs;
            CocoonRenderType.quad(pose, buffers,
                at(c, R, H, ts + 0.06, a0), at(c, R, H, ts - 0.06, a0), at(c, R, H, ts - 0.06, a1), at(c, R, H, ts + 0.06, a1), glow);
            CocoonRenderType.line(pose, buffers, at(c, R, H, ts, a0), at(c, R, H, ts, a1), LINE * 2.2, band);
        }

        // 4) pole sparks: the two sharp tips glow
        int spark = ArDraw.alpha(ArDraw.lighten(rgb, 0.7f), clamp((0.5f + 0.5f * neon) * level));
        spark(pose, buffers, camera, c.add(0, H * 0.97, 0), spark);
        spark(pose, buffers, camera, c.add(0, -H * 0.97, 0), spark);
    }

    private static void spark(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, int argb) {
        Vec3 left = new Vec3(camera.getLeftVector()).scale(0.06);
        Vec3 up = new Vec3(camera.getUpVector()).scale(0.06);
        CocoonRenderType.quad(pose, buffers, at.add(left).add(up), at.add(left).subtract(up), at.subtract(left).subtract(up), at.subtract(left).add(up), argb);
    }

    /** Point on the spindle: t along the height (−1 bottom pole … 1 top pole), a around the axis. */
    static Vec3 at(Vec3 c, double R, double H, double t, double a) {
        double r = R * profile(t);
        return c.add(Math.cos(a) * r, t * H, Math.sin(a) * r);
    }

    /** Radius fraction at height t; 1 at the equator, 0 at the poles, pointed tips. Pure. */
    public static double profile(double t) {
        double k = 1 - Math.pow(Math.min(1, Math.abs(t)), 2.2);
        return k <= 0 ? 0 : Math.pow(k, 0.9);
    }

    /** Linear mix of two 0xRRGGBB colours. Pure. */
    static int mix(int from, int to, float k) {
        float x = Math.max(0f, Math.min(1f, k));
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * x);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * x);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * x);
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static double easeOut(float x) {
        double v = 1 - x;
        return 1 - v * v * v;
    }
}
