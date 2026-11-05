package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.platform.KVRServer;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NeoForgeKVRServerProvider implements KVRServer.Provider {
    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> callback.accept(e.getDispatcher()));
    }
}
