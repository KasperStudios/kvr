package com.kasperstudios.kvr.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of {@link SetPowerStatePayload}. Runs on the server thread (make sure your
 * {@code KVRNetwork} receiver enqueues it there). Same targeting as {@code /kvr power}: the glasses
 * on the head, otherwise in the main hand. Power Off during an overload is the save.
 */
public final class PowerServerHandler {

    private PowerServerHandler() {}

    public static void handle(ServerPlayer player, SetPowerStatePayload payload) {
        ItemStack glasses = player.getItemBySlot(EquipmentSlot.HEAD);
        if (GlassesPower.build(glasses).isEmpty()) glasses = player.getMainHandItem();
        if (GlassesPower.build(glasses).isEmpty()) return;
        PowerSimulator.Event e = GlassesPower.setState(glasses, payload.state(), player.level().getGameTime());
        if (e == PowerSimulator.Event.SAVED) OverloadEffects.saved(player);
    }
}
