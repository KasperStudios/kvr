package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.core.KVRIds;
import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.menu.KVRMenus;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.platform.KVRRegistrar;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Declares and registers every Block 2 item and block through the loader facade.
 *
 * <p>Nothing is constructed here directly: each entry is a factory that is executed at registration
 * time and cached. Fabric registers immediately during mod init, NeoForge registers later from its
 * register event - both create the object when the registry is ready, which is what keeps one class
 * working on both loaders (constructing an {@code Item} too early throws
 * "This registry can't create intrusive holders").
 */
public final class KVRContent {

    private static final Map<String, Supplier<Item>> ITEM_FACTORIES = new LinkedHashMap<>();
    private static final Map<String, Supplier<Block>> BLOCK_FACTORIES = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS = new ConcurrentHashMap<>();
    private static final Map<String, Block> BLOCKS = new ConcurrentHashMap<>();

    private static boolean registered;

    static {
        BLOCK_FACTORIES.put("up_ore", () -> new Block(blockProperties("up_ore")
                .mapColor(MapColor.STONE)
                .strength(3.0F, 3.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)));
        BLOCK_FACTORIES.put("compressed_up_ore", () -> new Block(blockProperties("compressed_up_ore")
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)));
        ITEM_FACTORIES.put("up_ore", () -> new BlockItem(block("up_ore"), itemProperties("up_ore")));
        ITEM_FACTORIES.put("compressed_up_ore", () -> new BlockItem(block("compressed_up_ore"), itemProperties("compressed_up_ore")));
        ITEM_FACTORIES.put("up_raw", () -> new Item(itemProperties("up_raw")));
        ITEM_FACTORIES.put("up_ingot", () -> new Item(itemProperties("up_ingot")));
        ITEM_FACTORIES.put("tier_upgrader_template",
                () -> new TierUpgraderItem(itemProperties("tier_upgrader_template")));
        ITEM_FACTORIES.put("glasses", () -> new GlassesItem(itemProperties("glasses").stacksTo(1)));

        for (ComponentTier tier : ComponentTier.values()) {
            ITEM_FACTORIES.put(chassisPath(tier),
                    () -> new ChassisItem(tier, itemProperties(chassisPath(tier)).stacksTo(1)));
            for (ComponentType type : ComponentType.values()) {
                String path = componentPath(type, tier);
                ITEM_FACTORIES.put(path, () -> new ComponentItem(type, tier, itemProperties(path)));
            }
        }
    }

    /**
     * 1.21.9+ requires items and blocks to carry their registry id in their properties; older versions
     * derive it from the registry entry. Stonecutter strips the branch that does not belong here.
     */
    private static Item.Properties itemProperties(String path) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.11 {
        /*properties = properties.setId(ResourceKey.create(Registries.ITEM, KVRIds.id(path)));
        *///?}
        return properties;
    }

    private static BlockBehaviour.Properties blockProperties(String path) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of();
        //? if >=1.21.11 {
        /*properties = properties.setId(ResourceKey.create(Registries.BLOCK, KVRIds.id(path)));
        *///?}
        return properties;
    }

    private KVRContent() {
    }

    /** Called by every loader entrypoint after the registrar is installed. */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;

        KVRRegistrar registrar = KVRRegistryProvider.get();
        BLOCK_FACTORIES.forEach((path, factory) -> registrar.block(KVRIds.id(path), memoBlock(path, factory)));
        ITEM_FACTORIES.forEach((path, factory) -> registrar.item(KVRIds.id(path), memo(path, factory)));
        KVRMenus.init();

        ChassisDyeRecipe.SERIALIZER = ChassisDyeRecipe.createSerializer();
        registrar.recipeSerializer(KVRIds.id("crafting_special_chassis_dye"), () -> ChassisDyeRecipe.SERIALIZER);

        registrar.registerWorldgen();

        KVR.logger().info("KVR content registered: items={} blocks={}",
                ITEM_FACTORIES.size(), BLOCK_FACTORIES.size());
    }

    private static Supplier<Item> memo(String path, Supplier<Item> factory) {
        return () -> ITEMS.computeIfAbsent(path, ignored -> factory.get());
    }

    private static Supplier<Block> memoBlock(String path, Supplier<Block> factory) {
        return () -> BLOCKS.computeIfAbsent(path, ignored -> factory.get());
    }

    public static String componentPath(ComponentType type, ComponentTier tier) {
        return type.id() + "_tier" + tier.level();
    }

    public static String chassisPath(ComponentTier tier) {
        return "chassis_tier" + tier.level();
    }

    /** Resolved item, or {@code null} before the loader has registered the content. */
    public static Item item(String path) {
        return ITEMS.get(path);
    }

    public static Block block(String path) {
        return BLOCKS.get(path);
    }

    public static Block compressedUpOreBlock() {
        return block("compressed_up_ore");
    }

    public static Item compressedUpOre() {
        return item("compressed_up_ore");
    }

    public static ChassisItem chassis(ComponentTier tier) {
        return (ChassisItem) item(chassisPath(tier));
    }

    public static ComponentItem component(ComponentType type, ComponentTier tier) {
        return (ComponentItem) item(componentPath(type, tier));
    }

    public static Item upRaw() {
        return item("up_raw");
    }

    public static Item upIngot() {
        return item("up_ingot");
    }

    public static Item tierUpgrader() {
        return item("tier_upgrader_template");
    }

    public static GlassesItem glasses() {
        return (GlassesItem) item("glasses");
    }

    /** Items the client tints with the stored paint: every chassis plus assembled glasses. */
    public static java.util.List<Item> paintableItems() {
        java.util.List<Item> items = new java.util.ArrayList<>();
        for (ComponentTier tier : ComponentTier.values()) {
            items.add(chassis(tier));
        }
        items.add(glasses());
        return items;
    }

    /** Every registered item path, in declaration order. */
    public static Map<String, Supplier<Item>> itemFactories() {
        return Collections.unmodifiableMap(ITEM_FACTORIES);
    }

    /** Test/reload hook. */
    public static void reset() {
        registered = false;
        ITEMS.clear();
        BLOCKS.clear();
    }
}
