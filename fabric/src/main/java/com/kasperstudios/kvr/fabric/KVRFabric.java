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
        com.kasperstudios.kvr.platform.KVRServer.install(new FabricKVRServerProvider());
        com.kasperstudios.kvr.platform.KVRNetwork.install(new FabricKVRNetworkProvider());
        KVRContent.init();

        // Wire KVRDock: register BlockEntityType and install
        net.minecraft.world.level.block.Block dockBlock = KVRContent.block("dock_plate");
        net.minecraft.world.level.block.entity.BlockEntityType<com.kasperstudios.kvr.dock.DockPlateBlockEntity> dockBeType =
            net.minecraft.world.level.block.entity.BlockEntityType.Builder
                .of(com.kasperstudios.kvr.dock.DockPlateBlockEntity::new, dockBlock)
                .build(null);
        net.minecraft.core.Registry.register(
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE,
            com.kasperstudios.kvr.core.KVRIds.id("dock_plate"),
            dockBeType
        );
        com.kasperstudios.kvr.dock.KVRDock.install(
            () -> dockBeType,
            stack -> stack.is(KVRContent.item("solar_upgrade")),
            () -> new net.minecraft.world.item.ItemStack(KVRContent.item("solar_upgrade"))
        );
        if (platform.isClient()) {
            KVRGuiProvider.install(new SimpleKVRGui(platform.name()));
        }
        KVR.init();
        com.kasperstudios.kvr.bootstrap.KVRCommonBootstrap.init();

        // Register Creative Mode Tab for Fabric
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
                            net.minecraft.world.item.Item item = KVRContent.item(path);
                            if (item != null) {
                                output.accept(item);
                            }
                        }
                    });
                })
                .build()
        );
    }
}
