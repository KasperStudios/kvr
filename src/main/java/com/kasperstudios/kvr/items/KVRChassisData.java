package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.GlassesCodec;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
//?}
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A chassis mid-assembly: some of the five module slots filled, none duplicate.
 *
 * <p>Stored the same way as the finished build (six ints), with 0 meaning "slot still empty", so the
 * same NBT/data-component seam works on every target version.
 */
public final class KVRChassisData {

    private static final String TAG = "kvr:parts";
    private static final int SLOTS = 1 + ComponentType.values().length;

    private KVRChassisData() {
    }

    /** Modules currently inside the chassis, or {@code null} on a stack that is not a chassis. */
    public static PartialBuild read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof ChassisItem)) {
            return null;
        }
        int[] data = rawData(stack);
        if (data == null || data.length != SLOTS) {
            return new PartialBuild();
        }
        PartialBuild partial = new PartialBuild();
        for (ComponentType type : ComponentType.values()) {
            int level = data[1 + type.ordinal()];
            if (level > 0) {
                partial.set(type, ComponentTier.byLevel(level));
            }
        }
        return partial;
    }

    public static void write(ItemStack stack, PartialBuild partial) {
        int[] data = new int[SLOTS];
        for (Map.Entry<ComponentType, ComponentTier> entry : partial.tiers().entrySet()) {
            data[1 + entry.getKey().ordinal()] = entry.getValue().level();
        }
        //? if <1.20.5 {
        /*stack.getOrCreateTag().putIntArray(TAG, data);
        *///?} else {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(TAG, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
    }

    /** Removes the partial build (all modules taken back out). */
    public static void clear(ItemStack stack) {
        //? if <1.20.5 {
        /*if (stack.getTag() != null) {
            stack.getTag().remove(TAG);
        }
        *///?} else {
        stack.remove(DataComponents.CUSTOM_DATA);
        //?}
    }

    private static int[] rawData(ItemStack stack) {
        //? if <1.20.5 {
        /*return stack.getTag() == null ? null : stack.getTag().getIntArray(TAG);
        *///?} else if >=1.21.11 {
        /*CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag().getIntArray(TAG).orElse(null);
        *///?} else {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        return custom == null ? null : custom.copyTag().getIntArray(TAG);
        //?}
    }

    /** Immutable snapshot of a partially assembled chassis. */
    public static final class PartialBuild {
        private final Map<ComponentType, ComponentTier> tiers = new EnumMap<>(ComponentType.class);

        public Map<ComponentType, ComponentTier> tiers() {
            return new EnumMap<>(tiers);
        }

        public ComponentTier tierOf(ComponentType type) {
            return tiers.get(type);
        }

        public void set(ComponentType type, ComponentTier tier) {
            tiers.put(type, tier);
        }

        public boolean isEmpty() {
            return tiers.isEmpty();
        }

        public Module removeLast() {
            List<ComponentType> order = new ArrayList<>(tiers.keySet());
            ComponentType type = order.get(order.size() - 1);
            ComponentTier tier = tiers.remove(type);
            return new Module(type, tier);
        }

        public record Module(ComponentType type, ComponentTier tier) {
        }
    }
}
