package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * "Рабочий стол" from the dock: the apps from {@code host_desktop_apps}. A click starts the app inside
 * kvr-desktopd's hidden compositor; its window then opens here as a {@link HostDesktopWindow}.
 * System window (no RAM slot); the app windows themselves count against RAM.
 */
public final class DesktopLauncherWindow extends SpatialWindow {

    private static final int W = 220;
    private static final int ROW = 22;
    private static final int TOP = 28;
    private final List<List<String>> apps;

    public DesktopLauncherWindow(SpatialAnchor anchor) {
        this(DesktopDaemon.apps(KvrConfig.client().hostDesktopApps()), anchor);
    }

    private DesktopLauncherWindow(List<List<String>> apps, SpatialAnchor anchor) {
        super("kvr:desktop_launcher", Kind.SYSTEM, W, Math.max(80, TOP + apps.size() * ROW + 34), anchor);
        this.apps = apps;
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.desktop.launcher");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.panel(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 8, 9, GlassStyle.TEXT);
        if (apps.isEmpty()) {
            GlassStyle.text(g, Component.translatable("kvr.desktop.no_apps"), 8, TOP + 4, GlassStyle.MUTED);
        }
        for (int i = 0; i < apps.size(); i++) {
            int y = TOP + i * ROW;
            boolean hot = GlassStyle.inside(mx, my, 8, y, width - 16, ROW - 4);
            GlassStyle.panel(g, 8, y, width - 16, ROW - 4, hot);
            GlassStyle.text(g, Component.literal(String.join(" ", apps.get(i))), 14, y + 5, GlassStyle.TEXT);
        }
        GlassStyle.text(g, Component.translatable("kvr.desktop.daemon." + DesktopDaemon.state().name().toLowerCase()), 8, height - 26, GlassStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < apps.size(); i++) {
            if (GlassStyle.inside(mx, my, 8, TOP + i * ROW, width - 16, ROW - 4)) {
                HostDesktopClient.launch(apps.get(i));
                requestClose();
                return true;
            }
        }
        return false;
    }
}
