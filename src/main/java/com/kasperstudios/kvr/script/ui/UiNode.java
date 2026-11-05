package com.kasperstudios.kvr.script.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/**
 * The widget tree an app commits from JS ({@code kvr.ui}: Window / Text / Button / Slider / Stack),
 * parsed off the render thread and swapped in atomically. The root carries window options
 * (title, width, height, style).
 */
public record UiNode(int id, String type, JsonObject props, List<UiNode> children) {

    private static final int MAX_NODES = 512;

    public static UiNode parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int[] budget = {MAX_NODES};
        return node(root, "window", budget);
    }

    private static UiNode node(JsonObject o, String fallbackType, int[] budget) {
        budget[0]--;
        List<UiNode> kids = new ArrayList<>();
        if (o.has("children") && o.get("children").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("children")) {
                if (budget[0] <= 0) break;
                if (e.isJsonObject()) kids.add(node(e.getAsJsonObject(), "text", budget));
            }
        }
        JsonObject props = o.deepCopy();
        props.remove("children");
        int id = o.has("id") ? o.get("id").getAsInt() : 0;
        String type = o.has("type") ? o.get("type").getAsString() : fallbackType;
        return new UiNode(id, type, props, List.copyOf(kids));
    }

    public String str(String key, String def) {
        return props.has(key) && props.get(key).isJsonPrimitive() ? props.get(key).getAsString() : def;
    }

    public double num(String key, double def) {
        return props.has(key) && props.get(key).isJsonPrimitive() ? props.get(key).getAsDouble() : def;
    }

    public boolean bool(String key) {
        return props.has(key) && props.get(key).isJsonPrimitive() && props.get(key).getAsBoolean();
    }

    public JsonObject obj(String key) {
        return props.has(key) && props.get(key).isJsonObject() ? props.getAsJsonObject(key) : new JsonObject();
    }
}
