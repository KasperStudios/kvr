package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.items.KVRContent;
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
        KVRContent.init();
        if (platform.isClient()) {
            KVRGuiProvider.install(new SimpleKVRGui(platform.name()));
        }
        KVR.init();

        // Register Creative Mode Tab for Fabric
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(
            net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES
        ).register(entries -> {
            KVRContent.itemFactories().forEach((path, factory) -> {
                if (!path.equals("hackglasses")) {
                    net.minecraft.world.item.Item it = factory.get();
                    if (it != null) entries.accept(it);
                }
            });
        });
        registerKvrTab();
    }

    private static void registerKvrTab() {
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.CreativeModeTab> tabKey = 
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,
                com.kasperstudios.kvr.core.KVRIds.id("main")
            );
        net.minecraft.core.Registry.register(
            net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB,
            tabKey,
            net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
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
    }
}
