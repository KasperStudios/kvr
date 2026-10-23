package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRRegistrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** NeoForge queues entries into deferred registers owned by the mod event bus. */
final class NeoForgeRegistrar implements KVRRegistrar {

    private final DeferredRegister.Items items = DeferredRegister.createItems(KVR.MOD_ID);
    private final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(KVR.MOD_ID);
    private final DeferredRegister<EntityType<?>> entityTypes = DeferredRegister.create(Registries.ENTITY_TYPE, KVR.MOD_ID);

    NeoForgeRegistrar(IEventBus modBus) {
        items.register(modBus);
        blocks.register(modBus);
        entityTypes.register(modBus);
    }

    @Override
    public void item(ResourceLocation id, Supplier<Item> item) {
        items.register(id.getPath(), item);
    }

    @Override
    public void block(ResourceLocation id, Supplier<Block> block) {
        blocks.register(id.getPath(), block);
    }

    @Override
    public void entityType(ResourceLocation id, Supplier<EntityType<?>> type) {
        entityTypes.register(id.getPath(), type);
    }
}
