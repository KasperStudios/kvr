package com.kasperstudios.kvr.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A runnable app. 5a: {@code config.json} + single-file {@code main.js}; the full .vrapp format
 * (components.js, styles.js, assets/, zip) arrives in 5d. {@code "keyboard": true} in config.json: the
 * window captures the keyboard in Alt mode while hovered; the script gets {@code kvr.on('key'|'char', e => ...)}.
 */
public record VrApp(String id, String name, String version, int width, int height, String main, boolean keyboard) {

    public static VrApp parse(String configJson, String mainJs) {
        JsonObject c = JsonParser.parseString(configJson).getAsJsonObject();
        JsonObject win = c.has("window_opts") ? c.getAsJsonObject("window_opts") : new JsonObject();
        return new VrApp(
            str(c, "id", "app"),
            str(c, "name", "App"),
            str(c, "version", "0.0.0"),
            win.has("width") ? win.get("width").getAsInt() : 300,
            win.has("height") ? win.get("height").getAsInt() : 200,
            mainJs,
            c.has("keyboard") && c.get("keyboard").getAsBoolean());
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }
}
