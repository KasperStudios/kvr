package com.kasperstudios.kvr.menu;

import com.kasperstudios.kvr.hardware.AssemblyRules;
import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.GlassesBuild;
import com.kasperstudios.kvr.items.ChassisItem;
import com.kasperstudios.kvr.items.ComponentItem;
import com.kasperstudios.kvr.items.GlassesItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Assembly menu: one chassis slot, five module slots, one take-only result slot and the player inventory.
 *
 * <p>Every item movement is a vanilla container transaction, which is why this is an
 * {@link AbstractContainerMenu} and not a hand-written screen: no custom packets, no dupe surface. What
 * the six items form is decided by {@link AssemblyRules}.
 */
public class ChassisAssemblyMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = 6;
    public static final int RESULT_SLOT = INPUT_SLOTS;

    private final Container inputs;
    private final Container resultContainer;

    public ChassisAssemblyMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new SimpleContainer(INPUT_SLOTS), new SimpleContainer(1));
    }

    protected ChassisAssemblyMenu(int windowId, Inventory playerInventory, Container inputs, Container resultContainer) {
        super(KVRMenus.chassisAssembly(), windowId);
        this.inputs = inputs;
        this.resultContainer = resultContainer;

        addSlot(new Slot(inputs, 0, 27, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ChassisItem;
            }
        });

        ComponentType[] order = ComponentType.values();
        for (int i = 0; i < order.length; i++) {
            ComponentType type = order[i];
            addSlot(new Slot(inputs, i + 1, 62 + (i % 2) * 18, 20 + (i / 2) * 22) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ComponentItem item && item.componentType() == type;
                }
            });
        }

        addSlot(new Slot(resultContainer, 0, 148, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                consumeInputs();
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        refreshResult();
    }

    private void consumeInputs() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack stack = inputs.getItem(slot);
            if (!stack.isEmpty()) {
                stack.shrink(1);
                inputs.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
    }

    /** Recomputes what the six items in the slots assemble into. */
    public void refreshResult() {
        AssemblyRules.Result result = AssemblyRules.evaluate(currentChassisTier(), currentParts());
        resultContainer.setItem(0, result.ok() ? GlassesItem.of(result.build()) : ItemStack.EMPTY);
    }

    public GlassesBuild currentBuild() {
        AssemblyRules.Result result = AssemblyRules.evaluate(currentChassisTier(), currentParts());
        return result.ok() ? result.build() : null;
    }

    private ComponentTier currentChassisTier() {
        return inputs.getItem(0).getItem() instanceof ChassisItem chassis ? chassis.tier() : null;
    }

    private Map<ComponentType, ComponentTier> currentParts() {
        Map<ComponentType, ComponentTier> parts = new EnumMap<>(ComponentType.class);
        for (int i = 0; i < ComponentType.values().length; i++) {
            if (inputs.getItem(i + 1).getItem() instanceof ComponentItem component) {
                parts.put(component.componentType(), component.tier());
            }
        }
        return parts;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        refreshResult();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, RESULT_SLOT + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INPUT_SLOTS) {
            if (!moveItemStackTo(stack, INPUT_SLOTS + 1, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = targetInputSlot(stack);
            if (target >= 0 && moveItemStackTo(stack, target, target + 1, false)) {
                // moved into its dedicated slot
            } else if (!moveItemStackTo(stack, RESULT_SLOT + 1, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private int targetInputSlot(ItemStack stack) {
        if (stack.getItem() instanceof ChassisItem) {
            return 0;
        }
        if (stack.getItem() instanceof ComponentItem component) {
            return 1 + component.componentType().ordinal();
        }
        return -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, inputs);
        clearContainer(player, resultContainer);
    }
}
