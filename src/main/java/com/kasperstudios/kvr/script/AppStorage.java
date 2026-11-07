package com.kasperstudios.kvr.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.config.KvrConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Key-Value local storage for KVR OS applications (SPEC 5.2 / CONCEPT §2.5: kvr.storage).
 * Preserves simple state across restarts without requiring full async VFS file operations.
 * Stored under {@code config/kvr/data/<appId>/storage.json}.
 */
public final class AppStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private AppStorage() {}

    private static Path file(String appId) {
        return KvrConfig.directory().resolve("kvr").resolve("data").resolve(appId).resolve("storage.json");
    }

    private static Map<String, String> load(String appId) {
        Path path = file(appId);
        if (!Files.exists(path)) return new ConcurrentHashMap<>();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(r, MAP_TYPE);
            return map != null ? new ConcurrentHashMap<>(map) : new ConcurrentHashMap<>();
        } catch (IOException | RuntimeException e) {
            KVR.logger().warn("KVR app {} storage read error: {}", appId, e.toString());
            return new ConcurrentHashMap<>();
        }
    }

    private static void persist(String appId, Map<String, String> map) {
        Path path = file(appId);
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(map, MAP_TYPE, w);
            }
        } catch (IOException e) {
            KVR.logger().warn("KVR app {} storage save error: {}", appId, e.toString());
        }
    }

    private static Map<String, String> map(String appId) {
        return CACHE.computeIfAbsent(appId, AppStorage::load);
    }

    @Nullable
    public static String get(String appId, String key) {
        return map(appId).get(key);
    }

    public static void set(String appId, String key, String value) {
        Map<String, String> m = map(appId);
        m.put(key, value);
        persist(appId, m);
    }

    public static void remove(String appId, String key) {
        Map<String, String> m = map(appId);
        if (m.remove(key) != null) persist(appId, m);
    }

    public static Set<String> keys(String appId) {
        return Collections.unmodifiableSet(map(appId).keySet());
    }

    public static void clear(String appId) {
        Map<String, String> m = map(appId);
        m.clear();
        persist(appId, m);
    }
}
