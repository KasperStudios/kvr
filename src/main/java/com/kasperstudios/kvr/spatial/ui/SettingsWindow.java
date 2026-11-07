package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * In-visor KVR OS Settings Window (SPEC 5.3: kvr.settings).
 * Allows tweaking window opacity, public window preferences, and quick power actions directly in 3D.
 */
public final class SettingsWindow extends SpatialWindow {

    private static final int W = 260;
    private static final int H = 180;
    private static final int ROW_H = 24;

    public SettingsWindow(SpatialAnchor anchor) {
        super("kvr:settings", Kind.SYSTEM, W, H, anchor);
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.settings.title");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 10, 9, GlassStyle.TEXT);
        g.fill(10, 24, width - 10, 25, 0x30FFFFFF);

        // Row 1: Default window visibility (Public / Private)
        int y1 = 34;
        boolean pub = KvrConfig.client().defaultWindowPublic();
        GlassStyle.text(g, Component.translatable("kvr.window.menu.visibility"), 10, y1 + 5, GlassStyle.MUTED);
        boolean hot1 = GlassStyle.inside(mx, my, width - 85, y1, 75, ROW_H - 4);
        GlassStyle.panel(g, width - 85, y1, 75, ROW_H - 4, hot1);
        GlassStyle.centered(g, Component.literal(pub ? "PUBLIC" : "PRIVATE"), width - 47, y1 + 5, pub ? GlassStyle.ACCENT : GlassStyle.TEXT);

        // Row 2: Window Opacity control
        int y2 = 64;
        int opacity = KvrConfig.client().windowOpacity();
        GlassStyle.text(g, Component.translatable("kvr.settings.opacity", opacity + "%"), 10, y2 + 5, GlassStyle.MUTED);
        boolean hotMinus = GlassStyle.inside(mx, my, width - 85, y2, 35, ROW_H - 4);
        boolean hotPlus = GlassStyle.inside(mx, my, width - 45, y2, 35, ROW_H - 4);
        GlassStyle.panel(g, width - 85, y2, 35, ROW_H - 4, hotMinus);
        GlassStyle.panel(g, width - 45, y2, 35, ROW_H - 4, hotPlus);
        GlassStyle.centered(g, Component.literal("-10"), width - 68, y2 + 5, GlassStyle.TEXT);
        GlassStyle.centered(g, Component.literal("+10"), width - 28, y2 + 5, GlassStyle.TEXT);

        // Row 3: Open Power Management Window
        int y3 = 94;
        boolean hotPower = GlassStyle.inside(mx, my, 10, y3, width - 20, ROW_H - 4);
        GlassStyle.panel(g, 10, y3, width - 20, ROW_H - 4, hotPower);
        GlassStyle.centered(g, Component.translatable("kvr.power.window"), width / 2, y3 + 5, GlassStyle.ACCENT);

        // Row 4: Host Desktop consent/status
        int y4 = 124;
        boolean desktopOn = com.kasperstudios.kvr.desktop.client.HostDesktopClient.enabledLocally();
        boolean hotDesktop = GlassStyle.inside(mx, my, 10, y4, width - 20, ROW_H - 4);
        GlassStyle.panel(g, 10, y4, width - 20, ROW_H - 4, hotDesktop);
        GlassStyle.centered(g, Component.translatable(desktopOn ? "kvr.desktop.enabled" : "kvr.desktop.disabled"), width / 2, y4 + 5, desktopOn ? GlassStyle.OK : GlassStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y1 = 34;
        if (GlassStyle.inside(mx, my, width - 85, y1, 75, ROW_H - 4)) {
            boolean next = !KvrConfig.client().defaultWindowPublic();
            KvrConfig.updateClient("default_window_public", Boolean.toString(next));
            return true;
        }

        int y2 = 64;
        if (GlassStyle.inside(mx, my, width - 85, y2, 35, ROW_H - 4)) {
            int op = Math.max(10, KvrConfig.client().windowOpacity() - 10);
            KvrConfig.updateClient("window_opacity", Integer.toString(op));
            return true;
        }
        if (GlassStyle.inside(mx, my, width - 45, y2, 35, ROW_H - 4)) {
            int op = Math.min(100, KvrConfig.client().windowOpacity() + 10);
            KvrConfig.updateClient("window_opacity", Integer.toString(op));
            return true;
        }

        int y3 = 94;
        if (GlassStyle.inside(mx, my, 10, y3, width - 20, ROW_H - 4)) {
            KVRSpatial.openPowerWindow();
            return true;
        }

        int y4 = 124;
        if (GlassStyle.inside(mx, my, 10, y4, width - 20, ROW_H - 4)) {
            boolean next = !com.kasperstudios.kvr.desktop.client.HostDesktopClient.enabledLocally();
            KvrConfig.updateClient("enable_host_desktop_windows", Boolean.toString(next));
            return true;
        }

        return false;
    }
}
