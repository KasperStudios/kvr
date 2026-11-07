package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.VrAppPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Client package cache, {@code config/kvr/cache/vrapp/<sha256>.vrapp}: the server is the source of truth,
 * but a package already seen is never downloaded again (content-addressed: same hash = same bytes).
 * Every read is re-hashed, a tampered or truncated file is deleted. Trimmed to {@link #MAX_BYTES}, oldest first.
 */
public final class AppCache {

    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static Path dir = Path.of("config", "kvr", "cache", "vrapp");

    private AppCache() {}

    public static synchronized Optional<byte[]> get(String sha256) {
        if (!sha256.matches("[0-9a-f]{64}")) return Optional.empty();
        Path f = dir.resolve(sha256 + ".vrapp");
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            byte[] b = Files.readAllBytes(f);
            if (!VrAppPackage.sha256(b).equals(sha256)) {
                Files.deleteIfExists(f);
                return Optional.empty();
            }
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis())); // LRU
            return Optional.of(b);
        } catch (IOException e) {
            KVR.logger().warn("KVR app cache: {} unreadable: {}", f, e.toString());
            return Optional.empty();
        }
    }

    public static synchronized void put(byte[] bytes) {
        String sha = VrAppPackage.sha256(bytes);
        Path f = dir.resolve(sha + ".vrapp");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(f)) Files.write(f, bytes);
            trim();
        } catch (IOException e) {
            KVR.logger().warn("KVR app cache: cannot write {}: {}", f, e.toString());
        }
    }

    private static void trim() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".vrapp")).forEach(files::add);
        }
        long total = 0;
        for (Path p : files) total += Files.size(p);
        if (total <= MAX_BYTES) return;
        files.sort(Comparator.comparingLong(p -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));
        for (Path p : files) {
            if (total <= MAX_BYTES) break;
            total -= Files.size(p);
            Files.deleteIfExists(p);
        }
    }
}
