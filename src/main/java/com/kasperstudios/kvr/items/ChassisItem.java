package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.menu.ChassisAssemblyMenu;
import net.minecraft.network.chat.Component;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The shell. Its tier caps every module that can be installed (ADR-003 3.1). */
public class ChassisItem extends Item {

    private final ComponentTier tier;

    public ChassisItem(ComponentTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ComponentTier tier() {
        return tier;
    }

    // 1.21.2 changed Item#use to return InteractionResult instead of a holder of the stack
    //? if <1.21.2 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = openAssembly(level, player);
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
    //?} else {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return openAssembly(level, player);
    }
    *///?}

    private static InteractionResult openAssembly(Level level, Player player) {
        if (!isClientSide(level)) {
            player.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, ignored) -> new ChassisAssemblyMenu(windowId, inventory),
                    Component.literal("Сборка KVR")));
        }
        return success(isClientSide(level));
    }

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
}
