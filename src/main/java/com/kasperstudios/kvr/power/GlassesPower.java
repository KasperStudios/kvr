package com.kasperstudios.kvr.power;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Power facade over a glasses {@link ItemStack}. The power code does not know how the Block 2
 * assembly is stored, so the existing code plugs it in once at init:
 *
 * <pre>{@code
 * GlassesPower.install(
 *     stack -> stack.getItem() instanceof GlassesItem,
 *     stack -> GlassesCodec.read(stack).map(b -> new PowerBuild(b.cpu(), b.gpu(), b.ram(), b.storage(), b.battery())),
 *     stack -> GlassesItem.disassemble(stack));   // [chassis, cpu, gpu, ram, storage, battery]
 * }</pre>
 *
 * Tiers handed to {@link PowerBuild} are 1..5.
 */
public final class GlassesPower {

    private static Predicate<ItemStack> isGlasses = s -> false;
    private static Function<ItemStack, Optional<PowerBuild>> buildReader = s -> Optional.empty();
    private static Function<ItemStack, List<ItemStack>> disassembler = s -> List.of();
    /** Head slot by default; Curios/Trinkets integration can widen it. */
    private static BiPredicate<LivingEntity, ItemStack> wornCheck = (e, s) -> e.getItemBySlot(EquipmentSlot.HEAD) == s;

    private GlassesPower() {}

    public static void install(Predicate<ItemStack> glasses, Function<ItemStack, Optional<PowerBuild>> reader, Function<ItemStack, List<ItemStack>> disassemble) {
        isGlasses = glasses;
        buildReader = reader;
        disassembler = disassemble;
    }

    public static void installWornCheck(BiPredicate<LivingEntity, ItemStack> check) {
        wornCheck = check;
    }

    public static boolean isGlasses(ItemStack stack) {
        return !stack.isEmpty() && isGlasses.test(stack);
    }

    public static Optional<PowerBuild> build(ItemStack stack) {
        return isGlasses(stack) ? buildReader.apply(stack) : Optional.empty();
    }

    public static List<ItemStack> disassemble(ItemStack stack) {
        return disassembler.apply(stack);
    }

    public static boolean isWorn(LivingEntity entity, ItemStack stack) {
        return wornCheck.test(entity, stack);
    }

    public static PowerData data(ItemStack stack) {
        return PowerData.load(PowerNbt.read(stack));
    }

    /** False for a pair that was never powered or ticked yet (no {@code kvr_power} on the stack). */
    public static boolean hasData(ItemStack stack) {
        return !PowerNbt.read(stack).isEmpty();
    }

    /** Out of the box: {@link PowerTable#FACTORY_CHARGE_PERCENT}, OFF, nothing simulated yet. */
    static PowerData factoryData(PowerBuild build, long gameTime) {
        PowerData d = new PowerData();
        d.setChargePercent(build, PowerTable.FACTORY_CHARGE_PERCENT);
        d.lastStep = gameTime;
        return d;
    }

    public static void save(ItemStack stack, PowerData data) {
        PowerNbt.write(stack, data.save());
    }

    /** Battery for the dock / status (client reads the synced stack). */
    public static int percent(ItemStack stack) {
        return build(stack).map(b -> data(stack).percent(b)).orElse(0);
    }

    /** OS running (ON or HUD-off, booted). */
    public static boolean active(ItemStack stack, long gameTime) {
        return build(stack).isPresent() && data(stack).active(gameTime);
    }

    /** For {@code SpatialConfig.glassesWorn}: display lit (ON and booted) — HUD_OFF hides every window. */
    public static boolean displayOn(ItemStack stack, long gameTime) {
        return build(stack).isPresent() && data(stack).displayOn(gameTime);
    }

    /** Player action; persists immediately and reports SAVED when it defuses an overload. */
    public static PowerSimulator.Event setState(ItemStack stack, PowerState target, long gameTime) {
        Optional<PowerBuild> b = build(stack);
        if (b.isEmpty()) return PowerSimulator.Event.NONE;
        PowerData d = hasData(stack) ? data(stack) : factoryData(b.get(), gameTime);
        d.lastStep = gameTime; // sync time stamp directly to prevent stale deltas
        PowerSimulator.Event e = PowerSimulator.setState(b.get(), d, target, gameTime);
        save(stack, d);
        return e;
    }
}
