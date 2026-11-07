package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * App Manager settings (0025), in their own files next to the main ones so {@link KvrConfig}'s records
 * (and your local edits to them) stay untouched:
 * <ul>
 *   <li>{@code config/kvr-apps-client.toml} — store index URL, developer mode;</li>
 *   <li>{@code config/kvr-apps-server.toml} — may players install third-party apps at all, allowlist, size cap.</li>
 * </ul>
 * Same flat TOML subset and parser as {@link KvrConfig}.
 */
public final class AppsConfig {

    public static final String DEFAULT_INDEX = "https://raw.githubusercontent.com/kasper-studios/kvr-apps-index/main/index.json";

    /**
     * @param storeIndexUrl online KVR Store index (empty = online catalog off)
     * @param devApps       developer mode: run unpacked apps from {@code devAppsDir} with hot reload, Pack button
     * @param devAppsDir    folder for developer apps, relative to the game dir
     */
    public record Client(String storeIndexUrl, boolean devApps, String devAppsDir) {}

    /**
     * @param allowInstall  players may install third-party .vrapp packages on this server
     * @param allowlist     if not empty: only these app ids may be installed
     * @param maxPackageKb  largest package accepted (also capped by the Storage tier quota)
     */
    public record Server(boolean allowInstall, List<String> allowlist, int maxPackageKb) {}

    private static final String CLIENT_FILE = "kvr-apps-client.toml";
    private static final String SERVER_FILE = "kvr-apps-server.toml";
    private static final String CLIENT_DEFAULTS = """
        # KVR Spatial OS - apps (client)
        # Online KVR Store index (GitHub). Empty = only the built-in catalog.
        store_index_url = "%s"
        # Developer mode: apps from dev_apps_dir run unpacked with hot reload; App Market gets a "Dev" tab
        # with "Pack .vrapp" and "Install". The store index may then also be a local file:/// URL.
        dev_apps = false
        dev_apps_dir = "kvr_apps"
        """.formatted(DEFAULT_INDEX);
    private static final String SERVER_DEFAULTS = """
        # KVR Spatial OS - apps (server)
        # Players may install third-party .vrapp packages into their glasses (stored in <world>/kvr/vfs, counted
        # against the Storage tier). false = only the apps built into the mod.
        allow_app_install = true
        # Comma-separated app ids allowed to be installed; empty = any app
        app_allowlist = ""
        # Largest package in KiB (hard cap 4096)
        max_package_kb = 1024
        """;

    private static Path dir = Path.of("config");
    private static Client client;
    private static Server server;

    private AppsConfig() {}

    public static synchronized void setDirectory(Path directory) {
        dir = directory;
        client = null;
        server = null;
    }

    public static synchronized Client client() {
        if (client == null) {
            Map<String, String> m = load(CLIENT_FILE, CLIENT_DEFAULTS);
            String d = KvrConfig.str(m, "dev_apps_dir");
            client = new Client(KvrConfig.str(m, "store_index_url"), KvrConfig.bool(m, "dev_apps", false), d.isEmpty() ? "kvr_apps" : d);
        }
        return client;
    }

    public static synchronized Server server() {
        if (server == null) {
            Map<String, String> m = load(SERVER_FILE, SERVER_DEFAULTS);
            List<String> allow = new ArrayList<>();
            for (String s : KvrConfig.str(m, "app_allowlist").split(",")) {
                String id = s.strip().toLowerCase(Locale.ROOT);
                if (!id.isEmpty()) allow.add(id);
            }
            server = new Server(KvrConfig.bool(m, "allow_app_install", true), List.copyOf(allow), KvrConfig.clampInt(m, "max_package_kb", 1024, 16, 4096));
        }
        return server;
    }

    /** Re-read both files on next access (after editing them by hand). */
    public static synchronized void reload() {
        client = null;
        server = null;
    }

    private static Map<String, String> load(String file, String defaults) {
        Path p = dir.resolve(file);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(dir);
                Files.writeString(p, defaults, StandardCharsets.UTF_8);
            }
            Map<String, String> m = KvrConfig.parse(defaults);
            m.putAll(KvrConfig.parse(Files.readString(p, StandardCharsets.UTF_8)));
            return m;
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} unreadable, using defaults: {}", p, e.toString());
            return KvrConfig.parse(defaults);
        }
    }
}
