package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Empty spatial monitor / secondary screen (ADR-001 Gesture 5: New Space / CONCEPT §1.4).
 * Creates an independent floating space in 3D that can be pinned to blocks or the body.
 */
public final class WorkspaceWindow extends SpatialWindow {

    private static final int W = 280;
    private static final int H = 180;
    private static int counter = 1;

    public WorkspaceWindow(SpatialAnchor anchor) {
        super("kvr:space_" + (counter++), Kind.SYSTEM, W, H, anchor);
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.gesture.new_space");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 10, 9, GlassStyle.TEXT);
        g.fill(10, 24, width - 10, 25, 0x30FFFFFF);

        GlassStyle.centered(g, Component.translatable("kvr.space.hint"), width / 2, height / 2 - 20, GlassStyle.MUTED);

        // Action button to open App Launcher
        int bw = 160;
        int bh = 22;
        int bx = (width - bw) / 2;
        int by = height / 2 + 5;
        boolean hot = GlassStyle.inside(mx, my, bx, by, bw, bh);
        GlassStyle.panel(g, bx, by, bw, bh, hot);
        GlassStyle.centered(g, Component.translatable("kvr.space.pick_app"), width / 2, by + 6, GlassStyle.ACCENT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bw = 160;
        int bh = 22;
        int bx = (width - bw) / 2;
        int by = height / 2 + 5;
        if (GlassStyle.inside(mx, my, bx, by, bw, bh)) {
            KVRSpatial.config().apps().openLauncher();
            requestClose();
            return true;
        }
        return false;
    }
}
