package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.apps.client.AppsClient;
import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.config.KvrSettingsScreen;
import com.kasperstudios.kvr.config.KvrUiConfig;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.PowerNbt;
import com.kasperstudios.kvr.power.client.PowerClient;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Settings — one of the three preinstalled programs (0027, Creator: Market, Messenger, Settings), laid out like
 * the settings of real smart glasses: a section list on the left, the controls on the right.
 * <ul>
 *   <li><b>Personalisation</b> — theme accent (5 themes), window opacity, backdrop (glass / gradient / opaque), live preview;</li>
 *   <li><b>Display &amp; dock</b> — dock height, distance and follow angle, Public/Private for new windows,
 *       other players' public windows, clock and battery in the dock;</li>
 *   <li><b>Sound &amp; haptics</b> — click, notification and app sounds, window shake;</li>
 *   <li><b>System</b> — power window, the mod settings screen (PC apps), about: device, serial, KVR OS version,
 *       hardware tiers, battery, RAM windows, storage.</li>
 * </ul>
 * Everything applies at once: {@link KvrUiConfig} ({@code kvr-ui.toml}) and {@link KvrConfig} ({@code kvr-client.toml}).
 * A system window (no RAM slot).
 */
public final class SettingsWindow extends SpatialWindow {

    public static final String DOCK_ID = "settings";
    /** SPEC header: KVR 1.0.0-PROD. */
    public static final String KVR_OS_VERSION = "1.0.0";

    private static final int W = 384;
    private static final int H = 272;
    private static final int SIDE_W = 96;
    private static final int TOP = 28;
    private static final int ROW = 22;
    private static final int LINE = 12;
    private static final int BTN_H = 14;

    private enum Section { PERSONAL, DISPLAY, SOUND, SYSTEM }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private Section section = Section.PERSONAL;

    public SettingsWindow(SpatialAnchor anchor) {
        super("kvr:settings", Kind.SYSTEM, W, H, anchor);
    }

    /** Dock / launcher entry: restore the open window or spawn one in front of the player. */
    public static void open() {
        var wm = KVRSpatial.windows();
        Optional<SettingsWindow> open = wm.find(SettingsWindow.class);
        if (open.isPresent()) {
            wm.restore(open.get());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(0.9));
        wm.open(new SettingsWindow(new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.settings.app");
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 8, 8, GlassStyle.TEXT);

        Section[] all = Section.values();
        for (int i = 0; i < all.length; i++) {
            Section s = all[i];
            int y = TOP + i * 22;
            boolean on = s == section;
            boolean hot = GlassStyle.inside(mx, my, 8, y, SIDE_W, 18);
            GlassStyle.panel(g, 8, y, SIDE_W, 18, hot || on);
            if (on) g.fill(8, y + 3, 10, y + 15, GlassStyle.ACCENT);
            fitted(g, Component.translatable("kvr.settings.section." + s.name().toLowerCase(Locale.ROOT)).getString(), 14, y + 5, SIDE_W - 10, on ? GlassStyle.TEXT : GlassStyle.MUTED);
            hits.add(new Hit(8, y, SIDE_W, 18, () -> section = s));
        }
        g.fill(SIDE_W + 14, TOP, SIDE_W + 15, height - 18, 0x30FFFFFF);

        int x = SIDE_W + 22;
        int cw = width - x - 10;
        switch (section) {
            case PERSONAL -> personal(g, mx, my, x, cw);
            case DISPLAY -> display(g, mx, my, x, cw);
            case SOUND -> sound(g, mx, my, x, cw);
            case SYSTEM -> system(g, mx, my, x, cw);
        }
    }

    private void personal(GuiGraphics g, int mx, int my, int x, int cw) {
        KvrUiConfig.Prefs p = KvrUiConfig.get();
        int y = TOP;
        label(g, Component.translatable("kvr.settings.theme"), x, y);
        KvrUiConfig.Theme[] themes = KvrUiConfig.Theme.values();
        for (int i = 0; i < themes.length; i++) {
            KvrUiConfig.Theme t = themes[i];
            int sx = x + cw - themes.length * 22 + i * 22;
            if (t == p.theme()) g.fill(sx - 2, y + 1, sx + 18, y + 21, GlassStyle.TEXT);
            g.fill(sx - 1, y + 2, sx + 17, y + 20, 0xFF0F172A);
            g.fill(sx, y + 3, sx + 16, y + 19, t.accent);
            hits.add(new Hit(sx - 2, y + 1, 20, 20, () -> KvrUiConfig.update("theme", t)));
        }
        y += ROW;
        GlassStyle.text(g, Component.translatable(p.theme().langKey()), x + 8, y + 2, GlassStyle.ACCENT);
        y += LINE + 4;

        int opacity = KvrConfig.client().windowOpacity();
        stepper(g, mx, my, x, y, cw, Component.translatable("kvr.settings.opacity"), opacity + "%",
            () -> KvrConfig.updateClient("window_opacity", Math.max(0, opacity - 5)),
            () -> KvrConfig.updateClient("window_opacity", Math.min(100, opacity + 5)));
        y += ROW;

        KvrUiConfig.Backdrop[] styles = KvrUiConfig.Backdrop.values();
        List<Component> names = new ArrayList<>();
        for (KvrUiConfig.Backdrop b : styles) names.add(Component.translatable(b.langKey()));
        choice(g, mx, my, x, y, cw, Component.translatable("kvr.settings.backdrop"), names, p.backdrop().ordinal(),
            i -> KvrUiConfig.update("backdrop", styles[i]));
        y += ROW + 8;

        // live preview of a window in the current style
        label(g, Component.translatable("kvr.settings.preview"), x, y);
        y += LINE + 2;
        int pw = cw;
        int ph = 46;
        GlassStyle.surface(g, x, y, pw, ph, false);
        GlassStyle.text(g, Component.translatable("kvr.settings.preview.title").withStyle(ChatFormatting.BOLD), x + 8, y + 7, GlassStyle.TEXT);
        GlassStyle.text(g, Component.translatable("kvr.settings.preview.text"), x + 8, y + 19, GlassStyle.MUTED);
        GlassStyle.panel(g, x + 8, y + 30, 70, BTN_H, false);
        GlassStyle.centered(g, Component.translatable("kvr.settings.preview.button"), x + 43, y + 33, GlassStyle.ACCENT);
    }

    private void display(GuiGraphics g, int mx, int my, int x, int cw) {
        KvrUiConfig.Prefs p = KvrUiConfig.get();
        KvrConfig.Client c = KvrConfig.client();
        int y = TOP;
        // 0028: fine fit of the dock (±1 / ±10), the dock moves live while you click
        fine(g, mx, my, x, y, cw, Component.translatable("kvr.settings.dock_height"), p.dockHeightCm() + " cm",
            d -> KvrUiConfig.update("dock_height_cm", clamp(p.dockHeightCm() + d, KvrUiConfig.DOCK_HEIGHT_MIN, KvrUiConfig.DOCK_HEIGHT_MAX)));
        y += ROW;
        fine(g, mx, my, x, y, cw, Component.translatable("kvr.settings.dock_distance"), p.dockDistanceCm() + " cm",
            d -> KvrUiConfig.update("dock_distance_cm", clamp(p.dockDistanceCm() + d, KvrUiConfig.DOCK_DISTANCE_MIN, KvrUiConfig.DOCK_DISTANCE_MAX)));
        y += ROW;
        fine(g, mx, my, x, y, cw, Component.translatable("kvr.settings.dock_follow"), p.dockFollowDeg() + "\u00b0",
            d -> KvrUiConfig.update("dock_follow_deg", clamp(p.dockFollowDeg() + d, KvrUiConfig.DOCK_FOLLOW_MIN, KvrUiConfig.DOCK_FOLLOW_MAX)));
        y += ROW;
        int rw = 90;
        boolean hotReset = GlassStyle.inside(mx, my, x + cw - rw, y + 2, rw, BTN_H);
        GlassStyle.panel(g, x + cw - rw, y + 2, rw, BTN_H, hotReset);
        GlassStyle.centered(g, Component.translatable("kvr.settings.dock_reset"), x + cw - rw / 2, y + 5, hotReset ? GlassStyle.TEXT : GlassStyle.MUTED);
        hits.add(new Hit(x + cw - rw, y + 2, rw, BTN_H, () -> {
            KvrUiConfig.update("dock_height_cm", -50);
            KvrUiConfig.update("dock_distance_cm", 85);
            KvrUiConfig.update("dock_follow_deg", 40);
        }));
        y += ROW;
        choice(g, mx, my, x, y, cw, Component.translatable("kvr.settings.new_windows"),
            List.of(Component.translatable("kvr.window.menu.public"), Component.translatable("kvr.window.menu.private")),
            c.defaultWindowPublic() ? 0 : 1, i -> KvrConfig.updateClient("default_window_public", i == 0));
        y += ROW;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.show_public"), c.showPublicWindows(),
            v -> KvrConfig.updateClient("show_public_windows", v));
        y += ROW + 6;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.dock_clock"), p.showClock(), v -> KvrUiConfig.update("dock_show_clock", v));
        y += ROW;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.dock_battery"), p.showBattery(), v -> KvrUiConfig.update("dock_show_battery", v));
    }

    private void sound(GuiGraphics g, int mx, int my, int x, int cw) {
        KvrUiConfig.Prefs p = KvrUiConfig.get();
        int y = TOP;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.sound_clicks"), p.soundClicks(), v -> KvrUiConfig.update("sound_clicks", v));
        y += ROW;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.sound_toasts"), p.soundToasts(), v -> KvrUiConfig.update("sound_toasts", v));
        y += ROW;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.sound_events"), p.soundEvents(), v -> KvrUiConfig.update("sound_app_events", v));
        y += ROW + 6;
        toggle(g, mx, my, x, y, cw, Component.translatable("kvr.settings.shake"), p.windowShake(), v -> KvrUiConfig.update("window_shake", v));
        y += ROW;
        fitted(g, Component.translatable("kvr.settings.shake_note").getString(), x, y + 2, cw, GlassStyle.MUTED);
    }

    private void system(GuiGraphics g, int mx, int my, int x, int cw) {
        int y = TOP;
        action(g, mx, my, x, y, cw, Component.translatable("kvr.settings.power"), KVRSpatial::openPowerWindow);
        y += ROW;
        action(g, mx, my, x, y, cw, Component.translatable("kvr.settings.advanced"),
            () -> Minecraft.getInstance().setScreen(new KvrSettingsScreen(null)));
        y += ROW;
        action(g, mx, my, x, y, cw, Component.translatable("kvr.settings.calls"), com.kasperstudios.kvr.voice.client.CallWindow::openDialer);
        y += ROW + 4;

        GlassStyle.text(g, Component.translatable("kvr.settings.about").withStyle(ChatFormatting.BOLD), x, y, GlassStyle.ACCENT);
        y += LINE + 2;
        ItemStack worn = PowerClient.worn();
        info(g, x, y, cw, "kvr.settings.device", worn.isEmpty() ? "\u2014" : worn.getHoverName().getString());
        y += LINE;
        info(g, x, y, cw, "kvr.settings.serial", serial(worn));
        y += LINE;
        info(g, x, y, cw, "kvr.settings.os", "KVR OS " + KVR_OS_VERSION);
        y += LINE;
        info(g, x, y, cw, "kvr.settings.rednet", rednet());
        y += LINE;
        Optional<PowerBuild> b = GlassesPower.build(worn);
        if (b.isPresent()) {
            PowerBuild h = b.get();
            info(g, x, y, cw, "kvr.settings.hw.cpu", tier(h.cpu()));
            y += LINE;
            info(g, x, y, cw, "kvr.settings.hw.gpu", tier(h.gpu()));
            y += LINE;
            info(g, x, y, cw, "kvr.settings.hw.ram", tier(h.ram()) + " \u00b7 " + KVRSpatial.windows().appsOpen() + "/" + KVRSpatial.windows().ramLimit());
            y += LINE;
            info(g, x, y, cw, "kvr.settings.hw.storage", tier(h.storage()) + " \u00b7 " + storage());
            y += LINE;
            info(g, x, y, cw, "kvr.settings.hw.battery", tier(h.battery()) + " \u00b7 " + GlassesPower.percent(worn) + "%");
        } else {
            fitted(g, Component.translatable("kvr.settings.hw.none").getString(), x, y, cw, GlassStyle.MUTED);
        }
    }

    /** 0029: CC modem in the expansion slot — "#51234 · Ender", "no modem", "no CC: Tweaked". */
    private static String rednet() {
        var s = com.kasperstudios.kvr.rednet.client.RednetClient.state();
        if (!s.available()) return Component.translatable("kvr.settings.rednet.no_cc").getString();
        if (!s.modem()) {
            String none = Component.translatable("kvr.settings.rednet.no_modem").getString();
            return s.id() >= 0 ? none + " (ID " + s.id() + ")" : none;
        }
        return "ID " + s.id() + " \u00b7 " + Component.translatable(s.ender() ? "kvr.settings.rednet.ender" : "kvr.settings.rednet.wireless").getString();
    }

    private static String serial(ItemStack worn) {
        if (worn.isEmpty()) return "\u2014";
        String id = PowerNbt.getString(PowerNbt.readKey(worn, "kvr_vfs"), "id", "");
        // assigned by the server on the first file access (VfsServer); the UUID's head is unique enough to read out
        return id.length() >= 8 ? "KVR-" + id.substring(0, 8).toUpperCase(Locale.ROOT) : Component.translatable("kvr.settings.serial_none").getString();
    }

    private static String tier(int t) {
        return "T" + t + " " + Component.translatable("kvr.settings.tier." + Math.max(1, Math.min(5, t))).getString();
    }

    private static String storage() {
        if (!AppsClient.loaded()) return "\u2026";
        AppsClient.Usage u = AppsClient.usage();
        return (u.bytes() + 1023) / 1024 + "/" + (u.quotaBytes() + 1023) / 1024 + " KB";
    }

    // --- widgets -------------------------------------------------------------------------------

    private static void label(GuiGraphics g, Component text, int x, int y) {
        GlassStyle.text(g, text, x, y + 5, GlassStyle.TEXT);
    }

    private void toggle(GuiGraphics g, int mx, int my, int x, int y, int cw, Component text, boolean on, Consumer<Boolean> set) {
        label(g, text, x, y);
        int bx = x + cw - 48;
        boolean hot = GlassStyle.inside(mx, my, bx, y + 3, 48, BTN_H);
        GlassStyle.panel(g, bx, y + 3, 48, BTN_H, hot);
        if (on) g.fill(bx + 3, y + 5, bx + 6, y + 15, GlassStyle.OK);
        GlassStyle.centered(g, Component.translatable(on ? "options.on" : "options.off"), bx + 26, y + 6, on ? GlassStyle.OK : GlassStyle.MUTED);
        hits.add(new Hit(bx, y + 3, 48, BTN_H, () -> set.accept(!on)));
    }

    private void stepper(GuiGraphics g, int mx, int my, int x, int y, int cw, Component text, String value, Runnable dec, Runnable inc) {
        label(g, text, x, y);
        int right = x + cw;
        small(g, mx, my, right - 88, y + 3, "\u2212", dec);
        GlassStyle.centered(g, Component.literal(value), right - 44, y + 6, GlassStyle.ACCENT);
        small(g, mx, my, right - 16, y + 3, "+", inc);
    }

    /** Label, then « ‹ value › » = −10, −1, +1, +10. */
    private void fine(GuiGraphics g, int mx, int my, int x, int y, int cw, Component text, String value, IntConsumer delta) {
        label(g, text, x, y);
        int right = x + cw;
        small(g, mx, my, right - 118, y + 3, "\u00ab", () -> delta.accept(-10));
        small(g, mx, my, right - 100, y + 3, "\u2039", () -> delta.accept(-1));
        GlassStyle.centered(g, Component.literal(value), right - 59, y + 6, GlassStyle.ACCENT);
        small(g, mx, my, right - 34, y + 3, "\u203a", () -> delta.accept(1));
        small(g, mx, my, right - 16, y + 3, "\u00bb", () -> delta.accept(10));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void small(GuiGraphics g, int mx, int my, int x, int y, String sign, Runnable action) {
        boolean hot = GlassStyle.inside(mx, my, x, y, 16, BTN_H);
        GlassStyle.panel(g, x, y, 16, BTN_H, hot);
        GlassStyle.centered(g, Component.literal(sign), x + 8, y + 3, hot ? GlassStyle.TEXT : GlassStyle.MUTED);
        hits.add(new Hit(x, y, 16, BTN_H, action));
    }

    private void choice(GuiGraphics g, int mx, int my, int x, int y, int cw, Component text, List<Component> options, int selected, IntConsumer pick) {
        label(g, text, x, y);
        int segW = 54;
        int sx = x + cw - options.size() * segW;
        for (int i = 0; i < options.size(); i++) {
            int bx = sx + i * segW;
            boolean on = i == selected;
            boolean hot = GlassStyle.inside(mx, my, bx, y + 3, segW - 2, BTN_H);
            GlassStyle.panel(g, bx, y + 3, segW - 2, BTN_H, hot || on);
            if (on) g.fill(bx + 3, y + BTN_H + 1, bx + segW - 5, y + BTN_H + 2, GlassStyle.ACCENT);
            GlassStyle.centered(g, options.get(i), bx + (segW - 2) / 2, y + 6, on ? GlassStyle.TEXT : GlassStyle.MUTED);
            int index = i;
            hits.add(new Hit(bx, y + 3, segW - 2, BTN_H, () -> pick.accept(index)));
        }
    }

    private void action(GuiGraphics g, int mx, int my, int x, int y, int cw, Component text, Runnable run) {
        boolean hot = GlassStyle.inside(mx, my, x, y + 2, cw, BTN_H + 2);
        GlassStyle.panel(g, x, y + 2, cw, BTN_H + 2, hot);
        GlassStyle.text(g, text, x + 8, y + 6, hot ? GlassStyle.TEXT : GlassStyle.ACCENT);
        GlassStyle.text(g, "\u203a", x + cw - 10, y + 6, GlassStyle.MUTED);
        hits.add(new Hit(x, y + 2, cw, BTN_H + 2, run));
    }

    private static void info(GuiGraphics g, int x, int y, int cw, String key, String value) {
        Font font = Minecraft.getInstance().font;
        String k = Component.translatable(key).getString();
        GlassStyle.text(g, k, x, y, GlassStyle.MUTED);
        int kw = font.width(k) + 6;
        fitted(g, value, x + kw, y, cw - kw, GlassStyle.TEXT);
    }

    private static void fitted(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        String s = text;
        if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026";
        GlassStyle.text(g, s, x, y, color);
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
}
