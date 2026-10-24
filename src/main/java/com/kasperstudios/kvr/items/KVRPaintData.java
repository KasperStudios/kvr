package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.GlassesPaint;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.DyedItemColor;
//?}
import net.minecraft.world.item.ItemStack;

/**
 * Chassis/glasses paint, stored in the vanilla place so any future renderer (and vanilla tooling) can read
 * it: {@code display.color} in item NBT before 1.20.5, the {@code dyed_color} component from 1.20.5 on.
 */
public final class KVRPaintData {

    private static final String NBT_COLOR = "color";

    private KVRPaintData() {
    }

    public static void paint(ItemStack stack, GlassesPaint paint) {
        //? if <1.20.5 {
        /*stack.getOrCreateTagElement("display").putInt(NBT_COLOR, paint.rgb());
        *///?} else {
        stack.set(DataComponents.DYED_COLOR, dyed(paint.rgb()));
        //?}
    }

    /** 1.21.2 dropped the {@code showInTooltip} flag from the dyed-colour component. */
    //? if >=1.20.5 {
    private static DyedItemColor dyed(int rgb) {
        //? if <1.21.2 {
        return new DyedItemColor(rgb, true);
        //?} else {
        /*return new DyedItemColor(rgb);
        *///?}
    }
    //?}

    /** Stored paint, or {@code null} when the stack is unpainted. */
    public static GlassesPaint paint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        int rgb;
        //? if <1.20.5 {
        /*if (!stack.hasTag() || !stack.getTag().contains("display")) {
            return null;
        }
        rgb = stack.getTagElement("display").getInt(NBT_COLOR);
        *///?} else {
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed == null) {
            return null;
        }
        rgb = dyed.rgb();
        //?}
        for (GlassesPaint paint : GlassesPaint.values()) {
            if (paint.rgb() == (rgb & 0xFFFFFF)) {
                return paint;
            }
        }
        return null;
    }

    /** Paints when the stack is a chassis or glasses item, returns {@code true} when it changed. */
    public static boolean paintIfPaintable(ItemStack stack, GlassesPaint paint) {
        if (stack.getItem() instanceof ChassisItem || stack.getItem() instanceof GlassesItem) {
            paint(stack, paint);
            return true;
        }
        return false;
    }

    /** Clears the paint (water bucket style clearing arrives with the crafting recipes). */
    public static void clear(ItemStack stack) {
        //? if <1.20.5 {
        /*if (stack.getTagElement("display") != null) {
            stack.getTagElement("display").remove(NBT_COLOR);
        }
        *///?} else {
        stack.remove(DataComponents.DYED_COLOR);
        //?}
    }
}
