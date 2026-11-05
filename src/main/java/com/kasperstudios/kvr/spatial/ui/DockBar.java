package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.spatial.anchor.LazyFollowAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The dock, Quest "universal menu" style: a floating glass bar that sits low in front of the player
 * (look down to use it) and lazily follows when the player turns away. Not a HUD — it is a
 * {@link SpatialWindow} like any app. Shows pinned/running apps, minimised windows to restore,
 * and the status block (battery, clock).
 */
public final class DockBar extends SpatialWindow {

    private static final int ICON = 28;
    private static final int GAP = 6;
    private static final int STATUS_W = 76;
    private static final int H = 44;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final SpatialConfig config;
    private final WindowManager windows;

    public DockBar(SpatialConfig config, WindowManager windows) {
        super("kvr:dock", Kind.SYSTEM, 240, H, new LazyFollowAnchor(0.85, -0.5, 40));
        this.config = config;
        this.windows = windows;
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.dock");
    }

    @Override
    public boolean hasGrabBar() {
        return false;
    }

    @Override
    public void tick() {
        int slots = config.apps().dockItems().size() + windows.minimized().size();
        width = GlassStyle.PADDING * 2 + Math.max(1, slots) * (ICON + GAP) + STATUS_W;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.panel(g, 0, 0, width, height, false);
        int x = GlassStyle.PADDING;
        int y = (height - ICON) / 2;

        for (SpatialConfig.DockItem item : config.apps().dockItems()) {
            boolean hot = GlassStyle.inside(mx, my, x, y, ICON, ICON);
            GlassStyle.panel(g, x, y, ICON, ICON, hot);
            GlassStyle.sprite(g, item.icon(), x + 6, y + 6, 16, 16);
            if (item.running()) g.fill(x + ICON / 2 - 1, y + ICON + 2, x + ICON / 2 + 1, y + ICON + 4, GlassStyle.TEXT);
            x += ICON + GAP;
        }
        for (SpatialWindow w : windows.minimized()) {
            boolean hot = GlassStyle.inside(mx, my, x, y, ICON, ICON);
            GlassStyle.panel(g, x, y, ICON, ICON, hot);
            String mark = w.title().getString();
            GlassStyle.centered(g, Component.literal(mark.isEmpty() ? "?" : mark.substring(0, 1)), x + ICON / 2, y + 10, GlassStyle.ACCENT);
            x += ICON + GAP;
        }

        int sx = width - STATUS_W - GlassStyle.PADDING + 4;
        g.fill(sx - 6, 10, sx - 5, height - 10, 0x40FFFFFF);
        int pct = config.batteryPercent().getAsInt();
        GlassStyle.sprite(g, GlassStyle.BATTERY, sx, height / 2 - 8, 16, 16);
        int fill = Math.round(10 * pct / 100f);
        g.fill(sx + 2, height / 2 - 3, sx + 2 + fill, height / 2 + 3, pct <= 20 ? GlassStyle.DANGER : GlassStyle.OK);
        GlassStyle.text(g, Component.literal(pct + "%"), sx + 20, height / 2 - 8, GlassStyle.TEXT);
        GlassStyle.text(g, Component.literal(LocalTime.now().format(CLOCK)), sx + 20, height / 2 + 2, GlassStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (mx >= width - STATUS_W - GlassStyle.PADDING) {
            com.kasperstudios.kvr.spatial.KVRSpatial.openPowerWindow();
            return true;
        }
        int i = (int) ((mx - GlassStyle.PADDING) / (ICON + GAP));
        if (mx < GlassStyle.PADDING || i < 0) return false;
        List<SpatialConfig.DockItem> items = config.apps().dockItems();
        if (i < items.size()) {
            config.apps().launch(items.get(i).appId());
            return true;
        }
        List<SpatialWindow> min = windows.minimized();
        if (i - items.size() < min.size()) {
            windows.restore(min.get(i - items.size()));
            return true;
        }
        return false;
    }
}
