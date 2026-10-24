package com.kasperstudios.kvr.menu;

import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.TierUpgrade;
import com.kasperstudios.kvr.hardware.UpgradeRules;
import com.kasperstudios.kvr.items.ComponentItem;
import com.kasperstudios.kvr.items.KVRContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tier upgrade station: the previous-tier module, one tier upgrader template, and the result.
 *
 * <p>The price is 4/16/64/256 UpOre, which no vanilla slot can hold, so the ore is counted in the
 * player's inventory and deducted on take - using {@link UpgradeRules}, the same rules the UI reports.
 */
public class UpgradeMenu extends AbstractContainerMenu {

    public static final int PART_SLOT = 0;
    public static final int TEMPLATE_SLOT = 1;
    public static final int RESULT_SLOT = 2;

    private final Container inputs;
    private final Container resultContainer;
    private final Player owner;

    public UpgradeMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new SimpleContainer(2), new SimpleContainer(1),
                playerInventory.player);
    }

    protected UpgradeMenu(int windowId, Inventory playerInventory, Container inputs, Container resultContainer,
                          Player owner) {
        super(KVRMenus.upgrade(), windowId);
        this.inputs = inputs;
        this.resultContainer = resultContainer;
        // Without a listener the result slot is never recomputed - the screen simply stayed empty
        // addListener lives on SimpleContainer, not on the Container interface before 1.20.5.
        // Only the inputs are observed: the result container is written by refreshResult itself, and a
        // listener there would recurse refreshResult -> setChanged -> slotsChanged -> StackOverflow.
        ((SimpleContainer) inputs).addListener(this::slotsChanged);
        this.owner = owner;

        addSlot(new Slot(inputs, PART_SLOT, 27, 47) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ComponentItem;
            }
        });
        addSlot(new Slot(inputs, TEMPLATE_SLOT, 27, 20) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(KVRContent.tierUpgrader());
            }
        });
        addSlot(new Slot(resultContainer, 0, 120, 47) {
            @Override
            public void onTake(Player player, ItemStack stack) {
                // Taking the upgraded module must consume the part, the template and the ore once
                consume(player);
                super.onTake(player, stack);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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

    /** Upgrade described by the part currently in the input slot, or {@code null}. */
    public TierUpgrade currentUpgrade() {
        if (inputs.getItem(PART_SLOT).getItem() instanceof ComponentItem part && part.tier().next().isPresent()) {
            return TierUpgrade.of(part.componentType(), part.tier());
        }
        return null;
    }

    /**
     * UpOre (ingot form) available in the player's inventory.
     *
     * <p>Iterated by slot index: the {@code items}/{@code offhand} arrays are private from 1.21.11.
     */
    public int availableOre() {
        Inventory inventory = owner.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(KVRContent.upIngot())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean refreshing;

    public void refreshResult() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            doRefreshResult();
        } finally {
            refreshing = false;
        }
    }

    private void doRefreshResult() {
        TierUpgrade upgrade = currentUpgrade();
        if (upgrade == null) {
            resultContainer.setItem(0, ItemStack.EMPTY);
            return;
        }
        boolean hasTemplate = inputs.getItem(TEMPLATE_SLOT).is(KVRContent.tierUpgrader());
        // The station does not know which chassis the player will use, so shell room is not checked here
        UpgradeRules.Requirements requirements = new UpgradeRules.Requirements(true, hasTemplate, true);
        boolean ok = UpgradeRules.canUpgrade(upgrade, requirements, availableOre());
        resultContainer.setItem(0, ok ? new ItemStack(KVRContent.component(upgrade.type(), upgrade.to())) : ItemStack.EMPTY);
    }

    private void consume(Player player) {
        TierUpgrade upgrade = currentUpgrade();
        if (upgrade == null) {
            return;
        }
        shrink(inputs, PART_SLOT);
        shrink(inputs, TEMPLATE_SLOT);
        shrinkOre(player.getInventory(), upgrade.oreCost());
        player.getInventory().setChanged();
        refreshResult();
    }

    private static void shrink(Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        if (!stack.isEmpty()) {
            stack.shrink(1);
            container.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }

    private static void shrinkOre(Inventory inventory, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(KVRContent.upIngot())) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            remaining -= taken;
        }
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
        } else if (index < RESULT_SLOT) {
            if (!moveItemStackTo(stack, RESULT_SLOT + 1, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = stack.is(KVRContent.tierUpgrader()) ? TEMPLATE_SLOT
                    : stack.getItem() instanceof ComponentItem ? PART_SLOT : -1;
            if (target >= 0 && moveItemStackTo(stack, target, target + 1, false)) {
                // into its dedicated slot
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
