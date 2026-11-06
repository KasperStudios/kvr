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
 *     // inventoryTick → GlassesPowerTicker.tickCarried(...) stays as it is
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
}
