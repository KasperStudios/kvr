package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.script.BuiltinApps;
import com.kasperstudios.kvr.script.VrApp;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Main application launcher menu (ADR-001 Gesture 1: Launcher).
 * System window (Kind.SYSTEM, does not take a RAM slot).
 * Displays all installed KVR apps with their icons, names, and running status.
 * Clicking an app card launches it or restores its window into view.
 */
public final class AppLauncherWindow extends SpatialWindow {

    private static final int W = 280;
    private static final int CARD_H = 26;
    private static final int TOP = 30;

    public record Entry(String id, String name, String version, boolean running) {}

    private final BuiltinApps appHost;
    private final List<Entry> entries = new ArrayList<>();

    public AppLauncherWindow(BuiltinApps appHost, SpatialAnchor anchor) {
        super("kvr:app_launcher", Kind.SYSTEM, W, Math.max(120, TOP + BuiltinApps.SYSTEM_APPS.size() * (CARD_H + 4) + 16), anchor);
        this.appHost = appHost;
        refresh();
    }

    private void refresh() {
        entries.clear();
        for (String id : BuiltinApps.SYSTEM_APPS) {
            String name = appHost.app(id).map(VrApp::name).orElse(id);
            String version = appHost.app(id).map(VrApp::version).orElse("1.0.0");
            boolean running = KVRSpatial.windows().all().stream().anyMatch(w -> w.id().equals("app:" + id));
            entries.add(new Entry(id, name, version, running));
        }
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.gesture.launcher");
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 10, 9, GlassStyle.TEXT);
        GlassStyle.text(g, Component.literal("KVR OS Apps"), width - 75, 9, GlassStyle.ACCENT);

        g.fill(10, TOP - 4, width - 10, TOP - 3, 0x30FFFFFF);

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int y = TOP + i * (CARD_H + 4);
            boolean hot = GlassStyle.inside(mx, my, 10, y, width - 20, CARD_H);
            GlassStyle.panel(g, 10, y, width - 20, CARD_H, hot);

            // Icon indicator
            g.fill(14, y + 5, 20, y + CARD_H - 5, e.running() ? GlassStyle.OK : GlassStyle.ACCENT);

            GlassStyle.text(g, Component.literal(e.name()).withStyle(ChatFormatting.BOLD), 26, y + 5, GlassStyle.TEXT);
            GlassStyle.text(g, Component.literal("v" + e.version()), 26, y + 15, GlassStyle.MUTED);

            String status = e.running() ? "● RUN" : "START";
            int statusColor = e.running() ? GlassStyle.OK : (hot ? GlassStyle.TEXT : GlassStyle.MUTED);
            GlassStyle.text(g, Component.literal(status), width - 58, y + 9, statusColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < entries.size(); i++) {
            int y = TOP + i * (CARD_H + 4);
            if (GlassStyle.inside(mx, my, 10, y, width - 20, CARD_H)) {
                appHost.launch(entries.get(i).id());
                requestClose();
                return true;
            }
        }
        return false;
    }
}
