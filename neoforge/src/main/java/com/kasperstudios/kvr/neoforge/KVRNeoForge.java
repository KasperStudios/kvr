package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.items.KVRContent;
import com.kasperstudios.kvr.platform.KVRGuiProvider;
import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import com.kasperstudios.kvr.platform.SimpleKVRGui;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
//? if <1.20.5 {
/*import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
*///?}
import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint: installs the NeoForge platform facades and boots the KVR core. */
@Mod(KVR.MOD_ID)
public final class KVRNeoForge {

    // NeoForge 20.5+ injects the mod bus and container into the constructor; 1.20.4 has to ask for them
    //? if >=1.20.5 {
    public KVRNeoForge(IEventBus modBus, ModContainer container) {
        bootstrap(modBus, container);
    }
    //?} else {
    /*public KVRNeoForge() {
        bootstrap(FMLJavaModLoadingContext.get().getModEventBus(),
                ModList.get().getModContainerById(KVR.MOD_ID).orElseThrow(
                        () -> new IllegalStateException("KVR mod container missing")));
    }
    *///?}

    private static void bootstrap(IEventBus modBus, ModContainer container) {
        KVRPlatform platform = new NeoForgePlatform(container);
        KVRPlatformProvider.install(platform);
        KVRRegistryProvider.install(new NeoForgeRegistrar(modBus));
        KVRContent.init();
        if (platform.isClient()) {
            KVRGuiProvider.install(new SimpleKVRGui(platform.name()));
            NeoForgeClientScreens.register(modBus);
        }
        KVR.init();

        // Register Creative Mode Tab for NeoForge
        modBus.addListener(net.neoforged.neoforge.registries.RegisterEvent.class, event -> {
            event.register(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, helper -> {
                helper.register(
                    com.kasperstudios.kvr.core.KVRIds.id("main"),
                    net.minecraft.world.item.CreativeModeTab.builder()
                        .title(net.minecraft.network.chat.Component.translatable("itemGroup.kvr.main"))
                        .icon(() -> new net.minecraft.world.item.ItemStack(KVRContent.glasses()))
                        .displayItems((params, output) -> {
                            KVRContent.itemFactories().forEach((path, factory) -> {
                                if (!path.equals("hackglasses")) {
                                    net.minecraft.world.item.Item it = factory.get();
                                    if (it != null) output.accept(it);
                                }
                            });
                        })
                        .build()
                );
            });
        });
    }
}
