package com.kasperstudios.kvr.share.client;

import com.kasperstudios.kvr.hack.HackGlasses;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.hack.client.SpyClient;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Other players' Public windows as glass panels exactly where their owner sees them: title, owner
 * name and the window's text. Same two-phase world pass as {@code SpatialRenderer}: first every panel
 * paints into its own offscreen surface, then the quads are queued (painting flushes the shared
 * buffer source, so it must not happen between queued quads).
 *
 * <p>If the viewer wears HackGlasses and is looking at the owner, the green ghost view already shows
 * these windows, so they are skipped here.
 */
public final class PublicWindowRenderer {

    private static final int MAX_W = 1024;
    private static final int MAX_H = 768;
    private static final int LINE = 10;
    private static final Map<String, RenderCompat.Surface> SURFACES = new HashMap<>();

    private record Panel(String key, String owner, WindowSummary.Entry entry, int w, int h) {}

    private PublicWindowRenderer() {}

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Map<UUID, PublicWindowsClient.Seen> seen = PublicWindowsClient.seen();
        List<Panel> panels = new ArrayList<>();
        boolean hacker = HackGlasses.isWearing(mc.player);
        for (Map.Entry<UUID, PublicWindowsClient.Seen> e : seen.entrySet()) {
            if (hacker && SpyClient.seen().containsKey(e.getKey())) continue;
            List<WindowSummary.Entry> list = e.getValue().summary().windows();
            for (int i = 0; i < list.size(); i++) {
                WindowSummary.Entry w = list.get(i);
                if (w.minimized() || w.width() < 16 || w.height() < 16) continue;
                panels.add(new Panel(e.getKey() + "_" + i, e.getValue().owner(), w, Math.min(MAX_W, w.width()), Math.min(MAX_H, w.height())));
            }
        }
        releaseUnused(panels);
        if (panels.isEmpty()) return;

        // 1) offscreen
        for (Panel p : panels) {
            RenderCompat.Surface s = SURFACES.computeIfAbsent(p.key(), k -> RenderCompat.surface(GlassStyle.id("public/" + k.toLowerCase())));
            s.paint(p.w(), p.h(), 2, g -> paint(g, p));
        }

        // 2) world pass, far to near
        Vec3 cam = camera.getPosition();
        panels.sort((a, b) -> Double.compare(distSq(b.entry(), cam), distSq(a.entry(), cam)));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Panel p : panels) {
            WindowSummary.Entry w = p.entry();
            SpatialPose sp = new SpatialPose(new Vec3(w.x(), w.y(), w.z()), w.yaw(), w.pitch());
            Vec3 c = sp.center().subtract(cam);
            Vec3 r = sp.right().scale(w.width() / SpatialWindow.PX_PER_METER / 2);
            Vec3 u = sp.up().scale(w.height() / SpatialWindow.PX_PER_METER / 2);
            // framebuffer textures are bottom-up: v = 1 is the top row
            RenderCompat.quad(pose, buffers, SURFACES.get(p.key()).texture(),
                c.subtract(r).add(u), c.subtract(r).subtract(u), c.add(r).subtract(u), c.add(r).add(u), 0f, 1f, 1f, 0f, 0xFFFFFFFF);
        }
        RenderCompat.flush(buffers);
    }

    private static void paint(net.minecraft.client.gui.GuiGraphics g, Panel p) {
        Font font = Minecraft.getInstance().font;
        int w = p.w();
        int h = p.h();
        GlassStyle.panel(g, 0, 0, w, h, false);
        int ownerW = font.width(p.owner());
        GlassStyle.text(g, Component.literal(p.owner()), w - ownerW - 8, 8, GlassStyle.ACCENT);
        String title = p.entry().title();
        int maxTitle = Math.max(20, w - ownerW - 24);
        if (font.width(title) > maxTitle) title = font.plainSubstrByWidth(title, maxTitle - 6) + "\u2026";
        GlassStyle.text(g, Component.literal(title).withStyle(ChatFormatting.BOLD), 8, 8, GlassStyle.TEXT);
        g.fill(8, 21, w - 8, 22, 0x30FFFFFF);
        int y = 27;
        for (FormattedCharSequence line : font.split(Component.literal(p.entry().text()), w - 16)) {
            if (y > h - LINE - 2) break;
            g.drawString(font, line, 8, y, GlassStyle.MUTED, false);
            y += LINE;
        }
    }

    private static double distSq(WindowSummary.Entry e, Vec3 cam) {
        double dx = e.x() - cam.x;
        double dy = e.y() - cam.y;
        double dz = e.z() - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void releaseUnused(List<Panel> panels) {
        if (SURFACES.isEmpty()) return;
        Set<String> live = new HashSet<>();
        for (Panel p : panels) live.add(p.key());
        Iterator<Map.Entry<String, RenderCompat.Surface>> it = SURFACES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RenderCompat.Surface> e = it.next();
            if (live.contains(e.getKey())) continue;
            e.getValue().close();
            it.remove();
        }
    }

    /** Disconnect / world change: free every framebuffer. */
    static void releaseAll() {
        if (SURFACES.isEmpty()) return;
        SURFACES.values().forEach(RenderCompat.Surface::close);
        SURFACES.clear();
    }
}
