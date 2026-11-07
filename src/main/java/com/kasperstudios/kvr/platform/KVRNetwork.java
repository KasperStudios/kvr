package com.kasperstudios.kvr.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Cross-platform network facade (Architectury pattern).
 * Thin by rule: loaders implement actual packet sending/receiving.
 *
 * <p>Reference copy of the Creator's file (sent with 0019), so code in this tree is written against
 * the real signatures. The repo version is authoritative.
 */
public final class KVRNetwork {

    private static Provider provider;

    private KVRNetwork() {}

    public static void install(Provider p) {
        if (provider != null && provider != p) {
            throw new IllegalStateException("KVRNetwork provider already installed");
        }
        provider = p;
    }

    public static Provider get() {
        if (provider == null) {
            throw new IllegalStateException("KVRNetwork provider is not installed");
        }
        return provider;
    }

    public static void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        get().sendToServer(channel, encoder);
    }

    public static void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        get().sendToPlayer(player, channel, encoder);
    }

    public static void registerClientReceiver(ResourceLocation channel, PacketHandler handler) {
        get().registerClientReceiver(channel, handler);
    }

    public static void registerServerReceiver(ResourceLocation channel, ServerPacketHandler handler) {
        get().registerServerReceiver(channel, handler);
    }

    /**
     * 0026: server → client channels must be known on BOTH sides during init. Their receivers are registered
     * only by client code, so on a dedicated server the channel was first seen at the first send — too late
     * for NeoForge's payload registrar (every node) and for Fabric's payload registry. Common init declares
     * them all here ({@code KVRCommonBootstrap}).
     */
    public static void declareS2C(ResourceLocation... channels) {
        for (ResourceLocation c : channels) get().declareS2C(c);
    }

    public interface Provider {
        void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
        void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
        void registerClientReceiver(ResourceLocation channel, PacketHandler handler);
        void registerServerReceiver(ResourceLocation channel, ServerPacketHandler handler);
        /** Register the payload type of a server → client channel without a handler (default: nothing). */
        default void declareS2C(ResourceLocation channel) {}
    }

    @FunctionalInterface
    public interface PacketHandler {
        void handle(FriendlyByteBuf buf);
    }

    @FunctionalInterface
    public interface ServerPacketHandler {
        void handle(ServerPlayer player, FriendlyByteBuf buf);
    }
}
