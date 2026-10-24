package com.kasperstudios.kvr.client;

import com.kasperstudios.kvr.menu.ChassisAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Client panel of the chassis' own five-slot chest.
 *
 * <p>Every slot - modules and the player inventory - gets a visible box, and the module labels sit
 * under their slot instead of colliding with it.
 */
public class ChassisAssemblyScreen extends AbstractContainerScreen<ChassisAssemblyMenu> {

    private static final int PANEL = 0xF0101218;
    private static final int PANEL_EDGE = 0xFF3AB3DA;
    private static final int SLOT_FILL = 0xFF1B2430;
    private static final int SLOT_EDGE = 0xFF2E3A48;
    private static final int MODULE_EDGE = 0xFF3AB3DA;
    private static final int LABEL = 0x9AA7B8;

    public ChassisAssemblyScreen(ChassisAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 140;
        this.inventoryLabelY = 45;
        this.titleLabelY = 6;
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
            boolean module = slot.index < ChassisAssemblyMenu.MODULE_SLOTS;
            graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_FILL);
            // slot frame: 1px box
            graphics.fill(sx, sy, sx + 18, sy + 1, module ? MODULE_EDGE : SLOT_EDGE);
            graphics.fill(sx, sy + 17, sx + 18, sy + 18, module ? MODULE_EDGE : SLOT_EDGE);
            graphics.fill(sx, sy, sx + 1, sy + 18, module ? MODULE_EDGE : SLOT_EDGE);
            graphics.fill(sx + 17, sy, sx + 18, sy + 18, module ? MODULE_EDGE : SLOT_EDGE);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        String[] labels = {"CPU", "GPU", "RAM", "DSK", "BAT"};
        for (int i = 0; i < labels.length; i++) {
            int lx = 43 + i * 18;
            int ly = 40 + (i / 3) * 18;
            graphics.drawCenteredString(font, labels[i], lx + 9, ly, LABEL);
        }
    }
}
