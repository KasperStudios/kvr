package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRPlatform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

final class FabricPlatform implements KVRPlatform {

    @Override
    public String name() {
        return "fabric";
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(KVR.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
