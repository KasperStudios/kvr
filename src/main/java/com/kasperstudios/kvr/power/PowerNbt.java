package com.kasperstudios.kvr.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * NBT seams for the power data: where it lives on the stack ({@code custom_data} component from
 * 1.20.5, plain stack tag before) and the 1.21.5 switch of {@code CompoundTag} getters to
 * Optional / {@code ...Or(key, default)}. Using {@code custom_data} avoids registering a new
 * DataComponentType just for this.
 */
public final class PowerNbt {

    static final String KEY = "kvr_power";

    private PowerNbt() {}

    /** Any KVR compound stored on a stack (power, VFS id, ...). */
    public static CompoundTag readKey(ItemStack stack, String key) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : getCompound(data.copyTag(), key);
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : getCompound(tag, key);
        *///?}
    }

    public static void writeKey(ItemStack stack, String key, CompoundTag value) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, all -> all.put(key, value));
        //?} else {
        /*stack.getOrCreateTag().put(key, value);
        *///?}
    }

    static CompoundTag read(ItemStack stack) {
        return readKey(stack, KEY);
    }

    static void write(ItemStack stack, CompoundTag power) {
        writeKey(stack, KEY, power);
    }

    static CompoundTag getCompound(CompoundTag t, String k) {
        //? if >=1.21.5 {
        /*return t.getCompoundOrEmpty(k);
        *///?} else {
        return t.getCompound(k);
        //?}
    }

    static double getDouble(CompoundTag t, String k, double def) {
        //? if >=1.21.5 {
        /*return t.getDoubleOr(k, def);
        *///?} else {
        return t.contains(k) ? t.getDouble(k) : def;
        //?}
    }

    static long getLong(CompoundTag t, String k, long def) {
        //? if >=1.21.5 {
        /*return t.getLongOr(k, def);
        *///?} else {
        return t.contains(k) ? t.getLong(k) : def;
        //?}
    }

    public static String getString(CompoundTag t, String k, String def) {
        //? if >=1.21.5 {
        /*return t.getStringOr(k, def);
        *///?} else {
        return t.contains(k) ? t.getString(k) : def;
        //?}
    }
}
