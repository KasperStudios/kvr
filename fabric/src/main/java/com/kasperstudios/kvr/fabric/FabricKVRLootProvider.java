package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.platform.KVRLoot;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * Fabric side of {@link KVRLoot}: one extra pool per chest table, 1 roll gated by a random-chance
 * condition. In {@code onInitialize}: {@code KVRLoot.install(new FabricKVRLootProvider());}
 * Fabric API moved the key-based event to {@code loot.v3} for 1.20.5+, the old one stays {@code loot.v2}.
 */
public final class FabricKVRLootProvider implements KVRLoot.Provider {

    @Override
    public void addToEveryChest(Supplier<Item> item, float chance) {
        //? if >=1.20.5 {
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (KVRLoot.isChestTable(key.location().getPath())) builder.withPool(pool(item, chance));
        });
        //?} else {
        /*net.fabricmc.fabric.api.loot.v2.LootTableEvents.MODIFY.register((resourceManager, lootManager, id, builder, source) -> {
            if (KVRLoot.isChestTable(id.getPath())) builder.withPool(pool(item, chance));
        });
        *///?}
    }

    private static LootPool.Builder pool(Supplier<Item> item, float chance) {
        return LootPool.lootPool()
            .setRolls(ConstantValue.exactly(1))
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(LootItem.lootTableItem(item.get()));
    }
}
