package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRGuiProvider;
import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import com.kasperstudios.kvr.platform.SimpleKVRGui;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint: installs the Fabric platform facades and boots the KVR core. */
public final class KVRFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        KVRPlatform platform = new FabricPlatform();
        KVRPlatformProvider.install(platform);
        KVRRegistryProvider.install(new FabricRegistrar());
        if (platform.isClient()) {
            KVRGuiProvider.install(new SimpleKVRGui(platform.name()));
        }
        KVR.init();
    }
}
