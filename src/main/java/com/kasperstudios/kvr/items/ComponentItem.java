package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.HardwareSpec;
import com.kasperstudios.kvr.hardware.HardwareTable;
import net.minecraft.world.item.Item;

/**
 * One hardware module. Type and tier are baked into the item identity (25 distinct items), so a module
 * never needs NBT or data components to know what it is - which keeps it identical on every version.
 */
public class ComponentItem extends Item {

    private final ComponentType type;
    private final ComponentTier tier;

    public ComponentItem(ComponentType type, ComponentTier tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public ComponentType componentType() {
        return type;
    }

    public ComponentTier tier() {
        return tier;
    }

    public HardwareSpec spec() {
        return HardwareTable.of(type, tier);
    }
}
