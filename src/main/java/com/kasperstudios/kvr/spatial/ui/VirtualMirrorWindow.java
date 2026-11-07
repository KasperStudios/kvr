package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Spatial Virtual Mirror (ADR-001 Gesture 4: Virtual World Space / CONCEPT §1.4).
 * Floating mirror displaying the player's 3D avatar, equipped armor, worn KVR glasses, and skin.
 */
public final class VirtualMirrorWindow extends SpatialWindow {

    private static final int W = 180;
    private static final int H = 250;

    public VirtualMirrorWindow(SpatialAnchor anchor) {
        super("kvr:virtual_mirror", Kind.SYSTEM, W, H, anchor);
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.mirror.title");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 10, 9, GlassStyle.TEXT);
        g.fill(10, 24, width - 10, 25, 0x30FFFFFF);

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Draw 3D avatar centered inside the mirror frame
        int cx = width / 2;
        int cy = height / 2 + 30;
        int scale = 70;
        float mouseX = mx >= 0 ? mx : cx;
        float mouseY = my >= 0 ? my : cy - 40;

        try {
            com.kasperstudios.kvr.compat.client.ClientCompat.renderEntityFollowsMouse(
                g,
                cx - 50, cy - 100, cx + 50, cy + 20,
                scale,
                0.0625f,
                mouseX, mouseY,
                player
            );
        } catch (RuntimeException ignored) {
            // Guard against render crashes during dimension switches
        }

        // Bottom status badge: Player name and live battery readout
        GlassStyle.centered(g, Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.BOLD), cx, height - 28, GlassStyle.TEXT);
        int pct = com.kasperstudios.kvr.power.GlassesPower.percent(com.kasperstudios.kvr.power.client.PowerClient.worn());
        GlassStyle.centered(g, Component.literal("\u26a1 " + pct + "%").withStyle(ChatFormatting.AQUA), cx, height - 16, GlassStyle.ACCENT);
    }
}
