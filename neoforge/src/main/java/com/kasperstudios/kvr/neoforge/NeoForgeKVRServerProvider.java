package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.platform.KVRServer;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge side of {@link KVRServer}. In the {@code KVRNeoForge} constructor (both dists):
 * <pre>{@code
 * KVRServer.install(new NeoForgeKVRServerProvider());
 * KVRPower.init();
 * }</pre>
 */
public final class NeoForgeKVRServerProvider implements KVRServer.Provider {

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> callback.accept(e.getDispatcher()));
    }

    @Override
    public void onServerTick(Consumer<net.minecraft.server.MinecraftServer> callback) {
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, e -> callback.accept(e.getServer()));
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TickEvent.ServerTickEvent.class, e -> {
            if (e.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) callback.accept(e.getServer());
        });
        *///?}
    }
}
