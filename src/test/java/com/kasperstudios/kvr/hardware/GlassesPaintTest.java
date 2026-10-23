package com.kasperstudios.kvr.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Seam: chassis painting - the 16 vanilla dye colours and their RGB values rendered on the model. */
class GlassesPaintTest {

    @Test
    void hasExactlyTheSixteenVanillaDyes() {
        assertEquals(16, GlassesPaint.values().length);
    }

    @Test
    void rgbIsPackedAndOpaque() {
        assertEquals(0xF9FFFE, GlassesPaint.WHITE.rgb());
        assertEquals(0xB02E26, GlassesPaint.RED.rgb());
        for (GlassesPaint paint : GlassesPaint.values()) {
            assertEquals(0xFF, (paint.argb() >>> 24) & 0xFF, paint + " must be fully opaque");
            assertEquals(paint.rgb(), paint.argb() & 0xFFFFFF);
        }
    }

    @Test
    void dyesAreAddressableByVanillaId() {
        assertEquals(GlassesPaint.LIGHT_BLUE, GlassesPaint.byDyeId("light_blue"));
        assertEquals("light_blue", GlassesPaint.LIGHT_BLUE.dyeId());
        assertThrows(IllegalArgumentException.class, () -> GlassesPaint.byDyeId("rainbow"));
    }
}
