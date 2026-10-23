package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.platform.KVRPlatform;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.api.distmarker.Dist;

import java.nio.file.Path;

final class NeoForgePlatform implements KVRPlatform {

    private final ModContainer container;

    NeoForgePlatform(ModContainer container) {
        this.container = container;
    }

    @Override
    public String name() {
        return "neoforge";
    }

    @Override
    public String modVersion() {
        return container.getModInfo().getVersion().toString();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isClient() {
        // FML loader 10 (NeoForge 21.11+) turned the `dist` field into a getter
        //? if <1.21.11 {
        return FMLEnvironment.dist == Dist.CLIENT;
        //?} else {
        /*return FMLEnvironment.getDist() == Dist.CLIENT;
        *///?}
    }
}
