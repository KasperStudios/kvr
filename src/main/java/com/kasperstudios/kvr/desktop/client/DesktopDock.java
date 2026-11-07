package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.config.DockPrefs;
import com.kasperstudios.kvr.config.KvrUiConfig;
import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/**
 * PC apps the player pinned (★) in the launcher, as dock entries (0028, single source since 0030).
 *
 * <p>The one and only store is {@link DockPrefs} ({@code config/kvr/dock.json}): the launcher's ★ writes there and the
 * dock reads there, so a pin shows up in the dock on the next frame. A pin carries name, argv and icon, so the dock
 * shows and starts it before the {@code .desktop} scan ran. Shown only while PC apps are switched on
 * ({@code enable_host_desktop_windows}). Starting goes through {@link HostDesktopClient#launchApp} — local switch,
 * server policy, consent, binary, window limit — exactly like the launcher.
 */
public final class DesktopDock {

    private static final int MAX = 8;
    private static boolean migrated;

    private DesktopDock() {}

    public static boolean handles(String dockId) {
        return dockId.startsWith("desk:") || dockId.startsWith("deskcmd:");
    }

    public static List<SpatialConfig.DockItem> items() {
        if (!HostDesktopClient.enabledLocally()) return List.of();
        migrateLegacy();
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        for (String key : DockPrefs.pins()) {
            if (!handles(key) || out.size() >= MAX) continue;
            Optional<DockPrefs.DesktopPin> pin = DockPrefs.desktopPin(key);
            if (pin.isEmpty()) continue;
            DockPrefs.DesktopPin p = pin.get();
            if (!p.icon().isEmpty()) DesktopApps.requestIcon(key, Path.of(p.icon()));
            out.add(new SpatialConfig.DockItem(key, GlassStyle.id("gesture/launcher"), Component.literal(p.name()), false));
        }
        return out;
    }

    /** Dock click on a pinned PC app. */
    public static void launch(String key) {
        DockPrefs.desktopPin(key).ifPresent(p -> launch(key, p.argv()));
    }

    /** Launcher row or dock: history first (the launcher's "Recent" tab), then the checked launch. */
    public static void launch(String key, List<String> argv) {
        DockPrefs.recordLaunch(key);
        HostDesktopClient.launchApp(argv);
    }

    public static boolean isPinned(String key) {
        return DockPrefs.isPinned(key) && DockPrefs.desktopPin(key).isPresent();
    }

    /** ★ in the launcher. */
    public static void togglePin(DesktopApps.App app) {
        if (isPinned(app.key())) {
            DockPrefs.unpin(app.key());
        } else {
            DockPrefs.pinDesktop(new DockPrefs.DesktopPin(app.key(), app.name(), app.argv(), app.icon() == null ? "" : app.icon().toString()));
        }
    }

    /** The app behind a key: the scan if it has it, else what the pin remembers (dock works before the scan). */
    public static Optional<DesktopApps.App> resolve(String key) {
        Optional<DesktopApps.App> scanned = DesktopApps.find(key);
        if (scanned.isPresent()) return scanned;
        return DockPrefs.desktopPin(key).map(p -> new DesktopApps.App(p.key(), p.name(), "", p.argv(),
            p.icon().isEmpty() ? null : Path.of(p.icon()), p.key().startsWith("deskcmd:")));
    }

    /**
     * 0030: pins made with the pre-0030 launcher lived in {@code kvr-ui.toml} as plain command lines and never
     * reached the dock. Moved once into DockPrefs as {@code deskcmd:} pins (same key format as host_desktop_apps).
     */
    static void migrateLegacy() {
        if (migrated) return;
        migrated = true;
        for (String line : KvrUiConfig.takeLegacyPinnedDesktopApps()) {
            List<String> argv = DesktopDaemon.apps(line).stream().findFirst().orElse(List.of());
            if (argv.isEmpty()) continue;
            String key = "deskcmd:" + String.join(" ", argv);
            if (!DockPrefs.isPinned(key)) DockPrefs.pinDesktop(new DockPrefs.DesktopPin(key, String.join(" ", argv), argv, ""));
        }
    }
}
