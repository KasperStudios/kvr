package com.kasperstudios.kvr.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KVR built-in catalog (0027, Creator: clean glasses ship with exactly Market, Messenger and Settings — every
 * other app is installed from App Market). The apps live in the mod jar under {@code assets/kvr/apps/<id>/},
 * listed in {@code assets/kvr/apps/index.json} with all their files.
 *
 * <p>Read from the CLASSPATH, not the resource manager: the dedicated server has no client resources, and a
 * resource pack must not be able to swap the code the server installs. The server packs the package itself
 * ({@code AppsProtocol.Op.INSTALL_BUNDLED}), so official ids (reserved for uploads) can only ever hold this code.
 */
public final class BundledApps {

    public record Entry(String id, List<String> files) {}

    private static final String ROOT = "/assets/kvr/apps/";
    private static List<Entry> index;

    private BundledApps() {}

    public static synchronized List<Entry> entries() {
        if (index == null) index = List.copyOf(readIndex());
        return index;
    }

    public static boolean isBundled(String id) {
        return entries().stream().anyMatch(e -> e.id().equals(id));
    }

    public static Optional<VrAppManifest> manifest(String id) {
        return MANIFESTS.computeIfAbsent(id, BundledApps::loadManifest);
    }

    private static final Map<String, Optional<VrAppManifest>> MANIFESTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Optional<VrAppManifest> loadManifest(String id) {
        try {
            byte[] cfg = read(id, "config.json");
            return Optional.of(VrAppManifest.parse(new String(cfg, StandardCharsets.UTF_8)));
        } catch (VrAppException e) {
            KVR.logger().warn("KVR bundled app {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /** The app as a {@code .vrapp}, validated like any upload. */
    public static byte[] pack(String id) throws VrAppException {
        Entry e = entries().stream().filter(x -> x.id().equals(id)).findFirst()
            .orElseThrow(() -> new VrAppException(VrAppException.Code.NOT_FOUND, "not a bundled app: " + id));
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String f : e.files()) files.put(f, read(id, f));
        byte[] zip = VrAppPackage.packFiles(files);
        VrAppManifest m = VrAppPackage.read(zip).manifest();
        if (!m.id().equals(id)) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "bundled " + id + " declares id " + m.id());
        return zip;
    }

    private static byte[] read(String id, String file) throws VrAppException {
        VrAppPackage.checkPath(file);
        try (InputStream in = BundledApps.class.getResourceAsStream(ROOT + id + "/" + file)) {
            if (in == null) throw new VrAppException(VrAppException.Code.NOT_FOUND, ROOT + id + "/" + file);
            byte[] data = in.readNBytes(VrAppPackage.MAX_TEXT_FILE_BYTES + 1);
            if (data.length > VrAppPackage.MAX_TEXT_FILE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, file);
            return data;
        } catch (IOException ex) {
            throw new VrAppException(VrAppException.Code.IO, ex.getMessage());
        }
    }

    private static List<Entry> readIndex() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = BundledApps.class.getResourceAsStream(ROOT + "index.json")) {
            if (in == null) {
                KVR.logger().warn("KVR: {}index.json missing — no built-in catalog", ROOT);
                return out;
            }
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray apps = root.getAsJsonArray("apps");
            for (JsonElement el : apps) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                if (!VrAppManifest.ID.matcher(id).matches()) continue;
                List<String> files = new ArrayList<>();
                for (JsonElement f : o.getAsJsonArray("files")) files.add(f.getAsString());
                out.add(new Entry(id, List.copyOf(files)));
            }
        } catch (IOException | RuntimeException e) {
            KVR.logger().warn("KVR: built-in catalog unreadable: {}", e.toString());
        }
        return out;
    }
}
