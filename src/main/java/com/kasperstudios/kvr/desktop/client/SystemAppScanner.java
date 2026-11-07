package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.KVR;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans Linux .desktop entries (/usr/share/applications, ~/.local/share/applications)
 * or system PATH to discover installed apps on the host machine.
 */
public final class SystemAppScanner {

    public record AppEntry(String id, String name, String exec, String comment, boolean terminal) {}

    private static List<AppEntry> cached;
    private static long lastScanTime;

    private SystemAppScanner() {}

    public static synchronized List<AppEntry> getInstalledApps() {
        long now = System.currentTimeMillis();
        if (cached != null && now - lastScanTime < 60_000) {
            return cached;
        }

        Map<String, AppEntry> found = new LinkedHashMap<>();

        // Linux .desktop scan
        List<Path> dirs = List.of(
            Path.of("/usr/share/applications"),
            Path.of("/usr/local/share/applications"),
            Path.of(System.getProperty("user.home"), ".local/share/applications")
        );

        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".desktop")).forEach(p -> {
                    AppEntry entry = parseDesktopFile(p);
                    if (entry != null && !found.containsKey(entry.id())) {
                        found.put(entry.id(), entry);
                    }
                });
            } catch (Exception ignored) {}
        }

        // If nothing found or not Linux, fallback to common essentials
        if (found.isEmpty()) {
            addFallback(found, "firefox", "Firefox", "firefox --new-instance");
            addFallback(found, "chromium", "Chromium", "chromium");
            addFallback(found, "foot", "Foot Terminal", "foot");
            addFallback(found, "alacritty", "Alacritty", "alacritty");
            addFallback(found, "thunar", "File Manager", "thunar");
        }

        List<AppEntry> list = new ArrayList<>(found.values());
        list.sort(Comparator.comparing(AppEntry::name, String.CASE_INSENSITIVE_ORDER));
        cached = Collections.unmodifiableList(list);
        lastScanTime = now;
        return cached;
    }

    private static void addFallback(Map<String, AppEntry> map, String id, String name, String exec) {
        map.put(id, new AppEntry(id, name, exec, "", false));
    }

    private static AppEntry parseDesktopFile(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            String name = "";
            String exec = "";
            String comment = "";
            boolean noDisplay = false;
            boolean terminal = false;
            boolean inDesktopEntry = false;

            for (String raw : lines) {
                String line = raw.trim();
                if (line.startsWith("[Desktop Entry]")) {
                    inDesktopEntry = true;
                    continue;
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    inDesktopEntry = false;
                    continue;
                }
                if (!inDesktopEntry) continue;

                if (line.startsWith("Name=") && name.isEmpty()) {
                    name = line.substring(5).trim();
                } else if (line.startsWith("Exec=") && exec.isEmpty()) {
                    exec = cleanExec(line.substring(5).trim());
                } else if (line.startsWith("Comment=") && comment.isEmpty()) {
                    comment = line.substring(8).trim();
                } else if (line.equalsIgnoreCase("NoDisplay=true") || line.equalsIgnoreCase("Hidden=true")) {
                    noDisplay = true;
                } else if (line.equalsIgnoreCase("Terminal=true")) {
                    terminal = true;
                }
            }

            if (noDisplay || name.isEmpty() || exec.isEmpty()) return null;
            String fileName = path.getFileName().toString();
            String id = fileName.substring(0, fileName.length() - 8);
            return new AppEntry(id, name, exec, comment, terminal);
        } catch (Exception e) {
            return null;
        }
    }

    private static String cleanExec(String exec) {
        // Strip out %f, %F, %u, %U, etc.
        return exec.replaceAll("%[a-zA-Z]", "").trim();
    }
}
