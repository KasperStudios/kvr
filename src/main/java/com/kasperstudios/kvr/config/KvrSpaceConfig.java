package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.KVR;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * 0033 — Virtual Spaces + AR vision (ADR-001 gesture 4). Client-only, {@code config/kvr-space.toml}, same flat
 * TOML subset and helpers as {@link KvrConfig}. A separate file on purpose: {@link KvrUiConfig.Prefs} is a record
 * the repo may have grown, adding components to it would break callers.
 *
 * <p>The directory follows {@link KvrConfig#directory()}, so no extra loader wiring is needed.
 */
public final class KvrSpaceConfig {

    /** What gesture 4 does: open the slot picker or step to the next slot (World → 1 → 2 → … → World). */
    public enum GestureMode { PICKER, CYCLE }

    /** Cocoon colours approved by the Creator; CUSTOM uses {@code cocoon_custom_hex}. */
    public enum CocoonColor {
        CYAN(0x00E5FF), PURPLE(0xB026FF), EMERALD(0x2EE59D), AMBER(0xFFB020), CUSTOM(-1);

        public final int rgb;

        CocoonColor(int rgb) {
            this.rgb = rgb;
        }

        public String langKey() {
            return "kvr.settings.cocoon.color." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int SLOTS_MIN = 2;
    public static final int SLOTS_MAX = 5;
    public static final int OPACITY_MIN = 5;
    public static final int OPACITY_MAX = 100;
    public static final int INSPECTOR_OFFSET_MIN = 0;
    public static final int INSPECTOR_OFFSET_MAX = 100;

    /** 0034a: where the Block Inspector plate floats. */
    public enum InspectorPosition {
        /** Floating above the block, {@code inspector_offset_cm} over its top. */
        TOP,
        /** On the block centre, pulled towards the eye so it is not inside the block. */
        CENTER,
        /** Flat widget in the top-left screen corner (HUD), with the item icon. */
        HUD;

        public String langKey() {
            return "kvr.settings.inspector." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param slots      number of virtual spaces besides the real world
     * @param cocoonRgb  resolved 0xRRGGBB (custom hex already parsed)
     * @param opacityPct 5..100, brightness of the cocoon neon (0034: the shell itself is always opaque)
     */
    public record Prefs(int slots, GestureMode gesture,
                        boolean cocoon, CocoonColor color, int cocoonRgb, String customHex, int opacityPct,
                        boolean arRedstone, boolean arRednet, boolean arMobs,
                        boolean arInspector, InspectorPosition inspectorPosition, int inspectorOffsetCm) {}

    private static final String FILE = "kvr-space.toml";
    private static final String DEFAULTS = """
        # KVR Spatial OS - virtual spaces, AR cocoon and AR vision (in-visor Settings app writes this file)
        # Virtual spaces besides the real world (2..5)
        space_slots = 3
        # Gesture 4: picker | cycle
        space_gesture = "picker"
        # Cocoon around the player while a virtual space is active
        cocoon_enabled = true
        # cyan | purple | emerald | amber | custom
        cocoon_color = "cyan"
        cocoon_custom_hex = "#00E5FF"
        # 5..100 % - neon brightness (the shell is always opaque)
        cocoon_opacity = 45
        # AR vision: always on while the glasses are worn and lit
        ar_redstone = true
        ar_rednet = true
        ar_mobs = true
        # Block Inspector: outline + info plate for the block you look at
        ar_inspector = true
        # top | center | hud
        inspector_position = "top"
        # TOP only: plate height over the block top, cm (0..100)
        inspector_offset_cm = 35
        """;

    private static Prefs prefs;

    private KvrSpaceConfig() {}

    public static synchronized Prefs get() {
        if (prefs == null) {
            Map<String, String> m = load();
            CocoonColor color = enumOf(CocoonColor.class, KvrConfig.str(m, "cocoon_color"), CocoonColor.CYAN);
            String hex = KvrConfig.str(m, "cocoon_custom_hex");
            int rgb = color == CocoonColor.CUSTOM ? parseHex(hex, CocoonColor.CYAN.rgb) : color.rgb;
            prefs = new Prefs(
                KvrConfig.clampInt(m, "space_slots", 3, SLOTS_MIN, SLOTS_MAX),
                enumOf(GestureMode.class, KvrConfig.str(m, "space_gesture"), GestureMode.PICKER),
                KvrConfig.bool(m, "cocoon_enabled", true),
                color, rgb, hex,
                KvrConfig.clampInt(m, "cocoon_opacity", 45, OPACITY_MIN, OPACITY_MAX),
                KvrConfig.bool(m, "ar_redstone", true),
                KvrConfig.bool(m, "ar_rednet", true),
                KvrConfig.bool(m, "ar_mobs", true),
                KvrConfig.bool(m, "ar_inspector", true),
                enumOf(InspectorPosition.class, KvrConfig.str(m, "inspector_position"), InspectorPosition.TOP),
                KvrConfig.clampInt(m, "inspector_offset_cm", 35, INSPECTOR_OFFSET_MIN, INSPECTOR_OFFSET_MAX));
        }
        return prefs;
    }

    /** Writes one key ({@code Boolean}, {@code Integer}, {@code String} or an enum) and reloads on next {@link #get()}. */
    public static synchronized void update(String key, Object value) {
        Object v = value instanceof Enum<?> e ? e.name().toLowerCase(Locale.ROOT) : value;
        Path dir = KvrConfig.directory();
        Path p = dir.resolve(FILE);
        try {
            String text = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : DEFAULTS;
            Files.createDirectories(dir);
            Files.writeString(p, KvrConfig.withValue(text, key, KvrConfig.literal(v)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} not writable: {}", p, e.toString());
        }
        prefs = null;
    }

    /** {@code "#RRGGBB"} / {@code "RRGGBB"} / {@code "#RGB"} → 0xRRGGBB, else {@code def}. Pure. */
    public static int parseHex(String raw, int def) {
        if (raw == null) return def;
        String s = raw.strip();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        if (s.length() != 6) return def;
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** True when {@link #parseHex} accepts the text (Settings shows the field red otherwise). */
    public static boolean validHex(String raw) {
        return parseHex(raw, -1) != -1;
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static Map<String, String> load() {
        Path dir = KvrConfig.directory();
        Path p = dir.resolve(FILE);
        try {
            if (!Files.exists(p)) {
                Files.createDirectories(dir);
                Files.writeString(p, DEFAULTS, StandardCharsets.UTF_8);
            }
            Map<String, String> m = KvrConfig.parse(DEFAULTS);
            m.putAll(KvrConfig.parse(Files.readString(p, StandardCharsets.UTF_8)));
            return m;
        } catch (IOException e) {
            KVR.logger().warn("KVR config {} unreadable, using defaults: {}", p, e.toString());
            return KvrConfig.parse(DEFAULTS);
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String raw, E def) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
