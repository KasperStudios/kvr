package com.kasperstudios.kvr.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The online KVR Store index (SPEC 5.3, ADR-003 §5.1 phase 2): one JSON file in the GitHub repo
 * {@code kasper-studios/kvr-apps-index}. Authors open a PR that adds an entry; after the merge the app
 * shows up in every player's App Market.
 * <pre>{@code
 * {
 *   "format": 1,
 *   "apps": [{
 *     "id": "weather.station", "name": "Метеостанция", "version": "1.2.0", "author": "someone",
 *     "description": "Погода над базой",
 *     "url": "https://github.com/someone/kvr-weather/releases/download/v1.2.0/weather.vrapp",
 *     "sha256": "<64 hex>", "size": 18342,
 *     "permissions": ["network"], "requires": { "gpu": 2 }
 *   }]
 * }
 * }</pre>
 * The SHA-256 is the trust anchor: the package is downloaded from the author's repo, but only the exact
 * bytes reviewed in the index PR are accepted. Broken entries are skipped (and logged by the caller),
 * one bad PR must not empty the store for everyone.
 */
public final class StoreIndex {

    public static final int FORMAT = 1;
    public static final int MAX_INDEX_BYTES = 1024 * 1024;
    public static final int MAX_ENTRIES = 2000;
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
    /** Hosts a package may come from (GitHub release assets redirect to the objects/release-assets hosts). */
    public static final List<String> ALLOWED_HOSTS = List.of(
        "github.com", "raw.githubusercontent.com", "objects.githubusercontent.com",
        "release-assets.githubusercontent.com", "codeload.github.com");

    public record Entry(String id, String name, String version, String author, String description, String url,
                        String sha256, int size, List<String> permissions, VrAppManifest.Requirements requires) {}

    public record Parsed(List<Entry> entries, List<String> skipped) {}

    private StoreIndex() {}

    /** @param allowFiles also accept {@code file:} URLs (developer mode, local test index) */
    public static Parsed parse(String json, boolean allowFiles) throws VrAppException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "index is not a JSON object: " + e.getMessage());
        }
        int format = root.has("format") ? root.get("format").getAsInt() : 0;
        if (format != FORMAT) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "index format " + format + ", this KVR reads " + FORMAT);
        if (!root.has("apps") || !root.get("apps").isJsonArray()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "index has no apps[]");
        List<Entry> out = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        JsonArray apps = root.getAsJsonArray("apps");
        for (JsonElement el : apps) {
            if (out.size() >= MAX_ENTRIES) break;
            try {
                Entry e = entry(el.getAsJsonObject(), allowFiles);
                if (out.stream().anyMatch(o -> o.id().equals(e.id()))) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "duplicate id " + e.id());
                out.add(e);
            } catch (VrAppException | RuntimeException ex) {
                skipped.add(ex.getMessage());
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return new Parsed(List.copyOf(out), List.copyOf(skipped));
    }

    private static Entry entry(JsonObject o, boolean allowFiles) throws VrAppException {
        // the manifest parser validates id/name/permissions/requires exactly like config.json does
        VrAppManifest m = VrAppManifest.parse(o.toString());
        if (VrAppManifest.reserved(m.id())) throw new VrAppException(VrAppException.Code.RESERVED_ID, m.id());
        String url = o.has("url") ? o.get("url").getAsString().strip() : "";
        checkUrl(url, allowFiles);
        String sha = o.has("sha256") ? o.get("sha256").getAsString().strip().toLowerCase(Locale.ROOT) : "";
        if (!SHA.matcher(sha).matches()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, m.id() + ": sha256 must be 64 hex chars");
        int size = o.has("size") ? o.get("size").getAsInt() : -1;
        if (size <= 0 || size > VrAppPackage.MAX_PACKAGE_BYTES) throw new VrAppException(VrAppException.Code.TOO_LARGE, m.id() + ": size " + size);
        return new Entry(m.id(), m.name(), m.version(), m.author(), m.description(), url, sha, size, m.permissions(), m.requires());
    }

    /** https on a GitHub host; {@code file:} only in developer mode. */
    public static void checkUrl(String url, boolean allowFiles) throws VrAppException {
        URI u;
        try {
            u = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new VrAppException(VrAppException.Code.NOT_ALLOWED, "bad url " + url);
        }
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("file") && allowFiles) return;
        if (!scheme.equals("https")) throw new VrAppException(VrAppException.Code.NOT_ALLOWED, "only https: " + url);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(host)) throw new VrAppException(VrAppException.Code.NOT_ALLOWED, "host " + host + " is not a GitHub host");
    }

    /** "1.10.0" > "1.9.2"; non-numeric parts compare as text. Used for the "update" button. */
    public static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-+]");
        String[] y = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0";
            String q = i < y.length ? y[i] : "0";
            int c;
            try {
                c = Integer.compare(Integer.parseInt(p), Integer.parseInt(q));
            } catch (NumberFormatException e) {
                c = p.compareTo(q);
            }
            if (c != 0) return c;
        }
        return 0;
    }
}
