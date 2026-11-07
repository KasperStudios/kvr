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
    /** Dock placement the current anchor was built for (0027: Settings → Display); 0 = not applied yet. */
    private int placement;

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
        com.kasperstudios.kvr.config.KvrUiConfig.Prefs p = com.kasperstudios.kvr.config.KvrUiConfig.get();
        int key = java.util.Objects.hash(p.dockHeightCm(), p.dockDistanceCm(), p.dockFollowDeg());
        if (key != placement) {
            placement = key;
            setAnchor(new LazyFollowAnchor(p.dockDistanceCm() / 100.0, p.dockHeightCm() / 100.0, p.dockFollowDeg()));
        }
        int slots = config.apps().dockItems().size() + windows.minimized().size();
        width = GlassStyle.PADDING * 2 + Math.max(1, slots) * (ICON + GAP) + statusWidth();
    }

    /** Clock and battery can be switched off in Settings; with both off the status block disappears. */
    private static int statusWidth() {
        com.kasperstudios.kvr.config.KvrUiConfig.Prefs p = com.kasperstudios.kvr.config.KvrUiConfig.get();
        return p.showClock() || p.showBattery() ? STATUS_W : 0;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        int x = GlassStyle.PADDING;
        int y = (height - ICON) / 2;

        for (SpatialConfig.DockItem item : config.apps().dockItems()) {
            boolean hot = GlassStyle.inside(mx, my, x, y, ICON, ICON);
            GlassStyle.panel(g, x, y, ICON, ICON, hot);
            if (!AppIcons.draw(g, item.appId(), x + 6, y + 6)) GlassStyle.sprite(g, item.icon(), x + 6, y + 6, 16, 16);
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

        if (statusWidth() == 0) return;
        com.kasperstudios.kvr.config.KvrUiConfig.Prefs prefs = com.kasperstudios.kvr.config.KvrUiConfig.get();
        int sx = width - STATUS_W - GlassStyle.PADDING + 4;
        g.fill(sx - 6, 10, sx - 5, height - 10, 0x40FFFFFF);
        boolean both = prefs.showClock() && prefs.showBattery();
        if (prefs.showBattery()) {
            int pct = config.batteryPercent().getAsInt();
            int by = both ? height / 2 - 8 : height / 2 - 4;
            GlassStyle.sprite(g, GlassStyle.BATTERY, sx, height / 2 - 8, 16, 16);
            int fill = Math.round(10 * pct / 100f);
            g.fill(sx + 2, height / 2 - 3, sx + 2 + fill, height / 2 + 3, pct <= 20 ? GlassStyle.DANGER : GlassStyle.OK);
            GlassStyle.text(g, Component.literal(pct + "%"), sx + 20, by, GlassStyle.TEXT);
        }
        if (prefs.showClock()) {
            int cy = both ? height / 2 + 2 : height / 2 - 4;
            GlassStyle.text(g, Component.literal(LocalTime.now().format(CLOCK)), prefs.showBattery() ? sx + 20 : sx, cy, both ? GlassStyle.MUTED : GlassStyle.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (statusWidth() > 0 && mx >= width - STATUS_W - GlassStyle.PADDING) {
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
