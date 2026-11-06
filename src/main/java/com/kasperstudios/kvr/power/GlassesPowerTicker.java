package com.kasperstudios.kvr.power;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

/**
 * Server tick entry points. Two callers:
 * <ul>
 *   <li>{@code GlassesItem#inventoryTick} → {@link #tickCarried} (worn, in a hand or anywhere in the inventory);</li>
 *   <li>{@code ItemEntityMixin} → {@link #tickOnGround} (vanilla never ticks items lying on the ground).</li>
 * </ul>
 * Containers do not tick items; the absolute deadline makes the glasses go off as soon as someone
 * takes them out.
 *
 * <pre>{@code
 * // GlassesItem (1.21.1 signature; 1.21.5+ passes ServerLevel + EquipmentSlot)
 * @Override
 * public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
 *     GlassesPowerTicker.tickCarried(stack, level, entity);
 * }
 * }</pre>
 */
public final class GlassesPowerTicker {

    private GlassesPowerTicker() {}

    public static void tickCarried(ItemStack stack, Level level, Entity holder) {
        if (!(level instanceof ServerLevel server)) return;
        Optional<PowerBuild> build = GlassesPower.build(stack);
        if (build.isEmpty()) return;

        long now = server.getGameTime();
        if (!GlassesPower.hasData(stack)) {
            // brand-new pair: half charged out of the box, synced to the client right away
            GlassesPower.save(stack, GlassesPower.factoryData(build.get(), now));
            return;
        }
        PowerData d = GlassesPower.data(stack);
        boolean detonationDue = d.overloading() && now >= d.overloadDeadline;
        if (!detonationDue && now % PowerTable.STEP_TICKS != 0) return;

        PowerSimulator.Event event = PowerSimulator.step(build.get(), d, now, PowerTable.drainScale());
        Location where = locate(holder, stack);
        switch (event) {
            case DETONATE -> {
                OverloadEffects.detonate(server, stack, holder, where);
                return; // the stack is gone
            }
            case OVERLOAD_STARTED -> OverloadEffects.start(server, holder, where);
            default -> {}
        }
        if (d.overloading() && now % PowerTable.SIREN_PERIOD_TICKS == 0) OverloadEffects.siren(server);
        GlassesPower.save(stack, d);
    }

    public static void tickOnGround(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (GlassesPower.isGlasses(stack)) tickCarried(stack, entity.level(), entity);
    }

    public enum Location { HEAD, CARRIED, GROUND }

    static Location locate(Entity holder, ItemStack stack) {
        if (holder instanceof ItemEntity) return Location.GROUND;
        if (holder instanceof LivingEntity living && GlassesPower.isWorn(living, stack)) return Location.HEAD;
        return Location.CARRIED;
    }

    /** Parts that survive, already rolled (used by {@link OverloadEffects}). */
    static List<ItemStack> survivors(ServerLevel level, ItemStack glasses) {
        return PartBurn.survivors(GlassesPower.disassemble(glasses), () -> level.random.nextDouble());
    }
}
