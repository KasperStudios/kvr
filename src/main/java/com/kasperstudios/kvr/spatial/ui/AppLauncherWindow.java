package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.apps.client.AppCatalog;
import com.kasperstudios.kvr.apps.client.AppsClient;
import com.kasperstudios.kvr.apps.client.PackageIcons;
import com.kasperstudios.kvr.desktop.client.HostDesktopClient;
import com.kasperstudios.kvr.desktop.client.HostDesktopWindow;
import com.kasperstudios.kvr.script.BuiltinApps;
import com.kasperstudios.kvr.script.VrApp;
import com.kasperstudios.kvr.spatial.KVRSpatial;
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
 * Launcher of the glasses (ADR-001 gesture 1). System window, no RAM slot.
 *
 * <p>0031 — lists STRICTLY what these glasses can run:
 * <ol>
 *   <li>the three preinstalled programs (0027): App Market, Messenger ({@link BuiltinApps#PREINSTALLED}), Settings;</li>
 *   <li>"PC apps", only while they are switched on (an OS entry, same as in the dock);</li>
 *   <li>packages installed in the WORN glasses ({@link AppsClient#installed()} — the server's list for this stack).</li>
 * </ol>
 * Before 0031 it listed every JS app bundled in the mod jar ({@link BuiltinApps#SYSTEM_APPS}: hello, kav, notes, calc…)
 * whether installed or not, and clicking one started it from the jar. Apps that are not installed are now absent here;
 * the last card "Get more apps" opens App Market, where they are installed first. {@link BuiltinApps#launch} refuses
 * non-installed ids too (second line of defence for any other caller).
 */
public final class AppLauncherWindow extends SpatialWindow {

    private static final int W = 280;
    private static final int CARD_H = 26;
    private static final int GAP = 4;
    private static final int TOP = 30;
    private static final int MAX_ROWS = 10;
    private static final int TILE = 0xFF334155;
    private static final int STAR_W = 20;

    private enum Type { SYSTEM, DESKTOP, INSTALLED, MORE }

    public record Entry(String id, Component name, String detail, boolean running, Type type) {}

    private final BuiltinApps appHost;
    private final List<Entry> entries = new ArrayList<>();
    private int scroll;

    public AppLauncherWindow(BuiltinApps appHost, SpatialAnchor anchor) {
        super("kvr:app_launcher", Kind.SYSTEM, W, TOP + MAX_ROWS * (CARD_H + GAP) + 10, anchor);
        this.appHost = appHost;
        refresh();
    }

    private void refresh() {
        entries.clear();
        // 1. the three programs every pair of glasses has (0027)
        entries.add(new Entry(AppCatalog.MARKET_ID, Component.translatable("kvr.apps.market"), "KVR OS",
            KVRSpatial.windows().find(com.kasperstudios.kvr.apps.client.AppMarketWindow.class).isPresent(), Type.SYSTEM));
        for (String id : BuiltinApps.PREINSTALLED) {
            VrApp app = BuiltinApps.app(id).orElse(null);
            entries.add(new Entry(id, Component.literal(app == null ? id : app.name()), app == null ? "KVR OS" : "v" + app.version(),
                running(id), Type.SYSTEM));
        }
        entries.add(new Entry(SettingsWindow.DOCK_ID, Component.translatable("kvr.settings.app"), "KVR OS",
            KVRSpatial.windows().find(SettingsWindow.class).isPresent(), Type.SYSTEM));
        // 2. PC apps — an OS entry, only while enabled (exactly like the dock)
        if (HostDesktopClient.enabledLocally()) {
            boolean on = KVRSpatial.windows().all().stream().anyMatch(w -> w instanceof HostDesktopWindow);
            entries.add(new Entry(HostDesktopClient.DOCK_ID, Component.translatable("kvr.desktop.title"), "KVR OS", on, Type.DESKTOP));
        }
        // 3. what is installed in THESE glasses (empty until the server answered — never the jar's list)
        for (AppsClient.Installed inst : AppsClient.installed()) {
            String id = inst.manifest().id();
            if (entries.stream().anyMatch(e -> e.id().equals(id))) continue;
            entries.add(new Entry(id, Component.literal(inst.manifest().name()), "v" + inst.manifest().version(), running(id), Type.INSTALLED));
        }
        // 4. the way to everything else
        entries.add(new Entry(AppCatalog.MARKET_ID, Component.translatable("kvr.launcher.more"),
            Component.translatable("kvr.launcher.more_hint").getString(), false, Type.MORE));
        scroll = Math.max(0, Math.min(scroll, entries.size() - MAX_ROWS));
    }

    private static boolean running(String id) {
        return KVRSpatial.windows().all().stream().anyMatch(w -> w.id().equals("app:" + id));
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
        if (!AppsClient.loaded()) {
            // installed list not here yet: say so instead of showing nothing / the wrong thing
            fitted(g, Component.translatable("kvr.launcher.loading").getString(), width - 110, 9, 100, GlassStyle.MUTED);
        }
        g.fill(10, TOP - 4, width - 10, TOP - 3, 0x30FFFFFF);

        int end = Math.min(entries.size(), scroll + MAX_ROWS);
        for (int i = scroll; i < end; i++) {
            Entry e = entries.get(i);
            int y = rowY(i);
            boolean hot = GlassStyle.inside(mx, my, 10, y, width - 20, CARD_H);
            GlassStyle.panel(g, 10, y, width - 20, CARD_H, hot);
            icon(g, e, 14, y + 5);
            int textColor = e.type() == Type.MORE ? GlassStyle.ACCENT : GlassStyle.TEXT;
            fitted(g, e.name().getString(), 36, y + 4, width - 110, textColor);
            fitted(g, e.detail(), 36, y + 14, width - 110, GlassStyle.MUTED);
            if (e.type() != Type.MORE) {
                Component status = Component.translatable(e.running() ? "kvr.launcher.running" : "kvr.launcher.start");
                int sc = e.running() ? GlassStyle.OK : hot ? GlassStyle.TEXT : GlassStyle.MUTED;
                int right = e.type() == Type.INSTALLED ? width - 16 - STAR_W - 4 : width - 16;
                GlassStyle.text(g, status, right - Minecraft.getInstance().font.width(status), y + 9, sc);
            }
            if (e.type() == Type.INSTALLED) {
                // 0032: the ONLY way an installed app gets into the dock — never automatically
                boolean pinned = com.kasperstudios.kvr.config.DockPrefs.isPinned(e.id());
                int sx = width - 10 - STAR_W - 2;
                boolean hotStar = GlassStyle.inside(mx, my, sx, y + 3, STAR_W, CARD_H - 6);
                GlassStyle.panel(g, sx, y + 3, STAR_W, CARD_H - 6, hotStar);
                GlassStyle.centered(g, Component.literal(pinned ? "\u2605" : "\u2606"), sx + STAR_W / 2, y + 9,
                    pinned ? GlassStyle.ACCENT : hotStar ? GlassStyle.TEXT : GlassStyle.MUTED);
            }
        }
    }

    private static void icon(GuiGraphics g, Entry e, int x, int y) {
        if (e.type() == Type.INSTALLED && PackageIcons.draw(g, e.id(), x, y)) return;
        g.fill(x, y, x + 16, y + 16, e.running() ? GlassStyle.OK : e.type() == Type.MORE ? GlassStyle.ACCENT : TILE);
        String letter = e.type() == Type.MORE ? "+" : e.name().getString().isEmpty() ? "?"
            : e.name().getString().substring(0, 1).toUpperCase(Locale.ROOT);
        GlassStyle.centered(g, Component.literal(letter), x + 9, y + 4, GlassStyle.TEXT);
    }

    private int rowY(int index) {
        return TOP + (index - scroll) * (CARD_H + GAP);
    }

    private static void fitted(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        String s = text;
        if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026";
        GlassStyle.text(g, Component.literal(s), x, y, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int end = Math.min(entries.size(), scroll + MAX_ROWS);
        for (int i = scroll; i < end; i++) {
            if (!GlassStyle.inside(mx, my, 10, rowY(i), width - 20, CARD_H)) continue;
            Entry e = entries.get(i);
            if (e.type() == Type.INSTALLED && GlassStyle.inside(mx, my, width - 10 - STAR_W - 2, rowY(i) + 3, STAR_W, CARD_H - 6)) {
                com.kasperstudios.kvr.config.DockPrefs.togglePin(e.id()); // ★: dock pin, the launcher stays open
                return true;
            }
            if (e.type() == Type.MORE) AppCatalog.openMarket();
            else appHost.launch(e.id());
            requestClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (entries.size() <= MAX_ROWS || amount == 0) return false;
        scroll = Math.max(0, Math.min(entries.size() - MAX_ROWS, scroll - (int) Math.signum(amount)));
        return true;
    }
}
