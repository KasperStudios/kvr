package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRRegistrar;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** NeoForge queues entries into deferred registers owned by the mod event bus. */
final class NeoForgeRegistrar implements KVRRegistrar {

    private final DeferredRegister.Items items = DeferredRegister.createItems(KVR.MOD_ID);
    private final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(KVR.MOD_ID);

    NeoForgeRegistrar(IEventBus modBus) {
        items.register(modBus);
        blocks.register(modBus);
    }

    @Override
    public void item(String path, Supplier<Item> item) {
        items.register(path, item);
    }

    @Override
    public void block(String path, Supplier<Block> block) {
        blocks.register(path, block);
    }
}
