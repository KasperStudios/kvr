package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code config/kvr-client.toml} and {@code config/kvr-server.toml}: a flat TOML subset
 * ({@code key = true | 123 | "text"}, {@code # comments}, {@code [sections]} are ignored), no library,
 * same code on Fabric and NeoForge. Missing files are written with defaults; unknown keys are kept.
 * The settings screen and the consent dialog write single keys back with {@link #updateClient}.
 *
 * <p>The directory is {@code ./config} (game dir = working dir on both loaders and the dedicated
 * server). If a platform config dir is preferred, call {@link #setDirectory(Path)} before first use.
 */
public final class KvrConfig {

    /**
     * @param hostDesktopDaemon       path to kvr-desktopd; empty = {@code config/kvr/bin/kvr-desktopd[.exe]}
     * @param hostDesktopDownloadUrl  where to fetch the binary if it is missing (empty = never download)
     * @param hostDesktopDownloadSha256 required SHA-256 of the download (no hash = no download)
     * @param hostDesktopConsent      SHA-256 of the binary the user agreed to run (empty = no consent)
     * @param hostDesktopApps         launcher entries, {@code ;}-separated command lines
     * @param windowOpacity           dark backdrop under window glass, percent 0..100 (0018: readability)
     * @param desktopClipboard        share clipboard text between PC apps in KVR and the system (D2)
     */
    public record Client(boolean hostDesktopWindows, boolean defaultWindowPublic, boolean showPublicWindows,
                         String hostDesktopDaemon, String hostDesktopDownloadUrl, String hostDesktopDownloadSha256,
                         String hostDesktopConsent, String hostDesktopApps, int windowOpacity, boolean desktopClipboard) {}

    public record Server(boolean hostDesktopWindows, int hostDesktopMaxWindows, int publicWindowRange, int batteryLife) {}

    static final String CLIENT_FILE = "kvr-client.toml";
    private static final String CLIENT_DEFAULTS = """
        # KVR Spatial OS - client
        # Desktop apps inside KVR: kvr-desktopd runs them in its own hidden Wayland compositor, they never
        # appear on your real desktop. Needs this switch, your consent (settings screen) AND the server allowing it.
        enable_host_desktop_windows = false
        # Path to kvr-desktopd; empty = config/kvr/bin/kvr-desktopd
        host_desktop_daemon = ""
        # Optional download of kvr-desktopd when it is missing: URL + required SHA-256 (both or nothing)
        host_desktop_download_url = ""
        host_desktop_download_sha256 = ""
        # SHA-256 of the kvr-desktopd binary you allowed to run (written by the consent dialog; empty = not allowed)
        host_desktop_consent = ""
        # Desktop launcher entries, separated by ';' (no shell, arguments split on spaces)
        host_desktop_apps = "foot;firefox --new-instance;thunar"
        # Clipboard text shared between PC apps in KVR and your system clipboard (copy in the app -> paste anywhere)
        desktop_clipboard_sync = true
        # New windows are Public (materialise for other players nearby) instead of Private.
        # Per window: gear menu. Private windows are shown to nobody - only the secret HackGlasses can intercept them.
        # Desktop-app windows never leave this computer.
        default_window_public = false
        # Draw other players' Public windows in the world
        show_public_windows = true
        # Dark backdrop under window glass, percent (0 = pure glass, 100 = opaque). Higher = easier to read.
        window_opacity = 75
        """;

    private static Path dir = Path.of("config");
    private static Client client;
    private static Server server;

    private KvrConfig() {}

    public static synchronized void setDirectory(Path directory) {
        dir = directory;
        client = null;
        server = null;
    }

    public static synchronized Client client() {
        if (client == null) {
            Map<String, String> m = load(CLIENT_FILE, CLIENT_DEFAULTS);
            client = new Client(
                bool(m, "enable_host_desktop_windows", false),
                bool(m, "default_window_public", false),
                bool(m, "show_public_windows", true),
                str(m, "host_desktop_daemon"),
                str(m, "host_desktop_download_url"),
                str(m, "host_desktop_download_sha256"),
                str(m, "host_desktop_consent"),
                str(m, "host_desktop_apps"),
                clampInt(m, "window_opacity", 75, 0, 100),
                bool(m, "desktop_clipboard_sync", true));
        }
        return client;
    }

    public static synchronized Server server() {
        if (server == null) {
            Map<String, String> m = load("kvr-server.toml", """
                # KVR Spatial OS - server
                # Allow players to run desktop apps inside KVR (rendered only on their own client, never sent anywhere).
                enable_host_desktop_windows = false
                # Max desktop windows per player
                host_desktop_max_windows = 2
                # Public windows are relayed to players within this many blocks (0 = feature off)
                public_window_range = 48
                # Glasses battery life: real drain = nominal module draw / this (100: T1 kit ~9 min, T5 kit ~2 h on a full cell)
                battery_life = 100
                """);
            server = new Server(bool(m, "enable_host_desktop_windows", false), clampInt(m, "host_desktop_max_windows", 2, 0, 8),
                clampInt(m, "public_window_range", 48, 0, 128), clampInt(m, "battery_life", 100, 1, 10_000));
        }
        return server;
    }

    /**
     * Writes one key of {@code kvr-client.toml} (settings screen, consent dialog) and reloads.
     * {@code value} is a Boolean, Integer or String; strings must not contain quotes or line breaks.
     */
    public static synchronized void updateClient(String key, Object value) {
        Path p = dir.resolve(CLIENT_FILE);
        try {
            String text = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : CLIENT_DEFAULTS;
            Files.createDirectories(dir);
            Files.writeString(p, withValue(text, key, literal(value)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} not writable: {}", p, e.toString());
        }
        client = null;
    }

    static String literal(Object value) {
        if (value instanceof Boolean || value instanceof Integer) return value.toString();
        String s = String.valueOf(value);
        if (s.contains("\"") || s.contains("\n") || s.contains("\r")) throw new IllegalArgumentException("bad config value");
        return "\"" + s + "\"";
    }

    /** Pure: replaces {@code key = ...} (keeping comments and other lines) or appends it. */
    static String withValue(String text, String key, String literal) {
        StringBuilder out = new StringBuilder();
        boolean done = false;
        for (String raw : text.split("\n", -1)) {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (!done && !line.startsWith("#") && eq > 0 && line.substring(0, eq).strip().equals(key)) {
                out.append(key).append(" = ").append(literal).append('\n');
                done = true;
            } else {
                out.append(raw).append('\n');
            }
        }
        String s = out.substring(0, out.length() - 1); // split(-1) gave one extra empty tail
        if (!done) s = (s.endsWith("\n") || s.isEmpty() ? s : s + "\n") + key + " = " + literal + "\n";
        return s;
    }

    /** Pure parser, unit-testable. A {@code #} inside a quoted value is part of the value. */
    static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : text.split("\n")) {
            String line = stripComment(raw.strip());
            if (line.isEmpty() || line.startsWith("[")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String v = line.substring(eq + 1).strip();
            if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
            out.put(line.substring(0, eq).strip(), v);
        }
        return out;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (c == '#' && !quoted) return line.substring(0, i).strip();
        }
        return line;
    }

    private static Map<String, String> load(String file, String defaults) {
        Path p = dir.resolve(file);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(dir);
                Files.writeString(p, defaults, StandardCharsets.UTF_8);
            }
            Map<String, String> m = parse(defaults);
            m.putAll(parse(Files.readString(p, StandardCharsets.UTF_8)));
            return m;
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} unreadable, using defaults: {}", p, e.toString());
            return parse(defaults);
        }
    }

    static boolean bool(Map<String, String> m, String k, boolean def) {
        String v = m.get(k);
        return v == null ? def : v.equalsIgnoreCase("true");
    }

    static String str(Map<String, String> m, String k) {
        return m.getOrDefault(k, "").strip();
    }

    static int clampInt(Map<String, String> m, String k, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(m.getOrDefault(k, String.valueOf(def)))));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
