package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.platform.KVRLoot;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * NeoForge side of {@link KVRLoot}: adds a named pool when a chest table loads.
 * In the {@code KVRNeoForge} constructor: {@code KVRLoot.install(new NeoForgeKVRLootProvider());}
 */
public final class NeoForgeKVRLootProvider implements KVRLoot.Provider {

    @Override
    public void addToEveryChest(Supplier<Item> item, float chance) {
        NeoForge.EVENT_BUS.addListener(LootTableLoadEvent.class, e -> {
            if (!KVRLoot.isChestTable(e.getName().getPath())) return;
            e.getTable().addPool(LootPool.lootPool()
                .name("kvr_hackglasses") // NeoForge requires unique pool names
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(LootItem.lootTableItem(item.get()))
                .build());
        });
    }
}
