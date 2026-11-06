package com.kasperstudios.kvr.glasses;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
//? if <1.21.2 {
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
//?}

/**
 * Base class for everything worn as glasses: KVR glasses and HackGlasses. They go into the vanilla
 * HEAD slot — the slot every KVR system already reads (power ticking, spatial UI, VFS, HackGlasses).
 *
 * <ul>
 *   <li>RMB with the glasses in hand puts them on (swaps with the helmet), shift-click in the inventory and
 *       dragging into the helmet slot work, like a carved pumpkin or an elytra.</li>
 *   <li>On the head they are drawn by vanilla {@code CustomHeadLayer} with the item model's
 *       {@code display.head} transform — tune it in {@code models/item/<glasses>.json}, no renderer code needed.</li>
 *   <li>Trade-off by design: glasses replace the helmet. Curios/Trinkets "face" slots can be added later
 *       through {@code GlassesPower.installWornCheck} + {@code PowerClient.worn}, nothing else assumes HEAD.</li>
 * </ul>
 *
 * Versions: {@code Equipable} interface up to 1.21.1; from 1.21.2 the {@code EQUIPPABLE} data component.
 *
 * <pre>{@code
 * public class GlassesItem extends WearableGlassesItem {           // was: extends Item
 *     public GlassesItem(Properties p) { super(p); }
 *     // power ticking is inherited (0021): no inventoryTick needed in the subclass
 * }
 * }</pre>
 */
//? if <1.21.2 {
public class WearableGlassesItem extends Item implements net.minecraft.world.item.Equipable {
//?} else {
/*public class WearableGlassesItem extends Item {
*///?}

    public WearableGlassesItem(Properties properties) {
        super(wearable(properties.stacksTo(1)));
    }

    private static Properties wearable(Properties p) {
        //? if >=1.21.2 {
        /*return p.component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
            net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.HEAD)
                .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC)
                .build());
        *///?} else {
        return p;
        //?}
    }

    //? if <1.21.2 {
    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    /** RMB: put on, or swap with whatever is on the head (vanilla helper of {@code Equipable}). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }
    //?}

    // 0021 — THE "battery dies the moment I press O" bug: nothing ever called GlassesPowerTicker, so the
    // server never simulated the glasses while worn. ON time piled up unaccounted and the next O press
    // (GlassesPower.setState settles consumption first) charged all of it at once → 0 %. The base class
    // ticks now, so every subclass (GlassesItem, HackGlasses) drains smoothly every second. A subclass
    // that also calls tickCarried is harmless: the second step in the same tick has dt = 0.
    //? if >=1.21.5 {
    /*@Override
    public void inventoryTick(net.minecraft.world.item.ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity entity, @org.jetbrains.annotations.Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        com.kasperstudios.kvr.power.GlassesPowerTicker.tickCarried(stack, level, entity);
    }
    *///?} else {
    @Override
    public void inventoryTick(net.minecraft.world.item.ItemStack stack, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        com.kasperstudios.kvr.power.GlassesPowerTicker.tickCarried(stack, level, entity);
    }
    //?}
}
