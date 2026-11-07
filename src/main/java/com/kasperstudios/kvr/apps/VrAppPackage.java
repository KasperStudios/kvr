package com.kasperstudios.kvr.apps;

import com.kasperstudios.kvr.script.VrApp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code .vrapp} (CONCEPT §2.2): a plain ZIP, like an APK —
 * <pre>
 * myapp.vrapp
 * ├── config.json      manifest ({@link VrAppManifest})
 * ├── main.js          entry module
 * ├── components.js    any number of .js / .json modules, subfolders allowed
 * ├── styles.js
 * └── assets/          optional .png / .ogg / .txt
 * </pre>
 * Read fully in memory, never unpacked to disk: the server validates uploads with this class, the client
 * runs straight from it. GitHub release archives that wrap everything in one top folder are accepted.
 *
 * <p>Hard limits (zip-bomb and abuse guards, checked while inflating, not trusted from the headers):
 * {@link #MAX_PACKAGE_BYTES} packed, {@link #MAX_UNPACKED_BYTES} unpacked, {@link #MAX_ENTRIES} files,
 * {@link #MAX_TEXT_FILE_BYTES} per source file. Text must be valid UTF-8.
 */
public final class VrAppPackage {

    public static final int MAX_PACKAGE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_UNPACKED_BYTES = 16 * 1024 * 1024;
    public static final int MAX_ENTRIES = 128;
    public static final int MAX_TEXT_FILE_BYTES = 1024 * 1024;
    public static final int MAX_DEPTH = 5;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final List<String> TEXT_EXT = List.of(".js", ".json", ".txt", ".md");
    private static final List<String> ASSET_EXT = List.of(".png", ".ogg");
    /** 1980-01-01: fixed entry time so packing the same folder twice gives the same SHA-256. */
    private static final long FIXED_TIME = 315_532_800_000L;

    private final VrAppManifest manifest;
    private final Map<String, String> sources;
    private final Map<String, byte[]> assets;
    @Nullable private final byte[] raw;
    private final String sha256;
    private final long unpackedBytes;

    private VrAppPackage(VrAppManifest manifest, Map<String, String> sources, Map<String, byte[]> assets, @Nullable byte[] raw, String sha256, long unpacked) {
        this.manifest = manifest;
        this.sources = Collections.unmodifiableMap(sources);
        this.assets = Collections.unmodifiableMap(assets);
        this.raw = raw;
        this.sha256 = sha256;
        this.unpackedBytes = unpacked;
    }

    // --- reading ---------------------------------------------------------------------------------

    public static VrAppPackage read(byte[] zip) throws VrAppException {
        if (zip.length > MAX_PACKAGE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, zip.length + " > " + MAX_PACKAGE_BYTES + " bytes");
        Map<String, byte[]> files = new TreeMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            int count = 0;
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (++count > MAX_ENTRIES) throw new VrAppException(VrAppException.Code.TOO_MANY_FILES, "> " + MAX_ENTRIES);
                String name = e.getName();
                if (isJunk(name)) continue;
                checkPath(name);
                if (files.containsKey(name)) throw new VrAppException(VrAppException.Code.BAD_ZIP, "duplicate entry " + name);
                byte[] data = inflate(in, name, MAX_UNPACKED_BYTES - total);
                total += data.length;
                files.put(name, data);
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new VrAppException(VrAppException.Code.BAD_ZIP, String.valueOf(ex.getMessage()));
        }
        if (files.isEmpty()) throw new VrAppException(VrAppException.Code.BAD_ZIP, "empty archive (or not a zip)");
        return build(stripWrapper(files), zip, total);
    }

    /** Developer mode: an unpacked folder ({@code kvr_apps/<app>/}), read with the same rules. */
    public static VrAppPackage fromDirectory(Path dir) throws VrAppException {
        Map<String, byte[]> files = collect(dir);
        long total = files.values().stream().mapToLong(b -> b.length).sum();
        return build(files, null, total);
    }

    /** {@code kvr pack}: the folder as a deterministic {@code .vrapp} (same files → same bytes → same SHA-256). */
    public static byte[] pack(Path dir) throws VrAppException {
        return packFiles(collect(dir));
    }

    /** 0027: same packing for an in-memory file map (bundled apps read from the mod jar). */
    public static byte[] packFiles(Map<String, byte[]> input) throws VrAppException {
        Map<String, byte[]> files = new TreeMap<>(input);
        for (String name : files.keySet()) checkPath(name);
        build(files, null, 0); // validate before packing
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                ZipEntry e = new ZipEntry(f.getKey());
                e.setTime(FIXED_TIME);
                out.putNextEntry(e);
                out.write(f.getValue());
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new VrAppException(VrAppException.Code.IO, e.getMessage());
        }
        byte[] zip = bos.toByteArray();
        if (zip.length > MAX_PACKAGE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, zip.length + " > " + MAX_PACKAGE_BYTES + " bytes");
        return zip;
    }

    private static Map<String, byte[]> collect(Path dir) throws VrAppException {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new VrAppException(VrAppException.Code.NOT_FOUND, root.toString());
        Map<String, byte[]> files = new TreeMap<>();
        long[] total = {0};
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH + 1)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p)) continue;
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (isJunk(rel) || rel.endsWith(".vrapp")) continue;
                if (!allowedType(rel)) continue; // dev folders may hold sketches, .psd etc.: just not packed
                checkPath(rel);
                if (files.size() >= MAX_ENTRIES) throw new VrAppException(VrAppException.Code.TOO_MANY_FILES, "> " + MAX_ENTRIES);
                long size = Files.size(p);
                if (total[0] + size > MAX_UNPACKED_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, "unpacked > " + MAX_UNPACKED_BYTES);
                total[0] += size;
                files.put(rel, Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new VrAppException(VrAppException.Code.IO, e.getMessage());
        }
        return files;
    }

    private static VrAppPackage build(Map<String, byte[]> files, @Nullable byte[] raw, long total) throws VrAppException {
        byte[] cfg = files.get("config.json");
        if (cfg == null) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "config.json missing at the package root");
        Map<String, String> sources = new LinkedHashMap<>();
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            String name = f.getKey();
            if (!allowedType(name)) throw new VrAppException(VrAppException.Code.BAD_FILE_TYPE, name + " (allowed: " + TEXT_EXT + " anywhere, " + ASSET_EXT + " in assets/)");
            if (isText(name)) {
                if (f.getValue().length > MAX_TEXT_FILE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, name + " > " + MAX_TEXT_FILE_BYTES + " bytes");
                sources.put(name, utf8(name, f.getValue()));
            } else {
                assets.put(name, f.getValue());
            }
        }
        VrAppManifest manifest = VrAppManifest.parse(sources.get("config.json"));
        if (!sources.containsKey(manifest.main())) throw new VrAppException(VrAppException.Code.MISSING_MAIN, manifest.main());
        if (!manifest.icon().isEmpty() && !assets.containsKey(manifest.icon())) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "icon not in package: " + manifest.icon());
        ModuleBundler.bundle(sources, manifest.main()); // every import resolves, or the package is rejected now
        String sha = raw != null ? sha256(raw) : "";
        return new VrAppPackage(manifest, sources, assets, raw, sha, total);
    }

    /** {@code myapp/config.json, myapp/main.js} → {@code config.json, main.js} (GitHub "Source code (zip)"). */
    private static Map<String, byte[]> stripWrapper(Map<String, byte[]> files) {
        if (files.containsKey("config.json")) return files;
        String prefix = null;
        for (String name : files.keySet()) {
            int slash = name.indexOf('/');
            if (slash <= 0) return files;
            String p = name.substring(0, slash + 1);
            if (prefix == null) prefix = p;
            else if (!prefix.equals(p)) return files;
        }
        if (prefix == null || !files.containsKey(prefix + "config.json")) return files;
        Map<String, byte[]> out = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : files.entrySet()) out.put(f.getKey().substring(prefix.length()), f.getValue());
        return out;
    }

    private static byte[] inflate(InputStream in, String name, long budget) throws IOException, VrAppException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            n += r;
            if (n > budget) throw new VrAppException(VrAppException.Code.TOO_LARGE, "unpacked size over " + MAX_UNPACKED_BYTES + " bytes at " + name);
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    static void checkPath(String name) throws VrAppException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("//")) throw new VrAppException(VrAppException.Code.BAD_PATH, name);
        String[] parts = name.split("/");
        if (parts.length > MAX_DEPTH) throw new VrAppException(VrAppException.Code.BAD_PATH, "too deep: " + name);
        for (String s : parts) {
            if (s.equals(".") || s.equals("..") || !SEGMENT.matcher(s).matches()) throw new VrAppException(VrAppException.Code.BAD_PATH, name);
        }
    }

    /** OS metadata that zip tools add; never part of the app. */
    private static boolean isJunk(String name) {
        return name.startsWith("__MACOSX/") || name.endsWith(".DS_Store") || name.endsWith("Thumbs.db")
            || name.startsWith(".") || name.contains("/.");
    }

    private static boolean isText(String name) {
        return TEXT_EXT.stream().anyMatch(name::endsWith);
    }

    private static boolean allowedType(String name) {
        if (isText(name)) return true;
        return name.startsWith("assets/") && ASSET_EXT.stream().anyMatch(name::endsWith);
    }

    private static String utf8(String name, byte[] bytes) throws VrAppException {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return s.startsWith("\uFEFF") ? s.substring(1) : s; // BOM from Windows editors
        } catch (CharacterCodingException e) {
            throw new VrAppException(VrAppException.Code.BAD_FILE_TYPE, name + " is not UTF-8 text");
        }
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from the JVM", e);
        }
    }

    // --- use -------------------------------------------------------------------------------------

    /** One script for the engine (multi-file apps bundled, single-file apps unchanged). */
    public String script() throws VrAppException {
        return ModuleBundler.bundle(sources, manifest.main());
    }

    /** The runnable form ScriptHost/ScriptWindow already know. */
    public VrApp toVrApp() throws VrAppException {
        return new VrApp(manifest.id(), manifest.name(), manifest.version(), manifest.width(), manifest.height(), script(), manifest.keyboard());
    }

    public VrAppManifest manifest() { return manifest; }
    public Map<String, String> sources() { return sources; }
    public Map<String, byte[]> assets() { return assets; }
    /** Packed bytes; null for a dev folder. */
    @Nullable public byte[] raw() { return raw; }
    /** SHA-256 of {@link #raw()} (hex), empty for a dev folder. */
    public String sha256() { return sha256; }
    public long unpackedBytes() { return unpackedBytes; }
    public int packedBytes() { return raw == null ? 0 : raw.length; }

    @Nullable
    public byte[] icon() {
        return manifest.icon().isEmpty() ? null : assets.get(manifest.icon());
    }

    public List<String> files() {
        List<String> out = new ArrayList<>(sources.keySet());
        out.addAll(assets.keySet());
        return out;
    }
}
