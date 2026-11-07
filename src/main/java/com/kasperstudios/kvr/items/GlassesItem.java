package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.GlassesBuild;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Assembled KVR glasses.
 *
 * <p>Shift + right click disassembles them: the chassis and all five modules come back unchanged,
 * which is the contract {@link GlassesBuild} already guarantees on the data side.
 */
public class GlassesItem extends com.kasperstudios.kvr.glasses.WearableGlassesItem {

    public GlassesItem(Properties properties) {
        super(properties);
    }

    /** Item stack carrying the given build. */
    public static ItemStack of(GlassesBuild build) {
        ItemStack stack = new ItemStack(KVRContent.glasses());
        KVRGlassesData.write(stack, build);
        return stack;
    }

    // 1.21.2 changed Item#use to return InteractionResult instead of a holder of the stack
    //? if <1.21.2 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            InteractionResult result = disassemble(level, player, hand);
            return new InteractionResultHolder<>(result, player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }
    //?} else {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            return disassemble(level, player, hand);
        }
        return super.use(level, player, hand);
    }
    *///?}

    /** 1.21.11 turned {@code Level.isClientSide} into a method. */
    private static boolean isClientSide(Level level) {
        //? if >=1.21.11 {
        /*return level.isClientSide();
        *///?} else {
        return level.isClientSide;
        //?}
    }

    /** 1.21.11 replaced {@code sidedSuccess(boolean)} with the {@code SUCCESS} constant. */
    private static InteractionResult success(boolean clientSide) {
        //? if >=1.21.11 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(clientSide);
        //?}
    }

    private static InteractionResult disassemble(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        GlassesBuild build = KVRGlassesData.read(stack);
        if (build == null) {
            return InteractionResult.PASS;
        }
        if (!isClientSide(level)) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(KVRContent.chassis(build.chassisTier())));
            for (Map.Entry<ComponentType, com.kasperstudios.kvr.hardware.ComponentTier> part : build.parts()) {
                player.getInventory().placeItemBackInInventory(
                        new ItemStack(KVRContent.component(part.getKey(), part.getValue())));
            }
            stack.shrink(1);
        }
        return success(isClientSide(level));
    }
}
