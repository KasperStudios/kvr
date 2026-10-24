package com.kasperstudios.kvr.hardware;

import com.kasperstudios.kvr.items.ChassisDyeRecipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: painting chassis and glasses via crafting table dye mechanics.
 */
class ChassisDyeRecipeTest {

    @Test
    void verifiesPaintValues() {
        assertEquals(16, GlassesPaint.values().length);
        assertEquals(0xF9FFFE, GlassesPaint.WHITE.rgb());
        assertEquals("light_blue", GlassesPaint.LIGHT_BLUE.dyeId());
    }

    @Test
    void matchesVanillaDyeNames() {
        for (GlassesPaint paint : GlassesPaint.values()) {
            assertNotNull(GlassesPaint.byDyeId(paint.dyeId()));
        }
    }
}
