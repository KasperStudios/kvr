package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.GlassesPaint;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

//? if <1.20.5 {
/*import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.core.RegistryAccess;
*///?} else {
import net.minecraft.world.item.crafting.CraftingInput;
//?}

/**
 * Special recipe: combines any chassis or glasses item with a vanilla dye to paint it.
 * Modelled on vanilla {@code ArmorDyeRecipe}.
 */
public class ChassisDyeRecipe extends CustomRecipe {

    public static RecipeSerializer<ChassisDyeRecipe> SERIALIZER;

    //? if <1.20.4 {
    /*public ChassisDyeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }
    *///?} else {
    public ChassisDyeRecipe(CraftingBookCategory category) {
        super(category);
    }
    //?}

    public static RecipeSerializer<ChassisDyeRecipe> createSerializer() {
        //? if <1.20.4 {
        /*return new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(ChassisDyeRecipe::new);
        *///?} else if <1.21.11 {
        return new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(ChassisDyeRecipe::new);
        //?} else {
        /*return new net.minecraft.world.item.crafting.CustomRecipe.Serializer<>(ChassisDyeRecipe::new);
        *///?}
    }

    //? if <1.20.5 {
    /*@Override
    public boolean matches(CraftingContainer container, Level level) {
        ItemStack paintable = ItemStack.EMPTY;
        ItemStack dye = ItemStack.EMPTY;
        int count = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            count++;
            if (isPaintable(stack)) {
                if (!paintable.isEmpty()) return false;
                paintable = stack;
            } else if (stack.getItem() instanceof DyeItem) {
                if (!dye.isEmpty()) return false;
                dye = stack;
            } else {
                return false;
            }
        }
        return count == 2 && !paintable.isEmpty() && !dye.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack paintable = ItemStack.EMPTY;
        DyeItem dyeItem = null;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (isPaintable(stack)) {
                paintable = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyeItem = dye;
            }
        }

        if (paintable.isEmpty() || dyeItem == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = paintable.copy();
        result.setCount(1);
        GlassesPaint paint = GlassesPaint.byDyeId(dyeItem.getDyeColor().getName());
        KVRPaintData.paint(result, paint);
        return result;
    }
    *///?} else {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack paintable = ItemStack.EMPTY;
        ItemStack dye = ItemStack.EMPTY;
        int count = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            count++;
            if (isPaintable(stack)) {
                if (!paintable.isEmpty()) return false;
                paintable = stack;
            } else if (stack.getItem() instanceof DyeItem) {
                if (!dye.isEmpty()) return false;
                dye = stack;
            } else {
                return false;
            }
        }
        return count == 2 && !paintable.isEmpty() && !dye.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        ItemStack paintable = ItemStack.EMPTY;
        DyeItem dyeItem = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (isPaintable(stack)) {
                paintable = stack;
            } else if (stack.getItem() instanceof DyeItem dye) {
                dyeItem = dye;
            }
        }

        if (paintable.isEmpty() || dyeItem == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = paintable.copy();
        result.setCount(1);
        GlassesPaint paint = GlassesPaint.byDyeId(dyeItem.getDyeColor().getName());
        KVRPaintData.paint(result, paint);
        return result;
    }
    //?}

    private static boolean isPaintable(ItemStack stack) {
        return stack.getItem() instanceof ChassisItem || stack.getItem() instanceof GlassesItem;
    }

    //? if <1.21.11 {
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
    //?} else {
    /*@Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
    *///?}
}
