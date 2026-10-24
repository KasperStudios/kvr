package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.menu.ChassisAssemblyMenu;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The shell. Its tier caps every module that can be installed (ADR-003 3.1).
 *
 * <p>The chassis is itself the assembly container, exactly like a bundle: a module on the cursor
 * clicked into the chassis stacks it inside; when all five module types are present the stack itself
 * becomes the assembled glasses. No crafting menu, no dedicated slots - the item the player is
 * holding is the GUI.
 */
public class ChassisItem extends Item {

    private final ComponentTier tier;

    /** Opens the chassis' own five-slot assembly screen; the item is the inventory. */
    // 1.21.2 changed Item#use to return InteractionResult instead of a holder of the stack
    //? if <1.21.2 {
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        openScreen(level, player);
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.PASS, player.getItemInHand(hand));
    }
    //?} else {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        openScreen(level, player);
        return InteractionResult.PASS;
    }
    *///?}

    private static void openScreen(Level level, Player player) {
        // 1.21.11 turned Level.isClientSide into a method
        //? if >=1.21.11 {
        /*if (level.isClientSide()) {
            return;
        }
        *///?} else {
        if (level.isClientSide) {
            return;
        }
        //?}
        com.kasperstudios.kvr.platform.KVRRegistryProvider.get().openMenu(
                player, com.kasperstudios.kvr.core.KVRIds.id("chassis_assembly").toString(),
                net.minecraft.network.chat.Component.literal("Сборка KVR"), ChassisAssemblyMenu::forPlayer);
    }

    public ChassisItem(ComponentTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ComponentTier tier() {
        return tier;
    }
}
