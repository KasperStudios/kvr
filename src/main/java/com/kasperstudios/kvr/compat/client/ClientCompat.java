package com.kasperstudios.kvr.compat.client;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only version seams (0026). Kept apart from {@code VersionCompat} so a dedicated server never
 * loads a class whose signatures mention client types.
 */
public final class ClientCompat {

    private ClientCompat() {}

    /**
     * Camera position in the world. VERIFY on 1.21.11: if {@code getPosition()} was renamed
     * (e.g. to {@code position()}), only this line changes — four renderers call it.
     */
    public static Vec3 cameraPos(Camera camera) {
        return camera.getPosition();
    }

    /**
     * The inventory-style live entity (virtual mirror), called with the 1.20.2+ arguments everywhere:
     * box {@code x1,y1 – x2,y2} in GUI px, {@code scale} px per block, {@code yOffset} in blocks, absolute mouse.
     *
     * <p>1.20.1 has only {@code (g, x, y, scale, mouseX, mouseY, entity)}: {@code x,y} = the FEET, mouse = offset
     * from the look-at point. Converted so the entity sits and turns the same as on 1.20.2+, where it is centred
     * in the box ({@code bbHeight / 2 + yOffset} below the centre) and looks from the box centre.
     */
    public static void renderEntityFollowsMouse(GuiGraphics g, int x1, int y1, int x2, int y2, int scale, float yOffset,
                                                float mouseX, float mouseY, LivingEntity entity) {
        //? if >=1.20.2 {
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
        //?} else {
        /*int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int feet = cy + Math.round((entity.getBbHeight() / 2f + yOffset) * scale);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, scale, cx - mouseX, cy - mouseY, entity);
        *///?}
    }
}
