package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.VrAppException;
import com.kasperstudios.kvr.apps.VrAppManifest;
import com.kasperstudios.kvr.apps.VrAppPackage;
import com.kasperstudios.kvr.config.AppsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Developer mode (CONCEPT §2 "Воркфлоу разработки"): open {@code <game>/kvr_apps/} in VSCode, every
 * subfolder with a {@code config.json} is an app that runs unpacked — no install, no quota — and reloads
 * by itself when a file is saved (hot reload). {@code Pack} turns a folder into {@code kvr_apps/<id>.vrapp}
 * ({@code kvr pack myapp}); {@code .vrapp} files dropped into the folder can be installed from the Dev tab.
 *
 * <p>Only with {@code dev_apps = true} in {@code kvr-apps-client.toml}. Dev apps still run in the normal
 * sandbox with the permissions their config.json declares, and their files use {@code /data/<id>/} of the
 * worn glasses — the same folder the installed version uses.
 */
public final class DevApps {

    public record Folder(Path dir, VrAppManifest manifest) {}

    /** A broken folder is listed with its error so the author sees why it does not run. */
    public record Broken(Path dir, String error) {}

    public record PackedFile(Path file, String name) {}

    private static final int RESCAN_TICKS = 40;
    private static List<Folder> folders = List.of();
    private static List<Broken> broken = List.of();
    private static List<PackedFile> packed = List.of();
    private static int ticks;

    private DevApps() {}

    public static boolean enabled() {
        return AppsConfig.client().devApps();
    }

    public static Path dir() {
        return Path.of(AppsConfig.client().devAppsDir()).toAbsolutePath().normalize();
    }

    /** Client tick: rescan every 2 s (cheap: one config.json per folder). */
    public static void tick() {
        if (!enabled()) return;
        if (++ticks % RESCAN_TICKS == 0) rescan();
    }

    public static void rescan() {
        List<Folder> ok = new ArrayList<>();
        List<Broken> bad = new ArrayList<>();
        List<PackedFile> files = new ArrayList<>();
        Path root = dir();
        try {
            Files.createDirectories(root);
            try (Stream<Path> s = Files.list(root)) {
                for (Path p : s.sorted().toList()) {
                    String name = p.getFileName().toString();
                    if (name.startsWith(".")) continue;
                    if (Files.isRegularFile(p) && name.endsWith(".vrapp")) {
                        files.add(new PackedFile(p, name));
                        continue;
                    }
                    Path cfg = p.resolve("config.json");
                    if (!Files.isDirectory(p) || !Files.isRegularFile(cfg)) continue;
                    try {
                        VrAppManifest m = VrAppManifest.parse(Files.readString(cfg));
                        if (VrAppManifest.reserved(m.id())) throw new VrAppException(VrAppException.Code.RESERVED_ID, m.id());
                        ok.add(new Folder(p, m));
                    } catch (VrAppException | IOException e) {
                        bad.add(new Broken(p, e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            KVR.logger().warn("KVR dev apps: cannot read {}: {}", root, e.toString());
        }
        folders = List.copyOf(ok);
        broken = List.copyOf(bad);
        packed = List.copyOf(files);
    }

    public static Optional<Folder> folder(String appId) {
        return folders.stream().filter(f -> f.manifest().id().equals(appId)).findFirst();
    }

    /** Full read with all package rules (imports resolve, file types, sizes). */
    public static VrAppPackage load(String appId) throws VrAppException {
        Folder f = folder(appId).orElseThrow(() -> new VrAppException(VrAppException.Code.NOT_FOUND, appId));
        return VrAppPackage.fromDirectory(f.dir());
    }

    /** Newest modification time of any file in the app folder: the hot-reload trigger. */
    public static long stamp(String appId) {
        Optional<Folder> f = folder(appId);
        if (f.isEmpty()) return -1;
        try (Stream<Path> s = Files.walk(f.get().dir(), VrAppPackage.MAX_DEPTH + 1)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }).max().orElse(0L);
        } catch (IOException e) {
            return -1;
        }
    }

    /** {@code kvr pack <id>}: writes {@code kvr_apps/<id>-<version>.vrapp} and returns its bytes. */
    public static byte[] pack(String appId) throws VrAppException {
        Folder f = folder(appId).orElseThrow(() -> new VrAppException(VrAppException.Code.NOT_FOUND, appId));
        byte[] zip = VrAppPackage.pack(f.dir());
        Path out = dir().resolve(appId + "-" + f.manifest().version().replaceAll("[^A-Za-z0-9._-]", "_") + ".vrapp");
        try {
            Files.write(out, zip);
        } catch (IOException e) {
            throw new VrAppException(VrAppException.Code.IO, e.getMessage());
        }
        rescan();
        return zip;
    }

    public static byte[] readPacked(PackedFile f) throws VrAppException {
        try {
            if (Files.size(f.file()) > VrAppPackage.MAX_PACKAGE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, f.name());
            return Files.readAllBytes(f.file());
        } catch (IOException e) {
            throw new VrAppException(VrAppException.Code.IO, e.getMessage());
        }
    }

    public static List<Folder> folders() { return folders; }
    public static List<Broken> broken() { return broken; }
    public static List<PackedFile> packed() { return packed; }
}
