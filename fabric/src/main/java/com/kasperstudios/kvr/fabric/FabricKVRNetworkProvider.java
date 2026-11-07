package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRNetwork;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fabric implementation of {@link KVRNetwork.Provider}.
 *
 * <p>The Creator's file (received with 0020), 0026 adds the version branches:
 * <ul>
 *   <li><b>1.20.5+</b> (1.21.1, 1.21.11): CustomPacketPayload + PayloadTypeRegistry — the Creator's code, unchanged;
 *       {@link #declareS2C} registers server → client types during init (dedicated server).</li>
 *   <li><b>1.20.1 / 1.20.4</b>: the channel-id API of Fabric networking v1
 *       ({@code registerGlobalReceiver(ResourceLocation, ...)}, {@code send(channel, buf)}). No payload types exist
 *       there, so nothing has to be declared. The incoming buffer is released when the callback returns, so its
 *       bytes are copied before the hop to the main thread.</li>
 * </ul>
 * Version-specific types ({@code CustomPacketPayload}, {@code StreamCodec}, {@code PayloadTypeRegistry},
 * {@code PacketByteBufs}) are referenced by full name inside their branch only.
 */
public final class FabricKVRNetworkProvider implements KVRNetwork.Provider {

    private final Set<ResourceLocation> registeredC2S = Collections.synchronizedSet(new HashSet<>());
    private final Set<ResourceLocation> registeredS2C = Collections.synchronizedSet(new HashSet<>());

    //? if >=1.20.5 {
    public record RawPayload(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type, byte[] data)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type) {
            return net.minecraft.network.protocol.common.custom.CustomPacketPayload.codec((payload, buf) -> {
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

    private net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> ensureC2S(ResourceLocation channel) {
        var type = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload>(channel);
        if (registeredC2S.add(channel)) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(type, RawPayload.codec(type));
        }
        return type;
    }

    private net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> ensureS2C(ResourceLocation channel) {
        var type = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload>(channel);
        if (registeredS2C.add(channel)) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(type, RawPayload.codec(type));
        }
        return type;
    }

    @Override
    public void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        ClientPlayNetworking.send(new RawPayload(ensureC2S(channel), encode(encoder)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        ServerPlayNetworking.send(player, new RawPayload(ensureS2C(channel), encode(encoder)));
    }

    @Override
    public void registerClientReceiver(ResourceLocation channel, KVRNetwork.PacketHandler handler) {
        ClientPlayNetworking.registerGlobalReceiver(ensureS2C(channel), (payload, context) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            context.client().execute(() -> handler.handle(buf));
        });
    }

    @Override
    public void registerServerReceiver(ResourceLocation channel, KVRNetwork.ServerPacketHandler handler) {
        ServerPlayNetworking.registerGlobalReceiver(ensureC2S(channel), (payload, context) -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
            context.server().execute(() -> handler.handle(context.player(), buf));
        });
    }

    /** 0026: the dedicated server never calls registerClientReceiver, so S2C types are registered here during init. */
    @Override
    public void declareS2C(ResourceLocation channel) {
        ensureS2C(channel);
    }

    private static byte[] encode(Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
    //?} else {
    /*@Override
    public void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        registeredC2S.add(channel);
        FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        encoder.accept(buf);
        ClientPlayNetworking.send(channel, buf);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        registeredS2C.add(channel);
        FriendlyByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        encoder.accept(buf);
        ServerPlayNetworking.send(player, channel, buf);
    }

    @Override
    public void registerClientReceiver(ResourceLocation channel, KVRNetwork.PacketHandler handler) {
        registeredS2C.add(channel);
        ClientPlayNetworking.registerGlobalReceiver(channel, (client, listener, buf, sender) -> {
            byte[] data = copy(buf); // released by Fabric when this callback returns
            client.execute(() -> handler.handle(new FriendlyByteBuf(Unpooled.wrappedBuffer(data))));
        });
    }

    @Override
    public void registerServerReceiver(ResourceLocation channel, KVRNetwork.ServerPacketHandler handler) {
        registeredC2S.add(channel);
        ServerPlayNetworking.registerGlobalReceiver(channel, (server, player, listener, buf, sender) -> {
            byte[] data = copy(buf);
            server.execute(() -> handler.handle(player, new FriendlyByteBuf(Unpooled.wrappedBuffer(data))));
        });
    }

    // declareS2C: the channel-id API has no payload types, the default no-op is right

    private static byte[] copy(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }
    *///?}
}
