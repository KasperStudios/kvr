package com.kasperstudios.kvr.dock;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Wiring of Block 3c to the existing registries. {@code KVRContent} registers the block, its item,
 * the block entity type and {@code kvr:solar_upgrade}, then hands them over once:
 *
 * <pre>{@code
 * // 1.21.1 (1.21.2+: new BlockEntityType<>(DockPlateBlockEntity::new, Set.of(block)))
 * BlockEntityType<DockPlateBlockEntity> type = BlockEntityType.Builder.of(DockPlateBlockEntity::new, DOCK_PLATE.get()).build(null);
 * KVRDock.install(() -> DOCK_PLATE_BE.get(), stack -> stack.is(SOLAR_UPGRADE.get()), () -> new ItemStack(SOLAR_UPGRADE.get()));
 * // block: new DockPlateBlock(DockPlateBlock.properties())   (+ setId on 1.21.2+, as KVRContent already does)
 * }</pre>
 */
public final class KVRDock {

    private static Supplier<BlockEntityType<DockPlateBlockEntity>> type;
    private static Predicate<ItemStack> isSolarUpgrade = s -> false;
    private static Supplier<ItemStack> solarUpgradeStack = () -> ItemStack.EMPTY;

    private KVRDock() {}

    public static void install(Supplier<BlockEntityType<DockPlateBlockEntity>> blockEntityType, Predicate<ItemStack> solarUpgrade, Supplier<ItemStack> solarStack) {
        type = blockEntityType;
        isSolarUpgrade = solarUpgrade;
        solarUpgradeStack = solarStack;
    }

    public static BlockEntityType<DockPlateBlockEntity> blockEntityType() {
        if (type == null) throw new IllegalStateException("KVRDock is not installed: call KVRDock.install(...) from KVRContent after registering the dock plate");
        return type.get();
    }

    public static boolean isSolarUpgrade(ItemStack stack) {
        return !stack.isEmpty() && isSolarUpgrade.test(stack);
    }

    public static ItemStack solarUpgrade() {
        return solarUpgradeStack.get();
    }
}
