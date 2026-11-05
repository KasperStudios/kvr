package com.kasperstudios.kvr.power;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: the player switches the power state of their glasses from the "Питание" window or the
 * overload panel. This is the only Block 3 packet: the overload itself reaches the client through
 * the synced glasses stack (power data is written every second), so no S2C alarm is needed.
 *
 * <p>Register with your {@code KVRNetwork} (server receiver) and wire the client sender:
 * <pre>{@code
 * KVRNetwork.registerServerbound(SetPowerStatePayload.TYPE, SetPowerStatePayload.CODEC, PowerServerHandler::handle);
 * PowerClient.installSender(state -> KVRNetwork.sendToServer(new SetPowerStatePayload(state)));
 * }</pre>
 */
//? if >=1.20.5 {
public record SetPowerStatePayload(PowerState state) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<SetPowerStatePayload> TYPE = new Type<>(id("set_power_state"));

    public static final net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, SetPowerStatePayload> CODEC =
        net.minecraft.network.codec.StreamCodec.of((buf, p) -> buf.writeEnum(p.state()), buf -> new SetPowerStatePayload(buf.readEnum(PowerState.class)));

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record SetPowerStatePayload(PowerState state) {

    public static final ResourceLocation ID = id("set_power_state");

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(state);
    }

    public static SetPowerStatePayload read(FriendlyByteBuf buf) {
        return new SetPowerStatePayload(buf.readEnum(PowerState.class));
    }
*///?}

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
