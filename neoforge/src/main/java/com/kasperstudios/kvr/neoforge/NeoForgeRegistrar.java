package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRRegistrar;
import com.kasperstudios.kvr.platform.MenuFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** NeoForge queues entries into deferred registers owned by the mod event bus. */
final class NeoForgeRegistrar implements KVRRegistrar {

    private final DeferredRegister.Items items = DeferredRegister.createItems(KVR.MOD_ID);
    private final DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(KVR.MOD_ID);
    private final DeferredRegister<EntityType<?>> entityTypes = DeferredRegister.create(Registries.ENTITY_TYPE, KVR.MOD_ID);
    private final DeferredRegister<MenuType<?>> menus = DeferredRegister.create(Registries.MENU, KVR.MOD_ID);
    private final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> recipeSerializers =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, KVR.MOD_ID);

    NeoForgeRegistrar(IEventBus modBus) {
        items.register(modBus);
        blocks.register(modBus);
        entityTypes.register(modBus);
        menus.register(modBus);
        recipeSerializers.register(modBus);
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

    @Override
    public void menu(ResourceLocation id, Supplier<MenuType<?>> menu) {
        menus.register(id.getPath(), menu);
    }

    @Override
    public void recipeSerializer(ResourceLocation id, Supplier<net.minecraft.world.item.crafting.RecipeSerializer<?>> serializer) {
        recipeSerializers.register(id.getPath(), serializer);
    }

    @Override
    public void registerWorldgen() {
        // NeoForge uses biome modifier JSONs placed in data/kvr/neoforge/biome_modifier/
    }

    @Override
    public void openMenu(net.minecraft.world.entity.player.Player player, String menuId,
                         net.minecraft.network.chat.Component title, MenuFactory<? extends AbstractContainerMenu> factory) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (windowId, inventory, menuPlayer) -> factory.create(windowId, inventory), title));
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
        return IMenuTypeExtension.create((windowId, inventory, buffer) -> factory.create(windowId, inventory));
    }
}
