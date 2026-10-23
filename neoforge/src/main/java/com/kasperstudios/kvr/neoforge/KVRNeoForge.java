package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint: installs the NeoForge platform facades and boots the KVR core. */
@Mod(KVR.MOD_ID)
public final class KVRNeoForge {

    public KVRNeoForge(IEventBus modBus, ModContainer container) {
        KVRPlatformProvider.install(new NeoForgePlatform(container));
        KVRRegistryProvider.install(new NeoForgeRegistrar(modBus));
        KVR.init();
    }
}
