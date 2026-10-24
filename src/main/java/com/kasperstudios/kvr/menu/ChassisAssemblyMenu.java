package com.kasperstudios.kvr.menu;

import com.kasperstudios.kvr.hardware.AssemblyRules;
import com.kasperstudios.kvr.hardware.ComponentTier;
import com.kasperstudios.kvr.hardware.ComponentType;
import com.kasperstudios.kvr.hardware.GlassesPaint;
import com.kasperstudios.kvr.items.ChassisItem;
import com.kasperstudios.kvr.items.ComponentItem;
import com.kasperstudios.kvr.items.GlassesItem;
import com.kasperstudios.kvr.items.KVRChassisData;
import com.kasperstudios.kvr.items.KVRContent;
import com.kasperstudios.kvr.items.KVRPaintData;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Assembly menu: a five-slot chest that lives inside the chassis the player right-clicked.
 *
 * <p>The chassis item is the inventory (its {@link KVRChassisData} partial build is mirrored into a
 * {@link SimpleContainer} for the duration of the screen and written back on every change), so modules
 * placed here are physically inside the chassis stack, and closing the screen loses nothing: the
 * container is the item. When the fifth module type lands, the chassis turns into the assembled
 * glasses - the same rule that applied on the item itself.
 */
public class ChassisAssemblyMenu extends AbstractContainerMenu {

    public static final int MODULE_SLOTS = ComponentType.values().length;

    private final ItemStack chassisStack;
    private final SimpleContainer modules;
    private final int chassisSlotIndex;
    private final Player owner;
    private boolean finished;

    public ChassisAssemblyMenu(int windowId, Inventory playerInventory) {
        this(windowId, playerInventory, new SimpleContainer(MODULE_SLOTS), ItemStack.EMPTY, -1);
    }

    /**
     * Factory the menu type uses: resolves the chassis the player right-clicked (selected hotbar slot
     * or offhand) and binds the screen to that stack.
     */
    public static ChassisAssemblyMenu forPlayer(int windowId, Inventory playerInventory) {
        Player player = playerInventory.player;
        ItemStack chassis = ItemStack.EMPTY;
        // Inventory.selected became private in 1.21.11
        //? if >=1.21.11 {
        /*int index = player.getInventory().getSelectedSlot();
        *///?} else {
        int index = player.getInventory().selected;
        //?}

        ItemStack selected = player.getInventory().getItem(index);
        if (selected.getItem() instanceof ChassisItem) {
            chassis = selected;
        } else {
            // offhand lives at container index 40 before 1.21.11 as well; getItem stays public
            ItemStack offhand = player.getInventory().getItem(40);
            if (offhand.getItem() instanceof ChassisItem) {
                chassis = offhand;
                index = OFFHAND_INDEX;
            }
        }
        return new ChassisAssemblyMenu(windowId, playerInventory, new SimpleContainer(MODULE_SLOTS), chassis, index);
    }

    protected ChassisAssemblyMenu(int windowId, Inventory playerInventory, SimpleContainer modules,
                                  ItemStack chassisStack, int chassisSlotIndex) {
        super(KVRMenus.chassisAssembly(), windowId);
        this.modules = modules;
        this.chassisStack = chassisStack;
        this.chassisSlotIndex = chassisSlotIndex;
        this.owner = playerInventory.player;
        // Mirror the chassis contents into the screen once it opens
        if (!chassisStack.isEmpty()) {
            KVRChassisData.PartialBuild partial = KVRChassisData.read(chassisStack);
            if (partial != null) {
                for (ComponentType type : ComponentType.values()) {
                    ComponentTier tier = partial.tierOf(type);
                    if (tier != null) {
                        modules.setItem(type.ordinal(), new ItemStack(KVRContent.component(type, tier)));
                    }
                }
            }
        }
        // Without a listener the module slots never re-write the item; only the modules are observed
        // (the chassis stack is written back by persist(), which would recurse if observed)
        modules.addListener(ignored -> persist());

        // Five module slots with spacing (gap = 6px): 5 * 18 + 4 * 6 = 114px wide.
        // Centered horizontally: (176 - 114) / 2 = 31px.
        // Module Y: 20px, Labels Y: 41px.
        // Inventory starts at Y = 62px, Inventory Label at Y = 51px.
        ComponentType[] types = ComponentType.values();
        for (int i = 0; i < types.length; i++) {
            ComponentType type = types[i];
            int x = 31 + i * 24;
            int y = 20;
            addSlot(new Slot(modules, type.ordinal(), x, y) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof ComponentItem item && item.componentType() == type;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 62 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 120));
        }
    }

    /** Writes the screen contents back into the chassis stack (or replaces it with glasses). */
    private void persist() {
        if (finished || chassisStack.isEmpty()) {
            return;
        }
        ChassisItem chassis = (ChassisItem) chassisStack.getItem();
        java.util.Map<ComponentType, ComponentTier> tiers = new java.util.EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            ItemStack module = modules.getItem(type.ordinal());
            if (module.getItem() instanceof ComponentItem component) {
                tiers.put(type, component.tier());
            }
        }
        AssemblyRules.Result result = AssemblyRules.evaluate(chassis.tier(), tiers);
        if (result.ok() && chassisSlotIndex >= 0) {
            // the build is complete: the chassis stack itself becomes the glasses
            ItemStack glasses = new ItemStack(KVRContent.glasses());
            com.kasperstudios.kvr.items.KVRGlassesData.write(glasses, result.build());
            GlassesPaint paint = KVRPaintData.paint(chassisStack);
            if (paint != null) {
                KVRPaintData.paint(glasses, paint);
            }
            setChassisStack(glasses);
            // burn the module slots: they are inside the glasses now
            for (ComponentType type : ComponentType.values()) {
                modules.setItem(type.ordinal(), ItemStack.EMPTY);
            }
            finished = true;
            return;
        }
        KVRChassisData.PartialBuild partial = new KVRChassisData.PartialBuild();
        for (java.util.Map.Entry<ComponentType, ComponentTier> entry : tiers.entrySet()) {
            partial.set(entry.getKey(), entry.getValue());
        }
        if (partial.isEmpty()) {
            KVRChassisData.clear(chassisStack);
        } else {
            KVRChassisData.write(chassisStack, partial);
        }
    }

    private void setChassisStack(ItemStack replacement) {
        if (chassisSlotIndex >= 0 && chassisSlotIndex < owner.getInventory().getContainerSize()) {
            owner.getInventory().setItem(chassisSlotIndex, replacement);
        } else if (chassisSlotIndex == OFFHAND_INDEX) {
            owner.getInventory().setItem(40, replacement);
        } else {
            owner.getInventory().placeItemBackInInventory(replacement);
        }
    }

    private static final int OFFHAND_INDEX = -2;

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        persist();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (index < MODULE_SLOTS) {
            // a module goes back to the player inventory
            if (!moveItemStackTo(stack, MODULE_SLOTS, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // a module from the inventory goes into its slot
            int target = -1;
            if (stack.getItem() instanceof ComponentItem component) {
                target = component.componentType().ordinal();
            }
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.copy();
    }

    @Override
    public boolean stillValid(Player player) {
        // The screen is bound to one chassis stack: once it became the glasses (or vanished),
        // the screen must close immediately - otherwise the player could pull modules out of a
        // build that already turned into glasses and duplicate them.
        return !finished && chassisStack.getItem() instanceof ChassisItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        persist();
    }
}
