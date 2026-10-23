package com.kasperstudios.kvr.platform;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Loader facade for registry entries.
 *
 * <p>Fabric registers immediately, NeoForge queues into {@code DeferredRegister}; both expose the
 * same calls so the KVR core never branches on the loader.
 */
public interface KVRRegistrar {

    /** Registers an item as {@code kvr:<path>}. */
    void item(String path, Supplier<Item> item);

    /** Registers a block as {@code kvr:<path>}. */
    void block(String path, Supplier<Block> block);
}
