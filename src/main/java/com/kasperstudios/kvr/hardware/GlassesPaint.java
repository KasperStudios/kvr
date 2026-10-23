package com.kasperstudios.kvr.hardware;

/**
 * The 16 vanilla dye colours a chassis can be painted with (ADR-003 3.1). Values are the vanilla dye
 * texture tints, so the painted model matches the dye the player used.
 */
public enum GlassesPaint {
    WHITE("white", 0xF9FFFE),
    ORANGE("orange", 0xF9801D),
    MAGENTA("magenta", 0xC74EBD),
    LIGHT_BLUE("light_blue", 0x3AB3DA),
    YELLOW("yellow", 0xFED83D),
    LIME("lime", 0x80C71F),
    PINK("pink", 0xF38BAA),
    GRAY("gray", 0x474F52),
    LIGHT_GRAY("light_gray", 0x9D9D97),
    CYAN("cyan", 0x169C9C),
    PURPLE("purple", 0x8932B8),
    BLUE("blue", 0x3C44AA),
    BROWN("brown", 0x835432),
    GREEN("green", 0x5E7C16),
    RED("red", 0xB02E26),
    BLACK("black", 0x1D1D21);

    private final String dyeId;
    private final int rgb;

    GlassesPaint(String dyeId, int rgb) {
        this.dyeId = dyeId;
        this.rgb = rgb;
    }

    /** Vanilla dye id fragment, e.g. {@code light_blue}. */
    public String dyeId() {
        return dyeId;
    }

    public int rgb() {
        return rgb;
    }

    /** Fully opaque ARGB, the format the item colour component expects. */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    public static GlassesPaint byDyeId(String dyeId) {
        for (GlassesPaint paint : values()) {
            if (paint.dyeId.equals(dyeId)) {
                return paint;
            }
        }
        throw new IllegalArgumentException("unknown vanilla dye '" + dyeId + "'");
    }
}
