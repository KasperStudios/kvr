package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.apps.AppPermissions;
import com.kasperstudios.kvr.apps.StoreIndex;
import com.kasperstudios.kvr.apps.VrAppException;
import com.kasperstudios.kvr.apps.VrAppManifest;
import com.kasperstudios.kvr.apps.VrAppPackage;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.AppIcons;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * App Market — the App Manager of KVR OS (CONCEPT §2.3: one of the two programs in clean glasses; SPEC 5.3).
 * Three tabs:
 * <ul>
 *   <li><b>Installed</b> — OS apps and packages in these glasses: open, uninstall; disk usage of the Storage tier;</li>
 *   <li><b>Catalog</b> — the online KVR Store (GitHub index): install / update in one click after the
 *       permission dialog; the server's policy is shown when it forbids installs;</li>
 *   <li><b>Dev</b> — developer mode: run unpacked folders with hot reload, pack {@code .vrapp}, install.</li>
 * </ul>
 * A system window (no RAM slot), opened from the dock or by gesture 1.
 */
public final class AppMarketWindow extends SpatialWindow {

    private static final int W = 360;
    private static final int H = 260;
    private static final int TAB_Y = 24;
    private static final int LIST_Y = 44;
    private static final int LIST_BOTTOM = H - 28;
    private static final int ROW = 30;
    private static final int HEADER = 14;
    private static final int BTN_W = 58;
    private static final int BTN_H = 14;

    private enum Tab { INSTALLED, STORE, DEV }

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private interface Painter {
        void paint(GuiGraphics g, int y, int mx, int my);
    }

    private record Row(int height, Painter painter) {}

    /** The modal permission / uninstall dialog. */
    private record Confirm(Component title, List<Component> lines, Component okLabel, Runnable ok) {}

    private final List<Hit> hits = new ArrayList<>();
    /** Index in {@link #hits} where the dialog's buttons start: while it is open, nothing behind it is clickable. */
    private int modalHitStart = -1;
    private Tab tab = Tab.INSTALLED;
    private int scroll;
    private int maxScroll;
    @Nullable private Confirm confirm;
    private Component message = Component.empty();
    private int messageColor = GlassStyle.MUTED;
    private long messageUntil;

    public AppMarketWindow(SpatialAnchor anchor) {
        super("kvr:app_market", Kind.SYSTEM, W, H, anchor);
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.apps.market");
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        modalHitStart = -1;
        boolean modal = confirm != null;
        int hx = modal ? -1 : mx;
        int hy = modal ? -1 : my;
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 8, 8, GlassStyle.TEXT);

        int tx = 8;
        for (Tab t : Tab.values()) {
            Component label = Component.translatable("kvr.apps.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
            boolean on = tab == t;
            tab(g, hx, hy, tx, TAB_Y, 96, label, on, () -> {
                tab = t;
                scroll = 0;
                if (t == Tab.STORE && StoreClient.state() != StoreClient.State.READY) StoreClient.refresh();
                if (t == Tab.DEV && DevApps.enabled()) DevApps.rescan();
                if (t == Tab.INSTALLED) AppsClient.refresh();
            });
            tx += 100;
        }
        button(g, hx, hy, width - 30, TAB_Y, 22, Component.literal("\u27f3"), true, this::refreshTab);

        List<Row> rows = switch (tab) {
            case INSTALLED -> installedRows();
            case STORE -> storeRows();
            case DEV -> devRows();
        };
        paintRows(g, rows, hx, hy);
        paintStatus(g, hx, hy);
        if (modal) paintConfirm(g, mx, my);
    }

    private void paintRows(GuiGraphics g, List<Row> rows, int mx, int my) {
        maxScroll = Math.max(0, rows.size() - 1);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int y = LIST_Y;
        boolean more = false;
        for (int i = scroll; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (y + r.height() > LIST_BOTTOM) {
                more = true;
                break;
            }
            r.painter().paint(g, y, mx, my);
            y += r.height();
        }
        if (scroll > 0) GlassStyle.text(g, "\u25b2", width - 14, LIST_Y, GlassStyle.MUTED);
        if (more) GlassStyle.text(g, "\u25bc", width - 14, LIST_BOTTOM - 10, GlassStyle.MUTED);
    }

    // --- tab: installed ------------------------------------------------------------------------

    private List<Row> installedRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(header(Component.translatable("kvr.apps.section.system")));
        SpatialConfig cfg = KVRSpatial.config();
        for (SpatialConfig.DockItem item : cfg.apps().dockItems()) {
            if (AppCatalog.handles(item.appId())) continue;
            rows.add(new Row(ROW, (g, y, mx, my) -> {
                icon(g, item.appId(), 10, y + 6);
                GlassStyle.text(g, item.name(), 32, y + 4, GlassStyle.TEXT);
                GlassStyle.text(g, Component.translatable("kvr.apps.system_badge"), 32, y + 15, GlassStyle.MUTED);
                button(g, mx, my, width - 12 - BTN_W, y + 7, BTN_W, Component.translatable("kvr.apps.open"), true, () -> cfg.apps().launch(item.appId()));
            }));
        }

        List<AppsClient.Installed> list = AppsClient.installed();
        rows.add(header(Component.translatable("kvr.apps.section.installed", list.size())));
        if (!AppsClient.loaded()) {
            AppsClient.Failure f = AppsClient.lastError();
            rows.add(note(f == null ? Component.translatable("kvr.apps.loading") : errorText(f.code(), f.message()), f == null ? GlassStyle.MUTED : GlassStyle.DANGER));
        } else if (list.isEmpty()) {
            rows.add(note(Component.translatable("kvr.apps.none_installed"), GlassStyle.MUTED));
        }
        for (AppsClient.Installed app : list) {
            VrAppManifest m = app.manifest();
            rows.add(new Row(ROW, (g, y, mx, my) -> {
                icon(g, m.id(), 10, y + 6);
                fitted(g, m.name() + "  " + m.version(), 32, y + 4, width - 32 - 2 * (BTN_W + 4) - 14, GlassStyle.TEXT);
                fitted(g, sub(m, app.size()), 32, y + 15, width - 32 - 2 * (BTN_W + 4) - 14, GlassStyle.MUTED);
                int bx = width - 12 - BTN_W;
                button(g, mx, my, bx - BTN_W - 4, y + 7, BTN_W, Component.translatable("kvr.apps.open"), true, () -> AppCatalog.launchInstalled(app));
                button(g, mx, my, bx, y + 7, BTN_W, Component.translatable("kvr.apps.remove"), !AppsClient.busy(), () -> confirmRemove(app));
            }));
        }
        return rows;
    }

    private void confirmRemove(AppsClient.Installed app) {
        String id = app.manifest().id();
        confirm = new Confirm(
            Component.translatable("kvr.apps.confirm.remove", app.manifest().name()),
            List.of(Component.translatable("kvr.apps.confirm.remove_note", id)),
            Component.translatable("kvr.apps.remove"),
            () -> {
                KVRSpatial.windows().all().stream().filter(w -> w.id().equals("app:" + id)).findFirst().ifPresent(KVRSpatial.windows()::close);
                AppsClient.uninstall(id, f -> result(f, Component.translatable("kvr.apps.removed_ok", app.manifest().name())));
            });
    }

    // --- tab: catalog --------------------------------------------------------------------------

    private List<Row> storeRows() {
        List<Row> rows = new ArrayList<>();
        // 0027: built-in catalog — apps shipped with KVR, installed into these glasses on demand
        rows.add(header(Component.translatable("kvr.apps.section.builtin")));
        for (com.kasperstudios.kvr.apps.BundledApps.Entry b : com.kasperstudios.kvr.apps.BundledApps.entries()) {
            com.kasperstudios.kvr.apps.BundledApps.manifest(b.id()).ifPresent(m -> rows.add(bundledRow(m)));
        }
        rows.add(header(Component.translatable("kvr.apps.section.online")));
        AppsClient.Policy policy = AppsClient.policy();
        if (AppsClient.loaded() && !policy.allowInstall()) rows.add(note(Component.translatable("kvr.apps.server_denied"), GlassStyle.DANGER));
        switch (StoreClient.state()) {
            case OFF -> rows.add(note(Component.translatable("kvr.apps.store.off"), GlassStyle.MUTED));
            case IDLE, LOADING -> rows.add(note(Component.translatable("kvr.apps.store.loading"), GlassStyle.MUTED));
            case ERROR -> {
                rows.add(note(Component.translatable("kvr.apps.store.error", StoreClient.error()), GlassStyle.DANGER));
                rows.add(new Row(ROW, (g, y, mx, my) -> button(g, mx, my, 10, y + 6, BTN_W + 20, Component.translatable("kvr.apps.retry"), true, StoreClient::refresh)));
            }
            case READY -> {
                if (StoreClient.entries().isEmpty()) rows.add(note(Component.translatable("kvr.apps.store.empty"), GlassStyle.MUTED));
                for (StoreIndex.Entry e : StoreClient.entries()) rows.add(storeRow(e, policy));
            }
        }
        return rows;
    }

    private Row bundledRow(VrAppManifest m) {
        return new Row(ROW, (g, y, mx, my) -> {
            icon(g, m.id(), 10, y + 6);
            int textW = width - 32 - BTN_W - 18;
            fitted(g, m.name() + "  " + m.version(), 32, y + 4, textW, GlassStyle.TEXT);
            fitted(g, m.description().isEmpty() ? Component.translatable("kvr.apps.builtin_badge").getString() : m.description(), 32, y + 15, textW, GlassStyle.MUTED);
            var inst = AppsClient.installed(m.id());
            int bx = width - 12 - BTN_W;
            boolean update = inst.isPresent() && StoreIndex.compareVersions(m.version(), inst.get().manifest().version()) > 0;
            if (inst.isPresent() && !update) {
                GlassStyle.centered(g, Component.translatable("kvr.apps.installed_badge"), bx + BTN_W / 2, y + 10, GlassStyle.OK);
                return;
            }
            button(g, mx, my, bx, y + 7, BTN_W, Component.translatable(update ? "kvr.apps.update" : "kvr.apps.install"), !AppsClient.busy(), () -> {
                int size;
                try {
                    size = com.kasperstudios.kvr.apps.BundledApps.pack(m.id()).length; // only for the dialog; the server packs its own copy
                } catch (VrAppException e) {
                    say(errorText(e.code().name(), e.getMessage()), GlassStyle.DANGER);
                    return;
                }
                confirmInstall(m.name(), m.version(), m.author(), size, m.permissions(), m.requires(), () ->
                    AppsClient.installBundled(m.id(), m.name(), f -> result(f, Component.translatable("kvr.apps.installed_ok", m.name()))));
            });
        });
    }

    private Row storeRow(StoreIndex.Entry e, AppsClient.Policy policy) {
        return new Row(ROW, (g, y, mx, my) -> {
            icon(g, e.id(), 10, y + 6);
            int textW = width - 32 - BTN_W - 18;
            fitted(g, e.name() + "  " + e.version(), 32, y + 4, textW, GlassStyle.TEXT);
            String desc = e.description().isEmpty() ? e.author() : e.description();
            fitted(g, desc + (e.permissions().isEmpty() ? "" : "  \u26a0 " + String.join(", ", e.permissions())), 32, y + 15, textW, GlassStyle.MUTED);
            var inst = AppsClient.installed(e.id());
            boolean allowed = policy.allowInstall() && (policy.allowlist().isEmpty() || policy.allowlist().contains(e.id()));
            int bx = width - 12 - BTN_W;
            if (inst.isPresent() && inst.get().sha256().equals(e.sha256())) {
                GlassStyle.centered(g, Component.translatable("kvr.apps.installed_badge"), bx + BTN_W / 2, y + 10, GlassStyle.OK);
            } else if (!allowed) {
                GlassStyle.centered(g, Component.translatable("kvr.apps.denied_badge"), bx + BTN_W / 2, y + 10, GlassStyle.DANGER);
            } else {
                boolean update = inst.isPresent() && StoreIndex.compareVersions(e.version(), inst.get().manifest().version()) >= 0;
                Component label = Component.translatable(update ? "kvr.apps.update" : "kvr.apps.install");
                button(g, mx, my, bx, y + 7, BTN_W, label, !AppsClient.busy() && StoreClient.downloading().isEmpty(), () -> confirmInstall(
                    e.name(), e.version(), e.author(), e.size(), e.permissions(), e.requires(),
                    () -> {
                        say(Component.translatable("kvr.apps.downloading", e.name()), GlassStyle.MUTED);
                        StoreClient.download(e, bytes -> install(bytes, e.name()), err -> say(errorText("IO", err), GlassStyle.DANGER));
                    }));
            }
        });
    }

    // --- tab: dev ------------------------------------------------------------------------------

    private List<Row> devRows() {
        List<Row> rows = new ArrayList<>();
        if (!DevApps.enabled()) {
            rows.add(note(Component.translatable("kvr.apps.dev.off1"), GlassStyle.MUTED));
            rows.add(note(Component.translatable("kvr.apps.dev.off2"), GlassStyle.MUTED));
            return rows;
        }
        rows.add(note(Component.translatable("kvr.apps.dev.folder", DevApps.dir().toString()), GlassStyle.MUTED));
        if (DevApps.folders().isEmpty() && DevApps.broken().isEmpty() && DevApps.packed().isEmpty()) rows.add(note(Component.translatable("kvr.apps.dev.empty"), GlassStyle.MUTED));
        for (DevApps.Folder f : DevApps.folders()) {
            VrAppManifest m = f.manifest();
            rows.add(new Row(ROW, (g, y, mx, my) -> {
                icon(g, AppCatalog.DEV_PREFIX + m.id(), 10, y + 6);
                int textW = width - 32 - 3 * (BTN_W + 4) - 12;
                fitted(g, m.name() + "  " + m.version(), 32, y + 4, textW, GlassStyle.TEXT);
                fitted(g, m.id(), 32, y + 15, textW, GlassStyle.MUTED);
                int bx = width - 12 - BTN_W;
                button(g, mx, my, bx - 2 * (BTN_W + 4), y + 7, BTN_W, Component.translatable("kvr.apps.launch"), true, () -> AppCatalog.launchDev(m.id()));
                button(g, mx, my, bx - BTN_W - 4, y + 7, BTN_W, Component.translatable("kvr.apps.pack"), true, () -> {
                    try {
                        byte[] zip = DevApps.pack(m.id());
                        say(Component.translatable("kvr.apps.packed", m.id(), zip.length / 1024 + 1), GlassStyle.OK);
                    } catch (VrAppException e) {
                        say(errorText(e.code().name(), e.getMessage()), GlassStyle.DANGER);
                    }
                });
                button(g, mx, my, bx, y + 7, BTN_W, Component.translatable("kvr.apps.install"), !AppsClient.busy(), () -> {
                    try {
                        byte[] zip = DevApps.pack(m.id());
                        confirmInstall(m.name(), m.version(), m.author(), zip.length, m.permissions(), m.requires(), () -> install(zip, m.name()));
                    } catch (VrAppException e) {
                        say(errorText(e.code().name(), e.getMessage()), GlassStyle.DANGER);
                    }
                });
            }));
        }
        for (DevApps.Broken b : DevApps.broken()) {
            rows.add(new Row(ROW, (g, y, mx, my) -> {
                fitted(g, b.dir().getFileName().toString(), 32, y + 4, width - 44, GlassStyle.DANGER);
                fitted(g, b.error(), 32, y + 15, width - 44, GlassStyle.MUTED);
            }));
        }
        for (DevApps.PackedFile p : DevApps.packed()) {
            rows.add(new Row(ROW, (g, y, mx, my) -> {
                fitted(g, p.name(), 32, y + 10, width - 32 - BTN_W - 18, GlassStyle.TEXT);
                button(g, mx, my, width - 12 - BTN_W, y + 7, BTN_W, Component.translatable("kvr.apps.install"), !AppsClient.busy(), () -> {
                    try {
                        byte[] zip = DevApps.readPacked(p);
                        VrAppManifest m = VrAppPackage.read(zip).manifest();
                        confirmInstall(m.name(), m.version(), m.author(), zip.length, m.permissions(), m.requires(), () -> install(zip, m.name()));
                    } catch (VrAppException e) {
                        say(errorText(e.code().name(), e.getMessage()), GlassStyle.DANGER);
                    }
                });
            }));
        }
        return rows;
    }

    // --- install flow --------------------------------------------------------------------------

    private void confirmInstall(String name, String version, String author, int size, List<String> perms, VrAppManifest.Requirements req, Runnable go) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("kvr.apps.confirm.size", Math.max(1, size / 1024), author.isEmpty() ? "?" : author));
        if (perms.isEmpty()) {
            lines.add(Component.translatable("kvr.apps.perms.none"));
        } else {
            lines.add(Component.translatable("kvr.apps.perms"));
            for (String p : perms) lines.add(Component.literal(" \u2022 ").append(Component.translatable(AppPermissions.langKey(p))));
        }
        if (req.any()) lines.add(Component.translatable("kvr.apps.requires_line", req.cpu(), req.gpu(), req.ram(), req.storage()));
        confirm = new Confirm(Component.translatable("kvr.apps.confirm.install", name, version), lines, Component.translatable("kvr.apps.install"), go);
    }

    private void install(byte[] bytes, String name) {
        AppsClient.install(bytes, f -> result(f, Component.translatable("kvr.apps.installed_ok", name)));
    }

    private void result(@Nullable AppsClient.Failure f, Component success) {
        if (f == null) say(success, GlassStyle.OK);
        else say(errorText(f.code(), f.message()), GlassStyle.DANGER);
    }

    private static Component errorText(String code, String message) {
        String key = "kvr.apps.err." + code.toLowerCase(java.util.Locale.ROOT);
        if (Language.getInstance().has(key)) return Component.translatable(key);
        return Component.literal(message == null || message.isEmpty() ? code : message);
    }

    private void say(Component text, int color) {
        message = text;
        messageColor = color;
        messageUntil = System.currentTimeMillis() + 8000;
    }

    private void refreshTab() {
        switch (tab) {
            case INSTALLED -> AppsClient.refresh();
            case STORE -> StoreClient.refresh();
            case DEV -> DevApps.rescan();
        }
    }

    // --- status line and dialog ----------------------------------------------------------------

    private void paintStatus(GuiGraphics g, int mx, int my) {
        int y = H - 20;
        int maxW = width - 16 - GEAR - 8;
        g.fill(8, y - 5, width - 8, y - 4, 0x30FFFFFF);
        if (AppsClient.busy()) {
            float p = Math.max(0f, AppsClient.progress());
            fitted(g, Component.translatable("kvr.apps.installing", AppsClient.task(), Math.round(p * 100)).getString(), 8, y, maxW, GlassStyle.ACCENT);
            g.fill(8, y + 10, 8 + Math.round((maxW) * p), y + 12, GlassStyle.ACCENT);
        } else if (!StoreClient.downloading().isEmpty()) {
            fitted(g, Component.translatable("kvr.apps.downloading", StoreClient.downloading()).getString(), 8, y, maxW, GlassStyle.ACCENT);
        } else if (System.currentTimeMillis() < messageUntil) {
            fitted(g, message.getString(), 8, y, maxW, messageColor);
        } else if (AppsClient.loaded()) {
            AppsClient.Usage u = AppsClient.usage();
            Component usage = u.quotaFiles() < 0
                ? Component.translatable("kvr.apps.usage", kb(u.bytes()), kb(u.quotaBytes()))
                : Component.translatable("kvr.apps.usage_files", kb(u.bytes()), kb(u.quotaBytes()), u.files(), u.quotaFiles());
            fitted(g, usage.getString(), 8, y, maxW, GlassStyle.MUTED);
        }
    }

    private void paintConfirm(GuiGraphics g, int mx, int my) {
        Confirm c = confirm;
        if (c == null) return;
        // Полное затемнение фона и сплошная непрозрачная подложка модалки
        g.fill(0, 0, width, height, 0xCC000000);
        int w = 280;
        int h = 54 + c.lines().size() * 11;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        g.fill(x, y, x + w, y + h, 0xFF141923); // сплошной темный фон
        GlassStyle.surface(g, x, y, w, h, false);
        fitted(g, c.title().getString(), x + 8, y + 8, w - 16, GlassStyle.TEXT);
        int ly = y + 22;
        for (Component line : c.lines()) {
            fitted(g, line.getString(), x + 8, ly, w - 16, GlassStyle.MUTED);
            ly += 11;
        }
        int by = y + h - BTN_H - 8;
        modalHitStart = hits.size();
        button(g, mx, my, x + w - 8 - 2 * BTN_W - 6, by, BTN_W, Component.translatable("gui.cancel"), true, () -> confirm = null);
        button(g, mx, my, x + w - 8 - BTN_W, by, BTN_W, c.okLabel(), true, () -> {
            confirm = null;
            c.ok().run();
        });
    }

    // --- widgets -------------------------------------------------------------------------------

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean enabled, Runnable action) {
        boolean hot = enabled && GlassStyle.inside(mx, my, x, y, w, BTN_H);
        GlassStyle.panel(g, x, y, w, BTN_H, hot);
        GlassStyle.centered(g, label, x + w / 2, y + 3, enabled ? (hot ? GlassStyle.TEXT : GlassStyle.ACCENT) : 0x80B4C0CE);
        if (enabled) hits.add(new Hit(x, y, w, BTN_H, action));
    }

    private void tab(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean on, Runnable action) {
        boolean hot = GlassStyle.inside(mx, my, x, y, w, BTN_H);
        GlassStyle.panel(g, x, y, w, BTN_H, hot || on);
        if (on) g.fill(x + 4, y + BTN_H - 2, x + w - 4, y + BTN_H - 1, GlassStyle.ACCENT);
        GlassStyle.centered(g, label, x + w / 2, y + 3, on ? GlassStyle.TEXT : GlassStyle.MUTED);
        hits.add(new Hit(x, y, w, BTN_H, action));
    }

    private Row header(Component text) {
        return new Row(HEADER, (g, y, mx, my) -> {
            GlassStyle.text(g, text, 10, y + 3, GlassStyle.ACCENT);
            g.fill(10, y + HEADER - 2, width - 10, y + HEADER - 1, 0x30FFFFFF);
        });
    }

    private Row note(Component text, int color) {
        return new Row(HEADER + 2, (g, y, mx, my) -> fitted(g, text.getString(), 10, y + 4, width - 24, color));
    }

    private static void icon(GuiGraphics g, String key, int x, int y) {
        if (!AppIcons.draw(g, key, x, y)) GlassStyle.sprite(g, GlassStyle.id("gesture/launcher"), x, y, 16, 16);
    }

    private static void fitted(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        String s = text;
        if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026";
        GlassStyle.text(g, s, x, y, color);
    }

    private static String sub(VrAppManifest m, long size) {
        StringBuilder sb = new StringBuilder();
        if (!m.author().isEmpty()) sb.append(m.author()).append(" \u00b7 ");
        sb.append(kb(size)).append(" KB");
        if (!m.permissions().isEmpty()) sb.append(" \u00b7 \u26a0 ").append(String.join(", ", m.permissions()));
        return sb.toString();
    }

    private static long kb(long bytes) {
        return (bytes + 1023) / 1024;
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        // hits are rebuilt every frame; iterate from the last added so the dialog (painted last) wins
        int from = confirm != null && modalHitStart >= 0 ? modalHitStart : 0;
        for (int i = hits.size() - 1; i >= from; i--) {
            Hit h = hits.get(i);
            if (GlassStyle.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }
        if (confirm != null) return true; // click outside the dialog: swallowed, the dialog stays
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (confirm != null) return true;
        scroll = Math.max(0, Math.min(maxScroll, scroll + (amount > 0 ? -1 : 1)));
        return true;
    }
}
