package com.kasperstudios.kvr.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Cross-platform network facade (Architectury pattern).
 * Thin by rule: loaders implement actual packet sending/receiving.
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

    public interface Provider {
        void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
        void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
        void registerClientReceiver(ResourceLocation channel, PacketHandler handler);
        void registerServerReceiver(ResourceLocation channel, ServerPacketHandler handler);
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
