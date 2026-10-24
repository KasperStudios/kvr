package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRRegistrar;
import com.kasperstudios.kvr.platform.MenuFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    @Override
    public void menu(ResourceLocation id, Supplier<MenuType<?>> menu) {
        Registry.register(BuiltInRegistries.MENU, id, menu.get());
    }

    @Override
    public void openMenu(net.minecraft.world.entity.player.Player player, String menuId,
                         net.minecraft.network.chat.Component title, MenuFactory<? extends AbstractContainerMenu> factory) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }
        //? if >=1.20.5 {
        serverPlayer.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<Void>() {
            @Override
            public Void getScreenOpeningData(net.minecraft.server.level.ServerPlayer ignored) {
                // the menu type was registered with a unit payload codec
                return null;
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
        StreamCodec<RegistryFriendlyByteBuf, Void> noPayload = StreamCodec.unit(null);
        return new ExtendedScreenHandlerType<T, Void>(
                (windowId, inventory, payload) -> factory.create(windowId, inventory), noPayload);
        //?}
    }
}
