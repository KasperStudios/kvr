package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.GlassesBuild;
import com.kasperstudios.kvr.hardware.GlassesCodec;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
//?}
import net.minecraft.world.item.ItemStack;

/**
 * Stores the assembled build on the glasses stack.
 *
 * <p>Only six ints travel with the item ({@link GlassesCodec}), and only the container differs between
 * versions: item NBT before 1.20.5, a data component from 1.20.5 on. From 1.21.11 the tag getters return
 * {@code Optional}, which is why reading is branched three ways.
 */
public final class KVRGlassesData {

    private static final String TAG = "kvr:glasses";

    private KVRGlassesData() {
    }

    public static void write(ItemStack stack, GlassesBuild build) {
        int[] data = GlassesCodec.toInts(build);
        //? if <1.20.5 {
        /*stack.getOrCreateTag().putIntArray(TAG, data);
        *///?} else {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(TAG, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?}
    }

    /** Stored build, or {@code null} when the stack carries none or it no longer validates. */
    public static GlassesBuild read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        int[] data = rawData(stack);
        return GlassesCodec.isValid(data) ? GlassesCodec.fromInts(data) : null;
    }

    public static boolean hasBuild(ItemStack stack) {
        return read(stack) != null;
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
}
