package com.kasperstudios.kvr.desktop.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Named cursor shapes (cursor-shape names: "default", "text", "pointer", "crosshair", "wait", "ew-resize",
 * …) drawn from fills, for apps that ask for a shape instead of sending a cursor image. GUI pixels,
 * hotspot at (x, y). White with a dark outline: visible on light and dark app content.
 */
final class DesktopCursors {

    private static final int OUT = 0xFF0F172A;
    private static final int IN = 0xFFFFFFFF;

    private DesktopCursors() {}

    static void draw(GuiGraphics g, String name, int x, int y) {
        switch (name) {
            case "text", "vertical-text" -> beam(g, x, y);
            case "crosshair", "cell" -> cross(g, x, y);
            case "ew-resize", "col-resize", "e-resize", "w-resize" -> horizontal(g, x, y);
            case "ns-resize", "row-resize", "n-resize", "s-resize" -> vertical(g, x, y);
            case "wait", "progress" -> busy(g, x, y, name.equals("progress"));
            case "not-allowed", "no-drop" -> {
                arrow(g, x, y);
                g.fill(x + 5, y + 7, x + 10, y + 8, 0xFFF43F5E);
            }
            case "pointer", "grab", "grabbing", "move", "all-scroll" -> {
                arrow(g, x, y);
                g.fill(x + 5, y + 7, x + 8, y + 10, OUT); // little "hand" knob: something clickable
                g.fill(x + 6, y + 8, x + 7, y + 9, 0xFF67E8F9);
            }
            default -> arrow(g, x, y);
        }
    }

    /** Classic arrow, 7×10, tip at the hotspot. */
    private static void arrow(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 8; row++) {
            int w = row + 1;
            g.fill(x, y + row, x + w + 1, y + row + 1, OUT);
            if (row > 0 && w > 1) g.fill(x + 1, y + row, x + w, y + row + 1, IN);
        }
        g.fill(x, y + 8, x + 4, y + 9, OUT);
        g.fill(x + 2, y + 9, x + 4, y + 11, OUT);
        g.fill(x + 3, y + 9, x + 4, y + 10, IN);
    }

    private static void beam(GuiGraphics g, int x, int y) {
        g.fill(x - 2, y - 5, x + 3, y + 6, OUT);
        g.fill(x - 1, y - 4, x + 2, y - 3, IN);
        g.fill(x - 1, y + 3, x + 2, y + 4, IN);
        g.fill(x, y - 4, x + 1, y + 4, IN);
    }

    private static void cross(GuiGraphics g, int x, int y) {
        g.fill(x - 5, y - 1, x + 6, y + 2, OUT);
        g.fill(x - 1, y - 5, x + 2, y + 6, OUT);
        g.fill(x - 4, y, x + 5, y + 1, IN);
        g.fill(x, y - 4, x + 1, y + 5, IN);
    }

    private static void horizontal(GuiGraphics g, int x, int y) {
        g.fill(x - 6, y - 1, x + 7, y + 2, OUT);
        g.fill(x - 5, y, x + 6, y + 1, IN);
        g.fill(x - 4, y - 2, x - 3, y + 3, OUT);
        g.fill(x + 4, y - 2, x + 5, y + 3, OUT);
    }

    private static void vertical(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 6, x + 2, y + 7, OUT);
        g.fill(x, y - 5, x + 1, y + 6, IN);
        g.fill(x - 2, y - 4, x + 3, y - 3, OUT);
        g.fill(x - 2, y + 4, x + 3, y + 5, OUT);
    }

    /** Four dots, one lit, rotating (progress = arrow + dots). */
    private static void busy(GuiGraphics g, int x, int y, boolean withArrow) {
        int ox = x;
        int oy = y;
        if (withArrow) {
            arrow(g, x, y);
            ox = x + 8;
            oy = y + 8;
        }
        int lit = (int) (System.currentTimeMillis() / 150 % 4);
        int[][] dots = {{0, -3}, {3, 0}, {0, 3}, {-3, 0}};
        for (int i = 0; i < 4; i++) {
            int dx = ox + dots[i][0];
            int dy = oy + dots[i][1];
            g.fill(dx - 1, dy - 1, dx + 2, dy + 2, OUT);
            g.fill(dx, dy, dx + 1, dy + 1, i == lit ? 0xFF67E8F9 : IN);
        }
    }
}
