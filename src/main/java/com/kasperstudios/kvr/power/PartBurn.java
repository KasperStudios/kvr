package com.kasperstudios.kvr.power;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * What survives a detonation (SPEC 2.8: glasses destroyed, each part burns with 50%).
 *
 * <p>Interpretation for the Creator to confirm: the chassis goes with the glasses and the battery is
 * the cell that blew up (capacity already degraded to 0), so only CPU, GPU, RAM and Storage roll.
 */
public final class PartBurn {

    /** Disassembly order used by {@link GlassesPower#installDisassembler}. */
    public static final int CHASSIS = 0, CPU = 1, GPU = 2, RAM = 3, STORAGE = 4, BATTERY = 5;

    private PartBurn() {}

    /** @param random uniform 0..1; a part survives when the roll is at or above the burn chance */
    public static <T> List<T> survivors(List<T> parts, DoubleSupplier random) {
        List<T> out = new ArrayList<>();
        for (int i = CPU; i <= STORAGE && i < parts.size(); i++) {
            if (random.getAsDouble() >= PowerTable.PART_BURN_CHANCE) out.add(parts.get(i));
        }
        return out;
    }
}
