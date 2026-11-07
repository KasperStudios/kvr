package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.client.PackageIcons;
import com.kasperstudios.kvr.config.DockPrefs;
import com.kasperstudios.kvr.config.KvrConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Programs installed on the PC (0028): the freedesktop {@code .desktop} entries every Linux desktop uses for its
 * app menu — {@code $XDG_DATA_HOME/applications} (default {@code ~/.local/share/applications}) first, then each
 * {@code $XDG_DATA_DIRS/applications} (default {@code /usr/local/share:/usr/share}; Flatpak and Snap add their
 * export dirs there). Desktop-entry ids follow the spec ({@code sub/foo.desktop} → {@code sub-foo.desktop}),
 * the first one found wins, so a user override hides the system entry.
 *
 * <p>Skipped: {@code Type} other than Application, {@code NoDisplay}/{@code Hidden}, {@code OnlyShowIn} (helpers
 * of one particular desktop environment), {@code TryExec} not on PATH. {@code Exec} is split into argv with the
 * spec's quoting rules and field codes removed (no shell, as for {@code host_desktop_apps});
 * {@code Terminal=true} runs inside {@link DockPrefs#terminal()} ({@code foot -e …}) in the same hidden compositor.
 *
 * <p>Scans on a background thread, at most every {@link #RESCAN_MS}. Custom commands from {@code host_desktop_apps}
 * come first. Windows has no {@code .desktop} files: there only the custom commands are listed (kvr-desktopd is
 * Linux-only until D5 anyway).
 */
public final class DesktopApps {

    /**
     * @param key  dock/recents key: {@code desk:<desktop-file-id>} or {@code deskcmd:<command line>}
     * @param icon PNG to show (resolved from the icon theme), null = letter tile
     */
    public record App(String key, String name, String comment, List<String> argv, @Nullable Path icon, boolean custom) {}

    public enum State { IDLE, SCANNING, READY }

    static final long RESCAN_MS = 30_000;
    private static final int MAX_ICON_BYTES = 256 * 1024;
    private static final String[] ICON_SIZES = {"48x48", "64x64", "32x32", "96x96", "128x128", "256x256", "24x24"};
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KVR-DesktopScan");
        t.setDaemon(true);
        return t;
    });
    private static final Set<String> ICON_REQUESTED = ConcurrentHashMap.newKeySet();

    private static volatile List<App> apps = List.of();
    private static volatile State state = State.IDLE;
    private static volatile long scannedAt;

    private DesktopApps() {}

    // --- client API ------------------------------------------------------------------------------

    /** Launcher opened / dock built: rescan in the background if the list is missing or older than 30 s. */
    public static void scanIfStale() {
        if (state == State.SCANNING) return;
        if (state == State.READY && System.currentTimeMillis() - scannedAt < RESCAN_MS) return;
        state = State.SCANNING;
        Minecraft mc = Minecraft.getInstance();
        String lang = mc.options.languageCode;
        String terminal = DockPrefs.terminal();
        List<List<String>> custom = DesktopDaemon.apps(KvrConfig.client().hostDesktopApps());
        IO.execute(() -> {
            List<App> out = new ArrayList<>();
            for (List<String> argv : custom) {
                String line = String.join(" ", argv);
                out.add(new App("deskcmd:" + line, line, "", List.copyOf(argv), null, true));
            }
            List<App> found = List.of();
            try {
                found = scan(dataDirs(System.getenv(), Path.of(System.getProperty("user.home"))), lang, terminal);
            } catch (RuntimeException e) {
                KVR.logger().warn("KVR desktop app scan failed: {}", e.toString());
            }
            out.addAll(found);
            List<App> result = List.copyOf(out);
            mc.execute(() -> {
                apps = result;
                scannedAt = System.currentTimeMillis();
                state = State.READY;
            });
        });
    }

    public static List<App> all() { return apps; }
    public static State state() { return state; }

    public static Optional<App> find(String key) {
        return apps.stream().filter(a -> a.key().equals(key)).findFirst();
    }

    /** Lazily loads an icon PNG into {@link PackageIcons} under the app key (row became visible). */
    public static void requestIcon(String key, @Nullable Path icon) {
        if (icon == null || PackageIcons.has(key) || !ICON_REQUESTED.add(key)) return;
        IO.execute(() -> {
            try {
                if (Files.size(icon) > MAX_ICON_BYTES) return;
                byte[] png = Files.readAllBytes(icon);
                Minecraft.getInstance().execute(() -> PackageIcons.register(key, png));
            } catch (IOException e) {
                // unreadable icon: the letter tile stays
            }
        });
    }

    // --- scanning (pure, no Minecraft classes) ---------------------------------------------------

    /** XDG base directories in precedence order (user data dir first). */
    static List<Path> dataDirs(Map<String, String> env, Path home) {
        List<Path> out = new ArrayList<>();
        String dataHome = env.getOrDefault("XDG_DATA_HOME", "");
        out.add(dataHome.isBlank() ? home.resolve(".local/share") : Path.of(dataHome));
        String dirs = env.getOrDefault("XDG_DATA_DIRS", "");
        for (String d : (dirs.isBlank() ? "/usr/local/share:/usr/share" : dirs).split(":")) {
            if (!d.isBlank()) out.add(Path.of(d));
        }
        // common export dirs, in case the game was started without them in XDG_DATA_DIRS
        out.add(home.resolve(".local/share/flatpak/exports/share"));
        out.add(Path.of("/var/lib/flatpak/exports/share"));
        out.add(Path.of("/var/lib/snapd/desktop"));
        return out.stream().distinct().toList();
    }

    static List<App> scan(List<Path> dataDirs, String lang, String terminal) {
        Map<String, App> byId = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Path base : dataDirs) {
            Path dir = base.resolve("applications");
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir, 3)) {
                for (Path f : (Iterable<Path>) walk::iterator) {
                    if (!f.getFileName().toString().endsWith(".desktop") || !Files.isRegularFile(f)) continue;
                    String id = dir.relativize(f).toString().replace('\\', '/').replace('/', '-');
                    if (!seen.add(id)) continue; // a higher-precedence dir already defined (or hid) this id
                    try {
                        String text = Files.readString(f, StandardCharsets.UTF_8);
                        parse(id, text, lang, terminal, dataDirs).ifPresent(a -> byId.put(id, a));
                    } catch (IOException | RuntimeException e) {
                        // broken or non-UTF-8 entry: skipped, like every desktop does
                    }
                }
            } catch (IOException e) {
                KVR.logger().debug("KVR desktop scan: {} unreadable: {}", dir, e.toString());
            }
        }
        List<App> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(a -> a.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** One desktop entry → app, or empty when it must not be listed. */
    static Optional<App> parse(String id, String text, String lang, String terminal, List<Path> dataDirs) {
        Map<String, String> e = desktopEntry(text);
        if (!"Application".equals(e.get("Type"))) return Optional.empty();
        if ("true".equalsIgnoreCase(e.get("NoDisplay")) || "true".equalsIgnoreCase(e.get("Hidden"))) return Optional.empty();
        if (e.containsKey("OnlyShowIn")) return Optional.empty();
        String exec = e.get("Exec");
        if (exec == null || exec.isBlank()) return Optional.empty();
        String tryExec = e.get("TryExec");
        if (tryExec != null && !tryExec.isBlank() && !executable(tryExec)) return Optional.empty();
        List<String> argv = execArgv(exec);
        if (argv.isEmpty()) return Optional.empty();
        if ("true".equalsIgnoreCase(e.get("Terminal"))) {
            List<String> wrapped = new ArrayList<>(List.of(terminal.split("\\s+")));
            wrapped.add("-e");
            wrapped.addAll(argv);
            argv = wrapped;
        }
        String name = localized(e, "Name", lang);
        if (name == null || name.isBlank()) return Optional.empty();
        String comment = localized(e, "Comment", lang);
        return Optional.of(new App("desk:" + id, name, comment == null ? "" : comment, List.copyOf(argv), icon(e.get("Icon"), dataDirs), false));
    }

    /** Keys of the {@code [Desktop Entry]} group only (actions and other groups are ignored). */
    static Map<String, String> desktopEntry(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        boolean inEntry = false;
        for (String raw : text.split("\r?\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[")) {
                inEntry = line.equals("[Desktop Entry]");
                continue;
            }
            if (!inEntry) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            out.putIfAbsent(line.substring(0, eq).strip(), unescapeString(line.substring(eq + 1).strip()));
        }
        return out;
    }

    /** {@code Name[ru_RU]} → {@code Name[ru]} → {@code Name}; Minecraft's {@code ru_ru} is mapped to the POSIX form. */
    private static String localized(Map<String, String> e, String key, String mcLang) {
        String[] p = mcLang.split("_");
        String full = p.length == 2 ? p[0] + "_" + p[1].toUpperCase(Locale.ROOT) : mcLang;
        String v = e.get(key + "[" + full + "]");
        if (v == null) v = e.get(key + "[" + p[0] + "]");
        return v != null ? v : e.get(key);
    }

    /** String-value escapes of the spec: \s \n \t \r \\ (before Exec quoting is applied). */
    static String unescapeString(String v) {
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                switch (n) {
                    case 's' -> sb.append(' ');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> sb.append('\\').append(n); // kept for the Exec quoting pass ( \" \$ \` )
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * {@code Exec} → argv: arguments split on unquoted spaces, {@code "…"} quoting with {@code \" \` \$ \\} escapes,
     * field codes removed ({@code %f %F %u %U %i %c %k} and the deprecated ones — we never pass files/URLs),
     * {@code %%} → {@code %}.
     */
    static List<String> execArgv(String exec) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < exec.length(); i++) {
            char c = exec.charAt(i);
            if (quoted) {
                if (c == '\\' && i + 1 < exec.length() && "\"`$\\".indexOf(exec.charAt(i + 1)) >= 0) {
                    cur.append(exec.charAt(++i));
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
                continue;
            }
            if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == ' ' || c == '\t') {
                if (any || cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                any = false;
            } else if (c == '%' && i + 1 < exec.length()) {
                char code = exec.charAt(++i);
                if (code == '%') cur.append('%');
                // every other field code expands to nothing here
            } else {
                cur.append(c);
            }
        }
        if (any || cur.length() > 0) out.add(cur.toString());
        out.removeIf(String::isEmpty);
        return out;
    }

    private static boolean executable(String exe) {
        if (exe.contains("/")) return Files.isExecutable(Path.of(exe));
        String path = System.getenv("PATH");
        if (path == null) return true; // cannot tell: list it
        for (String d : path.split(java.io.File.pathSeparator)) {
            if (!d.isBlank() && Files.isExecutable(Path.of(d, exe))) return true;
        }
        return false;
    }

    /** Icon theme lookup (hicolor PNG sizes, then pixmaps); SVG-only icons get the letter tile. */
    @Nullable
    private static Path icon(@Nullable String icon, List<Path> dataDirs) {
        if (icon == null || icon.isBlank()) return null;
        if (icon.startsWith("/")) {
            Path p = Path.of(icon);
            return icon.endsWith(".png") && Files.isRegularFile(p) ? p : null;
        }
        String name = icon.endsWith(".png") ? icon.substring(0, icon.length() - 4) : icon;
        for (Path base : dataDirs) {
            for (String size : ICON_SIZES) {
                Path p = base.resolve("icons/hicolor/" + size + "/apps/" + name + ".png");
                if (Files.isRegularFile(p)) return p;
            }
        }
        for (Path base : dataDirs) {
            Path p = base.resolve("pixmaps/" + name + ".png");
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }
}
