package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.apps.client.PackageIcons;
import com.kasperstudios.kvr.config.DockPrefs;
import com.kasperstudios.kvr.script.ui.TextInputScreen;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * "Приложения ПК" (0028, rebuilt in 0030 on the single sources): the programs of this computer from
 * {@link DesktopApps} (freedesktop {@code .desktop} entries incl. Flatpak/Snap exports, localized {@code Name[ru]},
 * hicolor icons; custom {@code host_desktop_apps} commands first) and the pins/history of {@link DockPrefs}.
 *
 * <ul>
 *   <li>Tabs: <b>Recent</b> (launch history, newest first, up to 20) and <b>All apps</b> (alphabetical).
 *       Opens on Recent when there is history.</li>
 *   <li>Row: icon, name · description; click = start (through {@link HostDesktopClient#launchApp}: local switch, server
 *       policy, consent, binary, window limit); ★/☆ on the right = pin to the dock / unpin. The dock reads the same
 *       {@code dock.json}, so a pin appears there at once.</li>
 *   <li>Search strip: click it and type (keyboard capture via {@link TextInputScreen}, the list filters live by name,
 *       id and description); × clears. Wheel scrolls.</li>
 *   <li>Footer: daemon state and the number of programs found.</li>
 * </ul>
 * A system window (no RAM slot).
 */
public final class DesktopLauncherWindow extends SpatialWindow {

    private static final int W = 340;
    private static final int H = 286;
    private static final int TOP = 26;
    private static final int SEARCH_H = 16;
    private static final int LIST_Y = TOP + SEARCH_H + 6;
    private static final int ROW = 20;
    private static final int ICON = 16;
    private static final int STAR_W = 20;
    private static final int FOOT = 20;
    private static final int TAB_W = 74;
    private static final int RECENT_MAX = 20;
    private static final int SCROLL_STEP = 2;
    private static final int TILE = 0xFF334155;

    private enum Tab { RECENT, ALL }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private Tab tab;
    private String query = "";
    private boolean typing;
    private int scroll;
    private List<DesktopApps.App> rows = List.of();
    private boolean dirty = true;
    private DesktopApps.State seenState;
    private int seenCount = -1;

    public DesktopLauncherWindow(SpatialAnchor anchor) {
        super("kvr:desktop_launcher", Kind.SYSTEM, W, H, anchor);
        DesktopDock.migrateLegacy();
        DesktopApps.scanIfStale();
        tab = DockPrefs.recent("desk", 1).isEmpty() ? Tab.ALL : Tab.RECENT;
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.desktop.launcher");
    }

    @Override
    public void tick() {
        super.tick();
        DesktopApps.scanIfStale();
        DesktopApps.State st = DesktopApps.state();
        int n = DesktopApps.all().size();
        if (st != seenState || n != seenCount) {
            seenState = st;
            seenCount = n;
            dirty = true;
        }
    }

    // --- model ---------------------------------------------------------------------------------

    private void rebuild() {
        dirty = false;
        List<DesktopApps.App> source;
        if (tab == Tab.RECENT) {
            source = new ArrayList<>();
            for (String key : DockPrefs.recent("desk", RECENT_MAX)) DesktopDock.resolve(key).ifPresent(source::add);
        } else {
            source = DesktopApps.all();
        }
        String q = query.strip().toLowerCase(Locale.ROOT);
        rows = q.isEmpty() ? List.copyOf(source) : source.stream().filter(a -> matches(a, q)).toList();
        clampScroll();
    }

    private static boolean matches(DesktopApps.App a, String q) {
        return a.name().toLowerCase(Locale.ROOT).contains(q)
            || a.key().toLowerCase(Locale.ROOT).contains(q)
            || a.comment().toLowerCase(Locale.ROOT).contains(q);
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_Y - FOOT) / ROW);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private void setTab(Tab t) {
        if (tab == t) return;
        tab = t;
        scroll = 0;
        dirty = true;
    }

    private void setQuery(String q) {
        query = q;
        scroll = 0;
        dirty = true;
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        if (dirty) rebuild();
        hits.clear();
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 8, 9, GlassStyle.TEXT);
        tab(g, mx, my, width - 8 - 2 * TAB_W - 4, Tab.RECENT, "kvr.desktop.tab.recent");
        tab(g, mx, my, width - 8 - TAB_W, Tab.ALL, "kvr.desktop.tab.all");
        search(g, mx, my);
        list(g, mx, my);
        footer(g);
    }

    private void tab(GuiGraphics g, int mx, int my, int x, Tab t, String key) {
        boolean on = tab == t;
        boolean hot = GlassStyle.inside(mx, my, x, 5, TAB_W, 16);
        GlassStyle.panel(g, x, 5, TAB_W, 16, hot || on);
        if (on) g.fill(x + 4, 19, x + TAB_W - 4, 20, GlassStyle.ACCENT);
        GlassStyle.centered(g, Component.translatable(key), x + TAB_W / 2, 9, on ? GlassStyle.TEXT : GlassStyle.MUTED);
        hits.add(new Hit(x, 5, TAB_W, 16, () -> setTab(t)));
    }

    private void search(GuiGraphics g, int mx, int my) {
        int x = 8;
        int w = width - 16;
        boolean hot = GlassStyle.inside(mx, my, x, TOP, w, SEARCH_H);
        GlassStyle.panel(g, x, TOP, w, SEARCH_H, hot || typing);
        boolean empty = query.isEmpty();
        String shown = empty && !typing ? Component.translatable("kvr.desktop.search").getString() : query + (typing ? "_" : "");
        fitted(g, shown, x + 6, TOP + 4, w - 30, empty && !typing ? GlassStyle.MUTED : GlassStyle.TEXT);
        hits.add(new Hit(x, TOP, w, SEARCH_H, this::startTyping));
        if (!empty) {
            int cx = x + w - 16;
            boolean hotX = GlassStyle.inside(mx, my, cx, TOP, 16, SEARCH_H);
            GlassStyle.centered(g, Component.literal("\u00d7"), cx + 8, TOP + 4, hotX ? GlassStyle.TEXT : GlassStyle.MUTED);
            hits.add(new Hit(cx, TOP, 16, SEARCH_H, () -> setQuery("")));
        }
    }

    private void list(GuiGraphics g, int mx, int my) {
        if (rows.isEmpty()) {
            String key;
            if (DesktopApps.state() != DesktopApps.State.READY && tab == Tab.ALL) key = "kvr.desktop.scanning";
            else if (!query.isBlank()) key = "kvr.desktop.empty_search";
            else if (tab == Tab.RECENT) key = "kvr.desktop.empty_recent";
            else key = "kvr.desktop.empty_all";
            fitted(g, Component.translatable(key).getString(), 12, LIST_Y + 6, width - 24, GlassStyle.MUTED);
            return;
        }
        int visible = visibleRows();
        int end = Math.min(rows.size(), scroll + visible);
        int rowW = width - 16 - STAR_W - 2;
        int starX = width - 8 - STAR_W;
        for (int i = scroll; i < end; i++) {
            DesktopApps.App app = rows.get(i);
            int y = LIST_Y + (i - scroll) * ROW;
            boolean hot = GlassStyle.inside(mx, my, 8, y, rowW, ROW - 2);
            GlassStyle.panel(g, 8, y, rowW, ROW - 2, hot);
            icon(g, app, 11, y + 1);
            String text = app.comment().isEmpty() ? app.name() : app.name() + " \u00b7 " + app.comment();
            fitted(g, text, 11 + ICON + 6, y + 5, rowW - ICON - 14, hot ? GlassStyle.TEXT : GlassStyle.MUTED);
            hits.add(new Hit(8, y, rowW, ROW - 2, () -> launch(app)));

            boolean pinned = DesktopDock.isPinned(app.key());
            boolean hotStar = GlassStyle.inside(mx, my, starX, y, STAR_W, ROW - 2);
            GlassStyle.panel(g, starX, y, STAR_W, ROW - 2, hotStar);
            GlassStyle.centered(g, Component.literal(pinned ? "\u2605" : "\u2606"), starX + STAR_W / 2, y + 5,
                pinned ? GlassStyle.ACCENT : hotStar ? GlassStyle.TEXT : GlassStyle.MUTED);
            hits.add(new Hit(starX, y, STAR_W, ROW - 2, () -> DesktopDock.togglePin(app)));
        }
        if (rows.size() > visible) {
            // scroll position bar along the right edge of the list
            int listH = visible * ROW - 2;
            int barH = Math.max(8, listH * visible / rows.size());
            int barY = LIST_Y + (listH - barH) * scroll / Math.max(1, rows.size() - visible);
            g.fill(width - 5, barY, width - 3, barY + barH, 0x80FFFFFF);
        }
    }

    private static void icon(GuiGraphics g, DesktopApps.App app, int x, int y) {
        DesktopApps.requestIcon(app.key(), app.icon()); // lazy: only rows that are on screen
        if (PackageIcons.draw(g, app.key(), x, y)) return;
        g.fill(x, y, x + ICON, y + ICON, TILE);
        String letter = app.name().isEmpty() ? "?" : app.name().substring(0, 1).toUpperCase(Locale.ROOT);
        GlassStyle.centered(g, Component.literal(letter), x + ICON / 2 + 1, y + 4, GlassStyle.TEXT);
    }

    private void footer(GuiGraphics g) {
        int y = height - 14;
        String daemon = Component.translatable("kvr.desktop.daemon." + DesktopDaemon.state().name().toLowerCase(Locale.ROOT)).getString();
        String count = DesktopApps.state() == DesktopApps.State.READY
            ? Component.translatable("kvr.desktop.count", DesktopApps.all().size()).getString()
            : Component.translatable("kvr.desktop.scanning").getString();
        fitted(g, daemon + "  \u00b7  " + count, 8, y, width - 8 - GEAR - 10, GlassStyle.MUTED);
    }

    private static void fitted(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        String s = text;
        if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026";
        GlassStyle.text(g, Component.literal(s), x, y, color);
    }

    // --- actions -------------------------------------------------------------------------------

    private void launch(DesktopApps.App app) {
        DesktopDock.launch(app.key(), app.argv());
        if (tab == Tab.RECENT) dirty = true;
        requestClose();
    }

    private void startTyping() {
        typing = true;
        Minecraft.getInstance().setScreen(new TextInputScreen(new TextInputScreen.Target() {
            @Override public String value() { return query; }
            @Override public void set(String value) { setQuery(value); }
            @Override public void submit() { typing = false; }
            @Override public int maxLength() { return 64; }
            @Override public boolean multiline() { return false; }
        }));
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (GlassStyle.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0) return false;
        scroll -= (int) Math.signum(amount) * SCROLL_STEP;
        clampScroll();
        return true;
    }
}
