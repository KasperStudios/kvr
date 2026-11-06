package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRNetwork;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fabric implementation of {@link KVRNetwork.Provider} for 1.20.5+ / 1.21.1 CustomPacketPayloads.
 */
public final class FabricKVRNetworkProvider implements KVRNetwork.Provider {

    private final Set<ResourceLocation> registeredC2S = Collections.synchronizedSet(new HashSet<>());
    private final Set<ResourceLocation> registeredS2C = Collections.synchronizedSet(new HashSet<>());

    public record RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) implements CustomPacketPayload {
        public static StreamCodec<FriendlyByteBuf, RawPayload> codec(CustomPacketPayload.Type<RawPayload> type) {
            return CustomPacketPayload.codec((payload, buf) -> {
                buf.writeVarInt(payload.data.length);
                buf.writeBytes(payload.data);
            }, buf -> {
                int len = buf.readVarInt();
                byte[] bytes = new byte[len];
                buf.readBytes(bytes);
                return new RawPayload(type, bytes);
            });
        }
    }

    private CustomPacketPayload.Type<RawPayload> ensureC2S(ResourceLocation channel) {
        CustomPacketPayload.Type<RawPayload> type = new CustomPacketPayload.Type<>(channel);
        if (registeredC2S.add(channel)) {
            PayloadTypeRegistry.playC2S().register(type, RawPayload.codec(type));
        }
        return type;
    }

    private CustomPacketPayload.Type<RawPayload> ensureS2C(ResourceLocation channel) {
        CustomPacketPayload.Type<RawPayload> type = new CustomPacketPayload.Type<>(channel);
        if (registeredS2C.add(channel)) {
            PayloadTypeRegistry.playS2C().register(type, RawPayload.codec(type));
        }
        return type;
    }

    @Override
    public void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        CustomPacketPayload.Type<RawPayload> type = ensureC2S(channel);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        ClientPlayNetworking.send(new RawPayload(type, bytes));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        CustomPacketPayload.Type<RawPayload> type = ensureS2C(channel);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        ServerPlayNetworking.send(player, new RawPayload(type, bytes));
    }

    @Override
    public void registerClientReceiver(ResourceLocation channel, KVRNetwork.PacketHandler handler) {
        CustomPacketPayload.Type<RawPayload> type = ensureS2C(channel);
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            context.client().execute(() -> handler.handle(buf));
        });
    }

    @Override
    public void registerServerReceiver(ResourceLocation channel, KVRNetwork.ServerPacketHandler handler) {
        CustomPacketPayload.Type<RawPayload> type = ensureC2S(channel);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            context.server().execute(() -> handler.handle(context.player(), buf));
        });
    }
}
