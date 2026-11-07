package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Personalisation of KVR OS (0027, Settings app): theme accent, window backdrop style, dock placement,
 * dock status block, sounds, window shake. Client-only, {@code config/kvr-ui.toml}, same flat TOML subset and
 * parser as {@link KvrConfig}. Window opacity and default Public/Private stay where they already were
 * ({@code kvr-client.toml}, {@link KvrConfig}) so the mod settings screen and the in-visor app share them.
 *
 * <p>Read every frame by the UI ({@link #get()} returns a cached record; {@link #update} rewrites one key and
 * drops the cache).
 */
public final class KvrUiConfig {

    /** Accent colours (ARGB) of the five themes. */
    public enum Theme {
        CYAN(0xFF67E8F9), PURPLE(0xFFC084FC), AMBER(0xFFFBBF24), EMERALD(0xFF4ADE80), MONO(0xFFE5E7EB);

        public final int accent;

        Theme(int accent) {
            this.accent = accent;
        }

        public String langKey() {
            return "kvr.settings.theme." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Window background: glass sprite over a dark backdrop, an accent-tinted gradient, or fully opaque. */
    public enum Backdrop {
        GLASS, GRADIENT, OPAQUE;

        public String langKey() {
            return "kvr.settings.backdrop." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int DOCK_HEIGHT_MIN = -80;
    public static final int DOCK_HEIGHT_MAX = -20;
    public static final int DOCK_DISTANCE_MIN = 60;
    public static final int DOCK_DISTANCE_MAX = 140;
    public static final int DOCK_FOLLOW_MIN = 20;
    public static final int DOCK_FOLLOW_MAX = 90;

    /**
     * @param dockHeightCm   dock below eye height, cm (negative = below)
     * @param dockDistanceCm dock distance in front of the eyes, cm
     * @param dockFollowDeg  how far the head may turn before the dock follows
     */
    public record Prefs(Theme theme, Backdrop backdrop, int dockHeightCm, int dockDistanceCm, int dockFollowDeg,
                        boolean showClock, boolean showBattery,
                        boolean soundClicks, boolean soundToasts, boolean soundEvents, boolean windowShake) {}

    private static final String FILE = "kvr-ui.toml";
    private static final String DEFAULTS = """
        # KVR Spatial OS - personalisation (in-visor Settings app writes this file)
        # cyan | purple | amber | emerald | mono
        theme = "cyan"
        # glass | gradient | opaque
        backdrop = "glass"
        # Dock: height relative to the eyes and distance in cm, follow angle in degrees
        dock_height_cm = -50
        dock_distance_cm = 85
        dock_follow_deg = 40
        dock_show_clock = true
        dock_show_battery = true
        sound_clicks = true
        sound_toasts = true
        sound_app_events = true
        window_shake = true
        """;

    private static Path dir = Path.of("config");
    private static Prefs prefs;

    private KvrUiConfig() {}

    public static synchronized void setDirectory(Path directory) {
        dir = directory;
        prefs = null;
    }

    public static synchronized Prefs get() {
        if (prefs == null) {
            Map<String, String> m = load();
            prefs = new Prefs(
                enumOf(Theme.class, KvrConfig.str(m, "theme"), Theme.CYAN),
                enumOf(Backdrop.class, KvrConfig.str(m, "backdrop"), Backdrop.GLASS),
                KvrConfig.clampInt(m, "dock_height_cm", -50, DOCK_HEIGHT_MIN, DOCK_HEIGHT_MAX),
                KvrConfig.clampInt(m, "dock_distance_cm", 85, DOCK_DISTANCE_MIN, DOCK_DISTANCE_MAX),
                KvrConfig.clampInt(m, "dock_follow_deg", 40, DOCK_FOLLOW_MIN, DOCK_FOLLOW_MAX),
                KvrConfig.bool(m, "dock_show_clock", true),
                KvrConfig.bool(m, "dock_show_battery", true),
                KvrConfig.bool(m, "sound_clicks", true),
                KvrConfig.bool(m, "sound_toasts", true),
                KvrConfig.bool(m, "sound_app_events", true),
                KvrConfig.bool(m, "window_shake", true));
        }
        return prefs;
    }

    /** Writes one key ({@code Boolean}, {@code Integer}, {@code String} or an enum) and reloads on next {@link #get()}. */
    public static synchronized void update(String key, Object value) {
        Object v = value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : value;
        Path p = dir.resolve(FILE);
        try {
            String text = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : DEFAULTS;
            Files.createDirectories(dir);
            Files.writeString(p, KvrConfig.withValue(text, key, KvrConfig.literal(v)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} not writable: {}", p, e.toString());
        }
        prefs = null;
    }

    // --- Pinned & Recent Desktop Apps (pre-0030, DEPRECATED) --------------------------------------
    // 0030: pins and history of PC apps live ONLY in DockPrefs (config/kvr/dock.json) — the dock reads them there.
    // These methods stay so old callers still compile; nothing in KVR uses them any more.

    private static final String PINNED_KEY = "desktop_pinned_apps";
    private static final String RECENTS_KEY = "desktop_recent_apps";

    /**
     * One-time migration (0030): command lines the player pinned with the pre-0030 launcher, if the file has the key.
     * Clears the key, so it runs once. The built-in default list is NOT migrated: PC apps are never pinned by themselves.
     */
    public static synchronized java.util.List<String> takeLegacyPinnedDesktopApps() {
        String raw = load().get(PINNED_KEY);
        if (raw == null || raw.isBlank()) return java.util.List.of();
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : raw.split(";")) {
            String t = s.trim();
            if (!t.isEmpty() && !list.contains(t)) list.add(t);
        }
        update(PINNED_KEY, "");
        return list;
    }

    /** @deprecated 0030: use {@link DockPrefs#pins()}. */
    @Deprecated
    public static synchronized java.util.List<String> getPinnedDesktopApps() {
        Map<String, String> m = load();
        String raw = m.getOrDefault(PINNED_KEY, "firefox --new-instance;foot;thunar");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : raw.split(";")) {
            String t = s.trim();
            if (!t.isEmpty() && !list.contains(t)) list.add(t);
        }
        return list;
    }

    /** @deprecated 0030: use {@link DockPrefs#pinDesktop} / {@link DockPrefs#unpin}. */
    @Deprecated
    public static synchronized boolean togglePinnedDesktopApp(String exec) {
        java.util.List<String> list = new java.util.ArrayList<>(getPinnedDesktopApps());
        boolean pinned;
        if (list.contains(exec)) {
            list.remove(exec);
            pinned = false;
        } else {
            list.add(exec);
            pinned = true;
        }
        update(PINNED_KEY, String.join(";", list));
        return pinned;
    }

    /** @deprecated 0030: use {@link DockPrefs#recent}. */
    @Deprecated
    public static synchronized java.util.List<String> getRecentApps() {
        Map<String, String> m = load();
        String raw = m.getOrDefault(RECENTS_KEY, "");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String s : raw.split(";")) {
            String t = s.trim();
            if (!t.isEmpty() && !list.contains(t)) list.add(t);
        }
        return list;
    }

    /** @deprecated 0030: use {@link DockPrefs#recordLaunch}. */
    @Deprecated
    public static synchronized void recordRecentApp(String exec) {
        java.util.List<String> list = new java.util.ArrayList<>(getRecentApps());
        list.remove(exec);
        list.add(0, exec);
        while (list.size() > 6) list.remove(list.size() - 1);
        update(RECENTS_KEY, String.join(";", list));
    }

    private static Map<String, String> load() {
        Path p = dir.resolve(FILE);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(dir);
                Files.writeString(p, DEFAULTS, StandardCharsets.UTF_8);
            }
            Map<String, String> m = KvrConfig.parse(DEFAULTS);
            m.putAll(KvrConfig.parse(Files.readString(p, StandardCharsets.UTF_8)));
            return m;
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} unreadable, using defaults: {}", p, e.toString());
            return KvrConfig.parse(DEFAULTS);
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E def) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
