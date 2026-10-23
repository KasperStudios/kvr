package com.kasperstudios.kvr.client;

import com.kasperstudios.kvr.hardware.TierUpgrade;
import com.kasperstudios.kvr.menu.UpgradeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Client panel of the upgrade station: same glass plate, with the price line under the slots. */
public class UpgradeScreen extends AbstractContainerScreen<UpgradeMenu> {

    private static final int PANEL = 0xE610121B;
    private static final int PANEL_EDGE = 0xFF3AB3DA;
    private static final int RESULT_GLOW = 0x553AB3DA;

    public UpgradeScreen(UpgradeMenu menu, Inventory inventory, Component title) {
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
            int colour = slot.index == UpgradeMenu.RESULT_SLOT ? RESULT_GLOW : 0x33FFFFFF;
            graphics.fill(sx, sy, sx + 18, sy + 18, colour);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        TierUpgrade upgrade = menu.currentUpgrade();
        String price = upgrade == null
                ? "положите деталь и шаблон"
                : "цена: " + upgrade.oreCost() + " UpOre (есть " + menu.availableOre() + ")";
        graphics.drawString(font, price, 8, 62, 0x9AA7B8, false);
    }
}
