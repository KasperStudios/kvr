package com.kasperstudios.kvr.power;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: the player switches the power state of their glasses (Power window, power key, overload
 * panel). The only Block 3 packet — the overload reaches the client through the synced stack.
 * Written for the project's channel-based {@code KVRNetwork}:
 *
 * <pre>{@code
 * KVRNetwork.registerServerReceiver(SetPowerStatePayload.CHANNEL, (player, buf) -> {
 *     SetPowerStatePayload p = SetPowerStatePayload.read(buf);
 *     player.getServer().execute(() -> PowerServerHandler.handle(player, p));
 * });
 * PowerClient.installSender(state -> KVRNetwork.sendToServer(SetPowerStatePayload.CHANNEL, new SetPowerStatePayload(state)::write));
 * }</pre>
 */
public record SetPowerStatePayload(PowerState state) {

    public static final ResourceLocation CHANNEL = id("set_power_state");

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(state);
    }

    public static SetPowerStatePayload read(FriendlyByteBuf buf) {
        return new SetPowerStatePayload(buf.readEnum(PowerState.class));
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
