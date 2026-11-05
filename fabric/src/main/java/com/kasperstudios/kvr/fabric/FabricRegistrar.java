package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRRegistrar;
import com.kasperstudios.kvr.platform.MenuFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
//?}
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/** Fabric registers straight into the vanilla registries during mod initialization. */
final class FabricRegistrar implements KVRRegistrar {

    @Override
    public void item(ResourceLocation id, Supplier<Item> item) {
        Item registeredItem = item.get();
        Registry.register(BuiltInRegistries.ITEM, id, registeredItem);
    }

    @Override
    public void block(ResourceLocation id, Supplier<Block> block) {
        Block registeredBlock = block.get();
        Registry.register(BuiltInRegistries.BLOCK, id, registeredBlock);
    }

    @Override
    public void entityType(ResourceLocation id, Supplier<EntityType<?>> type) {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type.get());
    }

    @Override
    public void menu(ResourceLocation id, Supplier<MenuType<?>> menu) {
        Registry.register(BuiltInRegistries.MENU, id, menu.get());
    }

    @Override
    public void recipeSerializer(ResourceLocation id, Supplier<net.minecraft.world.item.crafting.RecipeSerializer<?>> serializer) {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, serializer.get());
    }

    @Override
    public void registerWorldgen() {
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        com.kasperstudios.kvr.core.KVRIds.id("ore_up_overworld"))
        );
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInTheNether(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES,
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        com.kasperstudios.kvr.core.KVRIds.id("ore_up_nether"))
        );
    }

    @Override
    public void openMenu(net.minecraft.world.entity.player.Player player, String menuId,
                         net.minecraft.network.chat.Component title, MenuFactory<? extends AbstractContainerMenu> factory) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        //? if >=1.20.5 {
        serverPlayer.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<Boolean>() {
            @Override
            public Boolean getScreenOpeningData(net.minecraft.server.level.ServerPlayer ignored) {
                return Boolean.TRUE;
            }

            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, net.minecraft.world.entity.player.Inventory inventory,
                                                    net.minecraft.world.entity.player.Player menuPlayer) {
                return factory.create(windowId, inventory);
            }
        });
        //?} else {
        /*serverPlayer.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(net.minecraft.server.level.ServerPlayer ignored,
                                               net.minecraft.network.FriendlyByteBuf buf) {
                // the menu type was registered with an empty payload
            }

            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, net.minecraft.world.entity.player.Inventory inventory,
                                                    net.minecraft.world.entity.player.Player menuPlayer) {
                return factory.create(windowId, inventory);
            }
        });
        *///?}
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(MenuFactory<T> factory) {
        // 1.20.5 moved the extended menu type from a PacketByteBuf factory to a StreamCodec payload
        //? if <1.20.5 {
        /*return new ExtendedScreenHandlerType<>(
                (windowId, inventory, buffer) -> factory.create(windowId, inventory));
        *///?} else {
        // A unit codec cannot encode null (StreamCodec.unit(null) NPEs inside encode), which made the
        // open_screen packet fail and dropped the client. A boolean payload keeps it trivially encodable.
        StreamCodec<RegistryFriendlyByteBuf, Boolean> emptyPayload = ByteBufCodecs.BOOL.cast();
        return new ExtendedScreenHandlerType<T, Boolean>(
                (windowId, inventory, payload) -> factory.create(windowId, inventory), emptyPayload);
        //?}
    }
}
