package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint: installs the Fabric platform facades and boots the KVR core. */
public final class KVRFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        KVRPlatformProvider.install(new FabricPlatform());
        KVRRegistryProvider.install(new FabricRegistrar());
        KVR.init();
    }
}
