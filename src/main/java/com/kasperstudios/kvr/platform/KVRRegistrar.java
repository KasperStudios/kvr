package com.kasperstudios.kvr.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Loader facade for registry entries.
 *
 * <p>Fabric registers immediately, NeoForge queues into {@code DeferredRegister}; both expose the same
 * calls so the KVR core never branches on the loader. Ids are passed in already built by
 * {@code KVRIds.id(path)}, so validation cannot be skipped by one loader and enforced by the other.
 */
public interface KVRRegistrar {

    /** Registers an item. */
    void item(ResourceLocation id, Supplier<Item> item);

    /** Registers a block. */
    void block(ResourceLocation id, Supplier<Block> block);

    /** Registers an entity type. */
    void entityType(ResourceLocation id, Supplier<EntityType<?>> type);

    /** Registers a container menu type. */
    void menu(ResourceLocation id, Supplier<MenuType<?>> menu);

    /** Builds a menu type through the loader API - vanilla's constructor is not public. */
    <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory);

    /**
     * Opens a registered KVR menu for a player.
     *
     * <p>Loader-specific on purpose: Fabric registers extended menu types and then demands an
     * {@code ExtendedScreenHandlerFactory}, NeoForge accepts a plain {@code SimpleMenuProvider}.
     */
    void openMenu(net.minecraft.world.entity.player.Player player, String menuId,
                  net.minecraft.network.chat.Component title, MenuFactory<? extends AbstractContainerMenu> factory);
}
