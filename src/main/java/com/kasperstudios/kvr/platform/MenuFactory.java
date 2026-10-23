package com.kasperstudios.kvr.platform;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Creates a menu instance for a window id and the player inventory.
 *
 * <p>Own functional type: {@code MenuType.MenuSupplier} is not accessible outside the vanilla class, so
 * each loader adapts this factory to its own menu-type API (Fabric extended type, NeoForge extension).
 */
@FunctionalInterface
public interface MenuFactory<T extends AbstractContainerMenu> {

    T create(int windowId, Inventory inventory);
}
