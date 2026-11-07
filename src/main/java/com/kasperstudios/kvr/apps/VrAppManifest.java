package com.kasperstudios.kvr.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@code config.json} of a {@code .vrapp} (CONCEPT §2.2):
 * <pre>{@code
 * {
 *   "id": "weather.station",          // [a-z0-9][a-z0-9._-]{1,39}: also the /data/<id>/ folder name
 *   "name": "Метеостанция",
 *   "version": "1.2.0",
 *   "author": "Kasper Studios",
 *   "description": "Погода над базой",
 *   "main": "main.js",                // entry module, default main.js
 *   "icon": "assets/icon.png",        // optional 16..256 px PNG, shown in the dock
 *   "window_opts": { "width": 300, "height": 200 },
 *   "keyboard": false,
 *   "permissions": ["network"],       // see AppPermissions
 *   "requires": { "gpu": 3, "ram": 3 } // minimum hardware tiers (CONCEPT: the browser needs T3+)
 * }
 * }</pre>
 * Pure Gson, no Minecraft classes — the server validates uploads with exactly this code.
 */
public record VrAppManifest(String id, String name, String version, String author, String description, String main,
                            String icon, int width, int height, boolean keyboard, List<String> permissions,
                            Requirements requires) {

    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,39}");
    /**
     * Ids of OS apps (jar bundle, dock entries) and names kept for them: a package can never shadow
     * Notes or Messenger. Kept here, not read from BuiltinApps, because the server must not load client classes.
     */
    public static final List<String> RESERVED_IDS = List.of(
        "hello", "kav", "notes", "calc", "messenger", "player", "ide", "settings", "market", "desktop",
        "calls", "files", "launcher", "system", "kvr");
    public static final int MIN_W = 120;
    public static final int MAX_W = 800;
    public static final int MIN_H = 80;
    public static final int MAX_H = 600;

    /** Minimum tier (1..5) of each module; 1 = no requirement. */
    public record Requirements(int cpu, int gpu, int ram, int storage) {
        public static final Requirements NONE = new Requirements(1, 1, 1, 1);

        /** First unmet requirement as {@code "gpu 3"}, empty when the build is good enough. */
        public Optional<String> unmet(int cpuTier, int gpuTier, int ramTier, int storageTier) {
            if (cpuTier < cpu) return Optional.of("cpu " + cpu);
            if (gpuTier < gpu) return Optional.of("gpu " + gpu);
            if (ramTier < ram) return Optional.of("ram " + ram);
            if (storageTier < storage) return Optional.of("storage " + storage);
            return Optional.empty();
        }

        public boolean any() {
            return cpu > 1 || gpu > 1 || ram > 1 || storage > 1;
        }
    }

    public static boolean reserved(String id) {
        return RESERVED_IDS.contains(id) || id.startsWith("kvr.") || id.startsWith("kvr-") || id.startsWith("kvr_");
    }

    public static VrAppManifest parse(String json) throws VrAppException {
        JsonObject c;
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "config.json is not an object");
            c = e.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "config.json: " + e.getMessage());
        }
        String id = str(c, "id", "");
        if (!ID.matcher(id).matches()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "id must match " + ID.pattern() + ": '" + id + "'");
        String name = limit(str(c, "name", id), 40);
        String version = limit(str(c, "version", "0.0.0"), 24);
        String author = limit(str(c, "author", ""), 40);
        String description = limit(str(c, "description", ""), 200);
        String main = str(c, "main", "main.js");
        if (!main.endsWith(".js")) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "main must be a .js file: " + main);
        String icon = str(c, "icon", "");
        if (!icon.isEmpty() && !icon.endsWith(".png")) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "icon must be a .png: " + icon);

        JsonObject win = c.has("window_opts") && c.get("window_opts").isJsonObject() ? c.getAsJsonObject("window_opts") : new JsonObject();
        int w = clamp(integer(win, "width", 300), MIN_W, MAX_W);
        int h = clamp(integer(win, "height", 200), MIN_H, MAX_H);
        boolean keyboard = c.has("keyboard") && c.get("keyboard").isJsonPrimitive() && c.get("keyboard").getAsBoolean();

        List<String> perms = new ArrayList<>();
        if (c.has("permissions")) {
            if (!c.get("permissions").isJsonArray()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "permissions must be an array");
            for (JsonElement p : c.getAsJsonArray("permissions")) {
                String s = p.isJsonPrimitive() ? p.getAsString() : "";
                if (!AppPermissions.known(s)) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "unknown permission '" + s + "' (known: " + AppPermissions.KNOWN + ")");
                if (!perms.contains(s)) perms.add(s);
            }
        }

        Requirements req = Requirements.NONE;
        if (c.has("requires") && c.get("requires").isJsonObject()) {
            JsonObject r = c.getAsJsonObject("requires");
            req = new Requirements(tier(r, "cpu"), tier(r, "gpu"), tier(r, "ram"), tier(r, "storage"));
        }
        return new VrAppManifest(id, name, version, author, description, main, icon, w, h, keyboard, List.copyOf(perms), req);
    }

    /** Compact JSON for the network (server LIST reply) — parse() accepts it back. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("name", name);
        o.addProperty("version", version);
        o.addProperty("author", author);
        o.addProperty("description", description);
        o.addProperty("main", main);
        if (!icon.isEmpty()) o.addProperty("icon", icon);
        JsonObject win = new JsonObject();
        win.addProperty("width", width);
        win.addProperty("height", height);
        o.add("window_opts", win);
        o.addProperty("keyboard", keyboard);
        JsonArray p = new JsonArray();
        permissions.forEach(p::add);
        o.add("permissions", p);
        JsonObject r = new JsonObject();
        r.addProperty("cpu", requires.cpu());
        r.addProperty("gpu", requires.gpu());
        r.addProperty("ram", requires.ram());
        r.addProperty("storage", requires.storage());
        o.add("requires", r);
        return o;
    }

    private static int tier(JsonObject o, String k) throws VrAppException {
        int t = integer(o, k, 1);
        if (t < 1 || t > 5) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "requires." + k + " must be 1..5, got " + t);
        return t;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString().strip() : def;
    }

    private static int integer(JsonObject o, String k, int def) throws VrAppException {
        if (!o.has(k)) return def;
        try {
            return o.get(k).getAsInt();
        } catch (RuntimeException e) {
            throw new VrAppException(VrAppException.Code.BAD_MANIFEST, k + " must be a number");
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String limit(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
