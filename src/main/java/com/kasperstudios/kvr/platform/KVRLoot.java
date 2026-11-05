package com.kasperstudios.kvr.platform;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * Chest loot injection. Used for HackGlasses: 0.5% in absolutely any generated chest — every loot
 * table whose path starts with {@code chests/}, vanilla, datapacks and other mods alike.
 */
public final class KVRLoot {

    public interface Provider {
        void addToEveryChest(Supplier<Item> item, float chance);
    }

    private static Provider provider;

    private KVRLoot() {}

    public static void install(Provider p) {
        provider = p;
    }

    public static void addToEveryChest(Supplier<Item> item, float chance) {
        if (provider == null) throw new IllegalStateException("KVRLoot is not installed: call KVRLoot.install(...) from the loader entrypoint");
        provider.addToEveryChest(item, chance);
    }

    /** {@code chests/...} in any namespace. */
    public static boolean isChestTable(String path) {
        return path.startsWith("chests/");
    }
}
