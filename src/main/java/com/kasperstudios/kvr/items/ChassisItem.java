package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentTier;
import net.minecraft.world.item.Item;

/** The shell. Its tier is the ceiling for every module that can be installed (ADR-003 3.1). */
public class ChassisItem extends Item {

    private final ComponentTier tier;

    public ChassisItem(ComponentTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ComponentTier tier() {
        return tier;
    }
}
