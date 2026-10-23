package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRRegistrar;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Fabric registers straight into the vanilla registries during mod initialization. */
final class FabricRegistrar implements KVRRegistrar {

    @Override
    public void item(ResourceLocation id, Supplier<Item> item) {
        Registry.register(BuiltInRegistries.ITEM, id, item.get());
    }

    @Override
    public void block(ResourceLocation id, Supplier<Block> block) {
        Registry.register(BuiltInRegistries.BLOCK, id, block.get());
    }

    @Override
    public void entityType(ResourceLocation id, Supplier<EntityType<?>> type) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type.get());
    }
}
