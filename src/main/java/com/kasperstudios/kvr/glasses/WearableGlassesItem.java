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

    /**
     * 0030 — the expansion slot of 0029 ({@link com.kasperstudios.kvr.rednet.GlassesModem}), handled like a bundle:
     * <ul>
     *   <li>RMB with a CC wireless / ender modem on the cursor over the glasses in any inventory → the modem goes in
     *       (one from the cursor stack). A slot that already holds a modem refuses (take it out first).</li>
     *   <li>RMB with an empty cursor over glasses that hold a modem → the modem lands on the cursor; the Rednet id stays
     *       on the glasses for the next modem.</li>
     *   <li>Anything else (or CC removed from the pack: the modem cannot be re-created) → vanilla behaviour.</li>
     * </ul>
     * Runs on both sides like the bundle: the client predicts, the server's result reaches it with the normal slot
     * sync (a fresh Rednet id is random, so the client briefly may show another id — the server's wins).
     * Signature is the same on 1.20.1 … 1.21.4; VERIFY on 1.21.11 (CHANGELOG 0029). Fully-qualified names: the
     * imports above are version-branched. Subclasses (GlassesItem, HackGlasses) with their own override: call
     * {@code super} first.
     */
    @Override
    public boolean overrideOtherStackedOnMe(net.minecraft.world.item.ItemStack glasses, net.minecraft.world.item.ItemStack cursor,
                                            net.minecraft.world.inventory.Slot slot, net.minecraft.world.inventory.ClickAction action,
                                            net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.SlotAccess access) {
        if (action != net.minecraft.world.inventory.ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        java.util.Optional<com.kasperstudios.kvr.rednet.GlassesModem.Kind> kind = com.kasperstudios.kvr.rednet.GlassesModem.kindOf(cursor);
        boolean hasModem = com.kasperstudios.kvr.rednet.GlassesModem.read(glasses).isPresent();
        if (kind.isPresent()) {
            if (hasModem) return true; // consumed, nothing changes: no accidental swap of glasses and modem
            com.kasperstudios.kvr.rednet.GlassesModem.install(glasses, kind.get());
            cursor.shrink(1);
            slot.setChanged();
            return true;
        }
        if (cursor.isEmpty() && hasModem) {
            net.minecraft.world.item.ItemStack modem = com.kasperstudios.kvr.rednet.GlassesModem.remove(glasses);
            if (modem.isEmpty()) return false; // CC not installed any more: the modem stays, RMB picks the glasses up
            access.set(modem);
            slot.setChanged();
            return true;
        }
        return false;
    }

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
