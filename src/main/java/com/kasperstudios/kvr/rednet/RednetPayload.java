package com.kasperstudios.kvr.rednet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Lua values (as CC: Tweaked hands them to Java) ⇄ JSON (what JS apps see). No CC classes here.
 *
 * <ul>
 *   <li>Lua → JS: table with keys exactly {@code 1..n} → array, any other table → object (keys as strings),
 *       number → number (integers stay integers), string, boolean, nil → null.</li>
 *   <li>JS → Lua: object → table, array → table {@code {[1]=…,[2]=…}}, number → Double (Lua has only numbers),
 *       null / undefined → nil (dropped from tables).</li>
 * </ul>
 * Limits: {@link #MAX_JSON} bytes of JSON, {@link #MAX_DEPTH} levels, {@link #MAX_NODES} values — a larger message
 * is dropped as a whole (never half-delivered). Same limits both ways, so a Lua script can't flood the glasses.
 */
public final class RednetPayload {

    public static final int MAX_JSON = 8192;
    static final int MAX_DEPTH = 16;
    static final int MAX_NODES = 1024;
    private static final double MAX_SAFE_INT = 9_007_199_254_740_991d; // 2^53 - 1, exact in a double

    /** Thrown when a value breaks the limits; carries no stack (it's an expected case). */
    public static final class TooBig extends Exception {
        TooBig() {
            super("rednet payload too big", null, false, false);
        }
    }

    private RednetPayload() {}

    // --- Lua → JSON ----------------------------------------------------------------------------

    public static JsonElement fromLua(@Nullable Object value) throws TooBig {
        JsonElement e = fromLua(value, 0, new int[] {MAX_NODES});
        if (e.toString().length() > MAX_JSON) throw new TooBig();
        return e;
    }

    private static JsonElement fromLua(@Nullable Object v, int depth, int[] budget) throws TooBig {
        if (--budget[0] < 0 || depth > MAX_DEPTH) throw new TooBig();
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Number n) return number(n.doubleValue());
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof byte[] bytes) return new JsonPrimitive(new String(bytes, StandardCharsets.ISO_8859_1));
        if (v instanceof ByteBuffer buf) {
            ByteBuffer b = buf.duplicate();
            byte[] bytes = new byte[b.remaining()];
            b.get(bytes);
            return new JsonPrimitive(new String(bytes, StandardCharsets.ISO_8859_1));
        }
        if (v instanceof Map<?, ?> map) {
            int n = sequenceLength(map);
            if (n > 0) {
                JsonArray arr = new JsonArray();
                for (int i = 1; i <= n; i++) arr.add(fromLua(lookup(map, i), depth + 1, budget));
                return arr;
            }
            JsonObject o = new JsonObject();
            for (Map.Entry<?, ?> en : map.entrySet()) {
                o.add(key(en.getKey()), fromLua(en.getValue(), depth + 1, budget));
            }
            return o;
        }
        return new JsonPrimitive(String.valueOf(v)); // unknown host object: its text, never the object itself
    }

    private static JsonElement number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return JsonNull.INSTANCE;
        if (d == Math.rint(d) && Math.abs(d) <= MAX_SAFE_INT) return new JsonPrimitive((long) d);
        return new JsonPrimitive(d);
    }

    /** n if the keys are exactly the integers 1..n, else 0. */
    private static int sequenceLength(Map<?, ?> map) {
        int n = map.size();
        if (n == 0) return 0;
        for (Object k : map.keySet()) {
            if (!(k instanceof Number num)) return 0;
            double d = num.doubleValue();
            if (d != Math.rint(d) || d < 1 || d > n) return 0;
        }
        return n; // n distinct integer keys within 1..n → exactly 1..n
    }

    @Nullable
    private static Object lookup(Map<?, ?> map, int i) {
        for (Map.Entry<?, ?> en : map.entrySet()) {
            if (en.getKey() instanceof Number num && num.doubleValue() == i) return en.getValue();
        }
        return null;
    }

    private static String key(@Nullable Object k) {
        if (k instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) <= MAX_SAFE_INT ? Long.toString((long) d) : Double.toString(d);
        }
        return String.valueOf(k);
    }

    // --- JSON → Lua ----------------------------------------------------------------------------

    @Nullable
    public static Object toLua(@Nullable JsonElement e) throws TooBig {
        return toLua(e, 0, new int[] {MAX_NODES});
    }

    @Nullable
    private static Object toLua(@Nullable JsonElement e, int depth, int[] budget) throws TooBig {
        if (--budget[0] < 0 || depth > MAX_DEPTH) throw new TooBig();
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return p.getAsDouble();
            return p.getAsString();
        }
        Map<Object, Object> table = new HashMap<>();
        if (e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                Object v = toLua(arr.get(i), depth + 1, budget);
                if (v != null) table.put((double) (i + 1), v);
            }
        } else {
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                Object v = toLua(en.getValue(), depth + 1, budget);
                if (v != null) table.put(en.getKey(), v);
            }
        }
        return table;
    }
}
