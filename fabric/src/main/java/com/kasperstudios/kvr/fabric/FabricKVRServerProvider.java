package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRServer;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;

public final class FabricKVRServerProvider implements KVRServer.Provider {
    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> callback.accept(dispatcher));
    }
}
