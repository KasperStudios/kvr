package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.core.KVRIds;
import com.kasperstudios.kvr.platform.KVRRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Fabric registers straight into the vanilla registries during mod initialization. */
final class FabricRegistrar implements KVRRegistrar {

    @Override
    public void item(String path, Supplier<Item> item) {
        Registry.register(BuiltInRegistries.ITEM, KVRIds.id(path), item.get());
    }

    @Override
    public void block(String path, Supplier<Block> block) {
        Registry.register(BuiltInRegistries.BLOCK, KVRIds.id(path), block.get());
    }
}
