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
 * <p>Vanilla proportions (176px wide), inset slot boxes with a light top/left edge and dark
 * bottom/right edge - the classic bevel that reads as a slot - and one centred module row with the
 * labels beneath it.
 */
public class ChassisAssemblyScreen extends AbstractContainerScreen<ChassisAssemblyMenu> {

    private static final int PANEL = 0xF00E131B;
    private static final int PANEL_EDGE = 0xFF2E4A5A;
    private static final int SLOT_HL = 0xFF37424E;   // bevel light edge (top/left)
    private static final int SLOT_SH = 0xFF10161D;   // bevel dark edge (bottom/right)
    private static final int SLOT_BG = 0xFF182129;   // slot interior
    private static final int MODULE_HL = 0xFF27596B; // module slots tinted cyan-ish
    private static final int LABEL = 0xFF7FA3B8;

    public ChassisAssemblyScreen(ChassisAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 148;
        this.titleLabelY = 6;
        this.inventoryLabelY = 51;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // panel + soft outer edge
        graphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0x664A3A48);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        graphics.fill(x, y, x + imageWidth, y + 2, PANEL_EDGE);
        graphics.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, PANEL_EDGE);
        graphics.fill(x, y, x + 2, y + imageHeight, PANEL_EDGE);
        graphics.fill(x + imageWidth - 2, y, x + imageWidth, y + imageHeight, PANEL_EDGE);

        for (Slot slot : menu.slots) {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1,
                    slot.index < ChassisAssemblyMenu.MODULE_SLOTS);
        }
    }

    /** Inset slot box: interior + 1px bevel, cyan-tinted for module slots. */
    private void drawSlot(GuiGraphics graphics, int sx, int sy, boolean module) {
        int light = module ? MODULE_HL : SLOT_HL;
        graphics.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
        graphics.fill(sx, sy, sx + 18, sy + 1, light);          // top
        graphics.fill(sx, sy, sx + 1, sy + 18, light);          // left
        graphics.fill(sx, sy + 17, sx + 18, sy + 18, SLOT_SH);  // bottom
        graphics.fill(sx + 17, sy, sx + 18, sy + 18, SLOT_SH);  // right
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        String[] labels = {"CPU", "GPU", "RAM", "DSK", "BAT"};
        for (int i = 0; i < labels.length; i++) {
            // under the module slot: x = 31 + i * 24, center is x + 9 = 40 + i * 24
            graphics.drawCenteredString(font, labels[i], 40 + i * 24, 39, LABEL);
        }
    }
}
