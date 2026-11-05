package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRServer;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;

/**
 * Fabric side of {@link KVRServer}. In {@code ModInitializer#onInitialize}:
 * <pre>{@code
 * KVRServer.install(new FabricKVRServerProvider());
 * KVRPower.init();
 * }</pre>
 */
public final class FabricKVRServerProvider implements KVRServer.Provider {

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> callback.accept(dispatcher));
    }

    @Override
    public void onServerTick(Consumer<net.minecraft.server.MinecraftServer> callback) {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(callback::accept);
    }
}
