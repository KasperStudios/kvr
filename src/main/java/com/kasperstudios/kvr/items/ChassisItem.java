package com.kasperstudios.kvr.items;

import com.kasperstudios.kvr.hardware.AssemblyRules;
import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.GlassesBuild;
import com.kasperstudios.kvr.hardware.GlassesPaint;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The shell. Its tier caps every module that can be installed (ADR-003 3.1).
 *
 * <p>The chassis is itself the assembly container, exactly like a bundle: a module on the cursor
 * clicked into the chassis stacks it inside; when all five module types are present the stack itself
 * becomes the assembled glasses. No crafting menu, no dedicated slots - the item the player is
 * holding is the GUI.
 */
public class ChassisItem extends Item {

    private final ComponentTier tier;

    public ChassisItem(ComponentTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ComponentTier tier() {
        return tier;
    }

    // ---------- module insertion: cursor stack clicked onto the chassis (right click) ----------

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack chassisStack, ItemStack moduleStack, Slot slot,
                                             ClickAction action, Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || moduleStack.isEmpty() || !slot.allowModification(player)) {
            return false;
        }
        if (!(moduleStack.getItem() instanceof ComponentItem component)) {
            return false;
        }
        KVRChassisData.PartialBuild partial = KVRChassisData.read(chassisStack);
        if (partial == null) {
            return false;
        }
        ComponentTier already = partial.tierOf(component.componentType());
        if (already != null) {
            return false; // slot of this type is taken - the click does nothing
        }
        if (!com.kasperstudios.kvr.hardware.ChassisRules.canInstall(tier, component.tier())) {
            return false; // module above the shell tier is rejected
        }
        ItemStack inserted = moduleStack.split(1);
        partial.set(component.componentType(), component.tier());
        applyBuild(chassisStack, partial, player);
        return true;
    }

    // ---------- chassis clicked onto an empty cursor slot: take one module out ----------

    @Override
    public boolean overrideStackedOnOther(ItemStack chassisStack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        KVRChassisData.PartialBuild partial = KVRChassisData.read(chassisStack);
        if (partial == null || partial.isEmpty()) {
            return false;
        }
        KVRChassisData.PartialBuild.Module last = partial.removeLast();
        applyBuild(chassisStack, partial, player);
        // hand the module back to the player instead of dropping it
        player.getInventory().placeItemBackInInventory(
                new ItemStack(KVRContent.component(last.type(), last.tier())));
        return true;
    }

    /** Applies the partial build, or turns the chassis into glasses when the build is complete. */
    private void applyBuild(ItemStack chassisStack, KVRChassisData.PartialBuild partial, Player player) {
        AssemblyRules.Result result = AssemblyRules.evaluate(tier, partial.tiers());
        if (result.ok()) {
            ItemStack glasses = new ItemStack(KVRContent.glasses());
            com.kasperstudios.kvr.items.KVRGlassesData.write(glasses, result.build());
            GlassesPaint paint = KVRPaintData.paint(chassisStack);
            if (paint != null) {
                KVRPaintData.paint(glasses, paint);
            }
            chassisStack.shrink(1);
            player.getInventory().placeItemBackInInventory(glasses);
        } else {
            KVRChassisData.write(chassisStack, partial);
        }
    }
}
