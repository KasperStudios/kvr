package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.menu.UpgradeMenu;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The tier upgrader template doubles as the key to the upgrade station. */
public class TierUpgraderItem extends Item {

    public TierUpgraderItem(Properties properties) {
        super(properties);
    }

    //? if <1.21.2 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = openStation(level, player);
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }
    //?} else {
    /*@Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return openStation(level, player);
    }
    *///?}

    private static InteractionResult openStation(Level level, Player player) {
        if (!isClientSide(level)) {
            player.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, ignored) -> new UpgradeMenu(windowId, inventory),
                    Component.literal("Станция апгрейда KVR")));
        }
        return success(isClientSide(level));
    }

    private static boolean isClientSide(Level level) {
        //? if >=1.21.11 {
        /*return level.isClientSide();
        *///?} else {
        return level.isClientSide;
        //?}
    }

    private static InteractionResult success(boolean clientSide) {
        //? if >=1.21.11 {
        /*return InteractionResult.SUCCESS;
        *///?} else {
        return InteractionResult.sidedSuccess(clientSide);
        //?}
    }
}
