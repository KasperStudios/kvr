package com.kasperstudios.kvr.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dock pins and launch history (0028, Creator: the dock is NOT a dump of everything — the player pins (★) what
 * they want; the rest starts from the launchers, which sort by recent use). {@code config/kvr/dock.json}, client.
 *
 * <p>Keys: an installed {@code .vrapp} = its app id ({@code notes}); a developer folder = {@code dev:<id>};
 * a PC app = {@code desk:<desktop-file-id>}, a custom command from {@code host_desktop_apps} = {@code deskcmd:<line>}.
 * PC apps are stored with their name, argv and icon path, so a pinned one is in the dock (and starts) before the
 * {@code .desktop} scan has even run.
 */
public final class DockPrefs {

    /** A pinned PC app, enough to show it in the dock and start it. */
    public record DesktopPin(String key, String name, List<String> argv, String icon) {}

    private static final int MAX_RECENT = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file = Path.of("config", "kvr", "dock.json");

    private static final List<String> PINS = new ArrayList<>();
    private static final Map<String, DesktopPin> DESKTOP = new LinkedHashMap<>();
    private static final Map<String, Long> RECENT = new LinkedHashMap<>();
    private static boolean migrated;
    private static String terminal = "foot";
    private static boolean loaded;

    private DockPrefs() {}

    public static synchronized void setFile(Path path) {
        file = path;
        loaded = false;
    }

    // --- pins ------------------------------------------------------------------------------------

    public static synchronized List<String> pins() {
        load();
        return List.copyOf(PINS);
    }

    public static synchronized boolean isPinned(String key) {
        load();
        return PINS.contains(key);
    }

    public static synchronized void pin(String key) {
        load();
        if (!PINS.contains(key)) {
            PINS.add(key);
            save();
        }
    }

    public static synchronized void pinDesktop(DesktopPin pin) {
        load();
        DESKTOP.put(pin.key(), pin);
        if (!PINS.contains(pin.key())) PINS.add(pin.key());
        save();
    }

    public static synchronized void unpin(String key) {
        load();
        boolean changed = PINS.remove(key);
        changed |= DESKTOP.remove(key) != null;
        if (changed) save();
    }

    public static synchronized Optional<DesktopPin> desktopPin(String key) {
        load();
        return Optional.ofNullable(DESKTOP.get(key));
    }

    // --- recents ---------------------------------------------------------------------------------

    public static synchronized void recordLaunch(String key) {
        load();
        RECENT.remove(key);
        RECENT.put(key, System.currentTimeMillis());
        while (RECENT.size() > MAX_RECENT) RECENT.remove(RECENT.keySet().iterator().next());
        save();
    }

    /** Epoch millis of the last launch, 0 = never. */
    public static synchronized long lastLaunch(String key) {
        load();
        return RECENT.getOrDefault(key, 0L);
    }

    /** 0030: keys launched so far that start with {@code prefix}, newest first, at most {@code limit}. */
    public static synchronized List<String> recent(String prefix, int limit) {
        load();
        List<String> keys = new ArrayList<>(RECENT.keySet()); // insertion order: recordLaunch re-adds at the end
        List<String> out = new ArrayList<>();
        for (int i = keys.size() - 1; i >= 0 && out.size() < limit; i--) {
            if (keys.get(i).startsWith(prefix)) out.add(keys.get(i));
        }
        return out;
    }

    // --- misc ------------------------------------------------------------------------------------

    /** One-time migration from 0027 (every installed app was in the dock): pin what is installed now. */
    public static synchronized boolean migrated() {
        load();
        return migrated;
    }

    public static synchronized void setMigrated() {
        load();
        migrated = true;
        save();
    }

    /** Terminal emulator for {@code Terminal=true} desktop entries (runs inside kvr-desktopd like any app). */
    public static synchronized String terminal() {
        load();
        return terminal;
    }

    // --- file ------------------------------------------------------------------------------------

    private static void load() {
        if (loaded) return;
        loaded = true;
        PINS.clear();
        DESKTOP.clear();
        RECENT.clear();
        migrated = false;
        if (!Files.isRegularFile(file)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("pins")) for (JsonElement e : o.getAsJsonArray("pins")) PINS.add(e.getAsString());
            if (o.has("desktop")) {
                for (JsonElement e : o.getAsJsonArray("desktop")) {
                    JsonObject d = e.getAsJsonObject();
                    List<String> argv = new ArrayList<>();
                    for (JsonElement a : d.getAsJsonArray("argv")) argv.add(a.getAsString());
                    String key = d.get("key").getAsString();
                    DESKTOP.put(key, new DesktopPin(key, d.get("name").getAsString(), List.copyOf(argv), d.has("icon") ? d.get("icon").getAsString() : ""));
                }
            }
            if (o.has("recent")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("recent").entrySet()) RECENT.put(e.getKey(), e.getValue().getAsLong());
            migrated = o.has("migrated") && o.get("migrated").getAsBoolean();
            if (o.has("terminal")) terminal = o.get("terminal").getAsString();
        } catch (IOException | RuntimeException e) {
            KVR.logger().warn("KVR dock prefs {} unreadable, starting empty: {}", file, e.toString());
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray pins = new JsonArray();
        PINS.forEach(pins::add);
        o.add("pins", pins);
        JsonArray desktop = new JsonArray();
        for (DesktopPin p : DESKTOP.values()) {
            JsonObject d = new JsonObject();
            d.addProperty("key", p.key());
            d.addProperty("name", p.name());
            JsonArray argv = new JsonArray();
            p.argv().forEach(argv::add);
            d.add("argv", argv);
            d.addProperty("icon", p.icon());
            desktop.add(d);
        }
        o.add("desktop", desktop);
        JsonObject recent = new JsonObject();
        RECENT.forEach(recent::addProperty);
        o.add("recent", recent);
        o.addProperty("migrated", migrated);
        o.addProperty("terminal", terminal);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KVR.logger().warn("KVR dock prefs {} not writable: {}", file, e.toString());
        }
    }
}
