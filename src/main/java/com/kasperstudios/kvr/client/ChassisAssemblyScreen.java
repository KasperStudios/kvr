package com.kasperstudios.kvr.client;

import com.kasperstudios.kvr.menu.ChassisAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Client side of the assembly menu: the vanilla container screen handles clicks and sync, this class
 * only paints the panel - a dark glassmorphism plate with slots floating on it, no textures involved.
 */
public class ChassisAssemblyScreen extends AbstractContainerScreen<ChassisAssemblyMenu> {

    private static final int PANEL = 0xE610121B;
    private static final int PANEL_EDGE = 0xFF3AB3DA;
    private static final int RESULT_GLOW = 0x553AB3DA;

    public ChassisAssemblyScreen(ChassisAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        graphics.fill(x, y, x + imageWidth, y + 1, PANEL_EDGE);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_EDGE);
        graphics.fill(x, y, x + 1, y + imageHeight, PANEL_EDGE);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_EDGE);

        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            int colour = slot.index == ChassisAssemblyMenu.RESULT_SLOT ? RESULT_GLOW : 0x33FFFFFF;
            graphics.fill(sx, sy, sx + 18, sy + 18, colour);
        }
    }
}
