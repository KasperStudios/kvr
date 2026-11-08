package com.kasperstudios.kvr.spatial.ar;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.config.KvrUiConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0033 — AR vision overlay (default whenever the glasses are worn and lit): redstone signal levels, Rednet ether
 * pulses, mob hitbox outline + health. 0034a: the floor grid is REMOVED (flat lines cut through hills and hung in
 * the air); the Block Inspector took its place. Each layer has its own toggle in Settings → Display &amp; dock.
 * Data comes from {@link ArScanner} / {@link ArInspector}; no world queries here.
 */
public final class ArRenderer {

    private static final int REDSTONE_LABEL_RANGE = 8;
    private static final int MAX_LABELS = 48;
    private static final long PULSE_MS = 1400;
    private static final int TX = 0xA78BFA;
    private static final int RX = 0x4ADE80;
    private static final int HOSTILE = 0xF43F5E;
    private static final int PLAYER = 0xC084FC;
    /** Inspector plate: blocks per GUI pixel (≈ a sign's text) and paddings in GUI px. */
    private static final double PLATE_SCALE = 0.0085;
    private static final int PLATE_PAD = 5;
    private static final int PLATE_LINE = 10;

    private ArRenderer() {}

    public static void render(PoseStack pose, MultiBufferSource buffers, Camera camera, float partialTick) {
        KvrSpaceConfig.Prefs p = KvrSpaceConfig.get();
        int accent = KvrUiConfig.get().theme().accent & 0xFFFFFF;
        Vec3 cam = camera.getPosition();
        if (p.arRedstone()) redstone(pose, buffers, camera, cam);
        if (p.arRednet()) rednet(pose, buffers, camera, cam, partialTick);
        if (p.arMobs()) mobs(pose, buffers, camera, cam, partialTick, accent);
        if (p.arInspector()) inspector(pose, buffers, camera, cam, p, accent);
    }

    // --- block inspector -----------------------------------------------------------------------

    private static void inspector(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam, KvrSpaceConfig.Prefs p, int accent) {
        ArInspector.Info info = ArInspector.info();
        if (info == null) return;
        float a = ArInspector.appear();
        float ease = 1f - (1f - a) * (1f - a) * (1f - a);
        double breathe = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0);

        // neon corner brackets around the block shape (+ faint edges), grows into place with the fade
        AABB b = info.box().inflate(0.004 + 0.06 * (1 - ease)).move(-cam.x, -cam.y, -cam.z);
        outline(pose, buffers, b, ArDraw.alpha(ArDraw.lighten(accent, (float) (0.15 + 0.2 * breathe)), 0.95f * ease));

        if (p.inspectorPosition() == KvrSpaceConfig.InspectorPosition.HUD) return; // drawn by ArInspector.renderHud

        Vec3 center = new Vec3((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2);
        Vec3 anchor;
        if (p.inspectorPosition() == KvrSpaceConfig.InspectorPosition.CENTER) {
            // on the block centre, pulled 0.6 block towards the eye so it is not inside the block
            Vec3 toEye = center.reverse();
            double len = toEye.length();
            anchor = len < 1e-6 ? center : center.add(toEye.scale(Math.min(0.6, len * 0.5) / len));
        } else {
            anchor = new Vec3(center.x, b.maxY + p.inspectorOffsetCm() / 100.0 + 0.12, center.z);
        }
        anchor = anchor.add(0, -0.08 * (1 - ease), 0); // slides up while fading in
        plate(pose, buffers, camera, anchor, info, accent, ease);
    }

    /** Holographic plate facing the camera, bottom-centre at {@code anchor}. */
    private static void plate(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 anchor, ArInspector.Info info, int accent, float a) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int w = Math.max(font.width(info.name()), font.width(info.id()));
        for (ArInspector.Line l : info.lines()) w = Math.max(w, font.width(l.text()));
        w = Math.min(w, 220) + PLATE_PAD * 2;
        int h = PLATE_PAD * 2 + 20 + info.lines().size() * PLATE_LINE;

        Vec3 right = new Vec3(camera.getLeftVector()).reverse();
        Vec3 up = new Vec3(camera.getUpVector());
        Vec3 normal = new Vec3(camera.getLookVector()).reverse();
        double s = PLATE_SCALE;
        Vec3 bl = anchor.subtract(right.scale(w * s / 2));
        Vec3 tl = bl.add(up.scale(h * s));
        Vec3 tr = tl.add(right.scale(w * s));
        Vec3 br = bl.add(right.scale(w * s));
        ArDraw.quad(pose, buffers, tl, bl, br, tr, ArDraw.alpha(0x0B1220, 0.80f * a));
        // accent stripe on the left and a thin line under the header
        Vec3 stripe = right.scale(2 * s);
        ArDraw.quad(pose, buffers, tl.add(normal.scale(0.001)), bl.add(normal.scale(0.001)), bl.add(stripe).add(normal.scale(0.001)), tl.add(stripe).add(normal.scale(0.001)), ArDraw.alpha(accent, a));
        Vec3 hl = tl.subtract(up.scale((PLATE_PAD + 19) * s)).add(normal.scale(0.001));
        ArDraw.quad(pose, buffers, hl, hl.subtract(up.scale(s * 0.6)), hl.subtract(up.scale(s * 0.6)).add(right.scale(w * s)), hl.add(right.scale(w * s)), ArDraw.alpha(accent, 0.45f * a));

        org.joml.Matrix4f m = ArDraw.plane(pose, tl.add(normal.scale(0.002)), right, up.reverse(), normal, s);
        float ta = Math.max(0.05f, a);
        com.kasperstudios.kvr.spatial.render.RenderCompat.text(buffers, m, info.name(), PLATE_PAD + 2, PLATE_PAD, ArDraw.alpha(0xFFFFFF, ta));
        com.kasperstudios.kvr.spatial.render.RenderCompat.text(buffers, m, info.id(), PLATE_PAD + 2, PLATE_PAD + 10, ArDraw.alpha(accent, ta));
        int y = PLATE_PAD + 22;
        for (ArInspector.Line l : info.lines()) {
            com.kasperstudios.kvr.spatial.render.RenderCompat.text(buffers, m, l.text(), PLATE_PAD + 2, y,
                ArDraw.alpha(l.color() & 0xFFFFFF, Math.max(0.05f, ta * ((l.color() >>> 24) / 255f))));
            y += PLATE_LINE;
        }
    }

    // --- redstone ------------------------------------------------------------------------------

    private static void redstone(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam) {
        List<ArScanner.Signal> list = ArScanner.signals();
        if (list.isEmpty()) return;
        int labels = 0;
        double labelRange2 = REDSTONE_LABEL_RANGE * REDSTONE_LABEL_RANGE;
        for (ArScanner.Signal s : list) {
            double x = s.pos().getX() - cam.x;
            double y = s.pos().getY() - cam.y;
            double z = s.pos().getZ() - cam.z;
            if (s.power() < 0) { // on/off component: small amber diamond above
                ArDraw.billboard(pose, buffers, camera, new Vec3(x + 0.5, y + 1.15, z + 0.5), 0.06, 0xE0FBBF24);
                continue;
            }
            int rgb = signalColor(s.power());
            if (s.wire()) {
                double h = y + 0.03;
                ArDraw.quad(pose, buffers, new Vec3(x + 0.12, h, z + 0.12), new Vec3(x + 0.12, h, z + 0.88),
                    new Vec3(x + 0.88, h, z + 0.88), new Vec3(x + 0.88, h, z + 0.12), ArDraw.alpha(rgb, 0.30f + 0.03f * s.power()));
            } else {
                ArDraw.billboard(pose, buffers, camera, new Vec3(x + 0.5, y + 1.15, z + 0.5), 0.07, ArDraw.alpha(rgb, 0.9f));
            }
            Vec3 at = new Vec3(x + 0.5, y + (s.wire() ? 0.35 : 1.4), z + 0.5);
            if (labels < MAX_LABELS && at.lengthSqr() <= labelRange2) {
                ArDraw.label(pose, buffers, camera, at, String.valueOf(s.power()), 0.016, 0xFF000000 | ArDraw.lighten(rgb, 0.25f));
                labels++;
            }
        }
    }

    /** Signal 1 = dim red, 15 = hot pink-red (same family as vanilla wire, readable in the dark). Pure. */
    public static int signalColor(int power) {
        float k = Math.max(0, Math.min(15, power)) / 15f;
        int r = 0x70 + Math.round((0xFF - 0x70) * k);
        int g = 0x10 + Math.round((0x4D - 0x10) * k);
        int b = 0x18 + Math.round((0x6D - 0x18) * k);
        return (r << 16) | (g << 8) | b;
    }

    // --- rednet ether --------------------------------------------------------------------------

    private static void rednet(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam, float partialTick) {
        if (camera.getEntity() == null) return;
        List<ArScanner.Pulse> list = ArScanner.pulses(PULSE_MS);
        if (list.isEmpty()) return;
        Vec3 chest = camera.getEntity().getPosition(partialTick).add(0, 1.1, 0).subtract(cam);
        long now = System.currentTimeMillis();
        int row = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            ArScanner.Pulse p = list.get(i);
            float t = Math.min(1f, (now - p.at()) / (float) PULSE_MS);
            float fade = 1f - t;
            int rgb = p.outgoing() ? TX : RX;
            // TX: the wave leaves the player; RX: it comes in from the ether
            double radius = p.outgoing() ? 0.4 + 5.6 * t : 6.0 - 5.6 * t;
            ArDraw.ring(pose, buffers, chest, radius, 64, 0.03, ArDraw.alpha(rgb, 0.75f * fade));
            if (row < 3) {
                String text = p.outgoing()
                    ? "TX \u2192 " + (p.peer() < 0 ? "*" : "#" + p.peer()) + proto(p.protocol())
                    : "RX \u2190 #" + p.peer() + proto(p.protocol()) + (p.distance() >= 0 ? " \u00b7 " + p.distance() + "m" : "");
                ArDraw.label(pose, buffers, camera, chest.add(0, 1.15 + row * 0.2, 0), text, 0.012, ArDraw.alpha(ArDraw.lighten(rgb, 0.3f), Math.max(0.15f, fade)));
                row++;
            }
        }
    }

    private static String proto(String protocol) {
        if (protocol.isEmpty()) return "";
        return " [" + (protocol.length() > 16 ? protocol.substring(0, 16) + "\u2026" : protocol) + "]";
    }

    // --- mobs ----------------------------------------------------------------------------------

    private static void mobs(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam, float partialTick, int accent) {
        LivingEntity target = ArScanner.target();
        for (LivingEntity e : ArScanner.mobs()) {
            if (e == target || e.isRemoved()) continue;
            float max = e.getMaxHealth();
            if (max <= 0 || e.getHealth() >= max) continue;
            if (e.distanceToSqr(cam) > 12 * 12) continue;
            Vec3 top = e.getPosition(partialTick).add(0, e.getBbHeight() + 0.3, 0).subtract(cam);
            ArDraw.bar(pose, buffers, camera, top, 0.5, 0.035, e.getHealth() / max, ArDraw.alpha(color(e, accent), 0.85f), 0x90000000);
        }
        if (target == null || target.isRemoved()) return;

        int rgb = color(target, accent);
        Vec3 lerp = target.getPosition(partialTick);
        AABB bb = target.getBoundingBox().move(lerp.subtract(target.position())).inflate(0.04).move(-cam.x, -cam.y, -cam.z);
        outline(pose, buffers, bb, ArDraw.alpha(ArDraw.lighten(rgb, 0.2f), 0.9f));

        float max = Math.max(1f, target.getMaxHealth());
        float hp = Math.max(0f, target.getHealth());
        Vec3 top = new Vec3((bb.minX + bb.maxX) / 2, bb.maxY + 0.35, (bb.minZ + bb.maxZ) / 2);
        ArDraw.bar(pose, buffers, camera, top, 0.8, 0.06, hp / max, ArDraw.alpha(rgb, 0.95f), 0xA0000000);
        String hpText = "\u2764 " + num(hp) + "/" + num(max);
        ArDraw.label(pose, buffers, camera, top.add(0, 0.16, 0), hpText, 0.014, 0xFFFFFFFF);
        ArDraw.label(pose, buffers, camera, top.add(0, 0.34, 0), target.getDisplayName().getString(), 0.016, 0xFF000000 | ArDraw.lighten(rgb, 0.4f));
    }

    /** Hostile red, players purple, everything else the theme accent. */
    private static int color(LivingEntity e, int accent) {
        if (e instanceof Enemy) return HOSTILE;
        if (e instanceof Player) return PLAYER;
        return accent;
    }

    /** 12 edges, with brighter corner brackets (the "cyber" look). */
    private static void outline(PoseStack pose, MultiBufferSource buffers, AABB b, int argb) {
        Vec3[] c = {
            new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ),
            new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ)
        };
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        int faint = ArDraw.alpha(argb & 0xFFFFFF, ((argb >>> 24) / 255f) * 0.35f);
        for (int[] e : edges) {
            Vec3 a = c[e[0]];
            Vec3 z = c[e[1]];
            ArDraw.line(pose, buffers, a, z, 0.012, faint);
            double len = z.subtract(a).length();
            double k = Math.min(0.3, 0.25 * len) / Math.max(1e-6, len);
            ArDraw.line(pose, buffers, a, a.add(z.subtract(a).scale(k)), 0.03, argb);
            ArDraw.line(pose, buffers, z, z.add(a.subtract(z).scale(k)), 0.03, argb);
        }
    }

    private static String num(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
