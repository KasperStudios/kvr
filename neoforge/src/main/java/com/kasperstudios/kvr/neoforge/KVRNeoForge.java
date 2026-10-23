package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRGuiProvider;
import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import com.kasperstudios.kvr.platform.SimpleKVRGui;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint: installs the NeoForge platform facades and boots the KVR core. */
@Mod(KVR.MOD_ID)
public final class KVRNeoForge {

    public KVRNeoForge(IEventBus modBus, ModContainer container) {
        KVRPlatform platform = new NeoForgePlatform(container);
        KVRPlatformProvider.install(platform);
        KVRRegistryProvider.install(new NeoForgeRegistrar(modBus));
        if (platform.isClient()) {
            KVRGuiProvider.install(new SimpleKVRGui(platform.name()));
        }
        KVR.init();
    }
}
