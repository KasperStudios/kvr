package com.kasperstudios.kvr.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;

/**
 * Server-side loader hooks (both dedicated and integrated server). Same facade pattern as
 * {@link KVRClient}: the loader entrypoint installs the provider, the core asks for it.
 */
public final class KVRServer {

    public interface Provider {
        void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback);
        default void onServerTick(Consumer<net.minecraft.server.MinecraftServer> callback) {}
    }

    private static Provider provider;

    private KVRServer() {}

    public static void install(Provider p) {
        if (provider != null && provider != p) throw new IllegalStateException("KVRServer provider already installed: " + provider.getClass().getName());
        provider = p;
    }

    public static Provider get() {
        if (provider == null) throw new IllegalStateException("KVRServer is not installed: call KVRServer.install(...) from the loader entrypoint before KVRPower.init()");
        return provider;
    }

    public static void onServerTick(Consumer<net.minecraft.server.MinecraftServer> callback) {
        get().onServerTick(callback);
    }
}
