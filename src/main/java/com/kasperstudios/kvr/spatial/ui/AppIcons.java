package com.kasperstudios.kvr.spatial.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 16×16 dock icons for the system apps, drawn from fills (pixel art in code) — no PNGs to generate,
 * crisp at the 2× window supersampling. Unknown ids return false and the caller draws the sprite.
 *
 * <p>Colours are fixed on purpose (they are app identities, like real launcher icons), dark outline
 * pixels keep them readable on light and dark backgrounds.
 */
public final class AppIcons {

    private static final int OUTLINE = 0xFF0F172A;

    private AppIcons() {}

    /** @return false when {@code appId} has no built-in icon */
    public static boolean draw(GuiGraphics g, String appId, int x, int y) {
        switch (appId) {
            case "notes" -> notes(g, x, y);
            case "calc" -> calc(g, x, y);
            case "messenger" -> messenger(g, x, y);
            case "kav" -> kav(g, x, y);
            case "hello" -> hello(g, x, y);
            case "desktop" -> desktop(g, x, y);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Yellow sticky note with ruled lines and a folded corner. */
    private static void notes(GuiGraphics g, int x, int y) {
        r(g, x, y, 2, 1, 12, 14, OUTLINE);
        r(g, x, y, 3, 2, 10, 12, 0xFFFDE68A);
        r(g, x, y, 10, 11, 3, 3, 0xFFF59E0B); // fold
        r(g, x, y, 3, 4, 10, 1, 0xFFEF4444); // margin header
        for (int i = 0; i < 3; i++) r(g, x, y, 5, 7 + i * 2, 7 - (i == 2 ? 3 : 0), 1, 0xFF92400E);
    }

    /** Calculator: dark body, cyan display, 3×3 keys, orange "=". */
    private static void calc(GuiGraphics g, int x, int y) {
        r(g, x, y, 2, 0, 12, 16, OUTLINE);
        r(g, x, y, 3, 1, 10, 14, 0xFF334155);
        r(g, x, y, 4, 2, 8, 3, 0xFF67E8F9);
        r(g, x, y, 9, 3, 2, 1, OUTLINE); // digits on the display
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean eq = row == 2 && col == 2;
                r(g, x, y, 4 + col * 3, 6 + row * 3, 2, 2, eq ? 0xFFF97316 : 0xFFE2E8F0);
            }
        }
    }

    /** Speech bubble with three dots. */
    private static void messenger(GuiGraphics g, int x, int y) {
        r(g, x, y, 1, 2, 14, 10, OUTLINE);
        r(g, x, y, 2, 3, 12, 8, 0xFF38BDF8);
        r(g, x, y, 3, 12, 4, 1, OUTLINE);
        r(g, x, y, 3, 13, 2, 1, OUTLINE);
        r(g, x, y, 4, 11, 2, 1, 0xFF38BDF8);
        for (int i = 0; i < 3; i++) r(g, x, y, 4 + i * 3, 6, 2, 2, 0xFFFFFFFF);
    }

    /** KAV antivirus: green shield with a white check mark. */
    private static void kav(GuiGraphics g, int x, int y) {
        int[][] rows = { // x0, width per row 0..15 (outline included)
            {2, 12}, {2, 12}, {2, 12}, {2, 12}, {2, 12}, {2, 12}, {2, 12}, {3, 10},
            {3, 10}, {4, 8}, {4, 8}, {5, 6}, {6, 4}, {7, 2}};
        for (int i = 0; i < rows.length; i++) r(g, x, y, rows[i][0], i + 1, rows[i][1], 1, OUTLINE);
        for (int i = 1; i < rows.length - 1; i++) r(g, x, y, rows[i][0] + 1, i + 1, rows[i][1] - 2, 1, 0xFF22C55E);
        r(g, x, y, 4, 7, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 5, 8, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 6, 9, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 7, 8, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 8, 7, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 9, 6, 1, 2, 0xFFFFFFFF);
        r(g, x, y, 10, 5, 1, 2, 0xFFFFFFFF);
    }

    /** Hello: a smiling yellow face. */
    private static void hello(GuiGraphics g, int x, int y) {
        int[][] rows = {{5, 6}, {3, 10}, {2, 12}, {2, 12}, {1, 14}, {1, 14}, {1, 14}, {1, 14}, {1, 14}, {1, 14}, {2, 12}, {2, 12}, {3, 10}, {5, 6}};
        for (int i = 0; i < rows.length; i++) r(g, x, y, rows[i][0], i + 1, rows[i][1], 1, OUTLINE);
        for (int i = 1; i < rows.length - 1; i++) r(g, x, y, rows[i][0] + 1, i + 1, rows[i][1] - 2, 1, 0xFFFACC15);
        r(g, x, y, 5, 5, 2, 2, OUTLINE);
        r(g, x, y, 9, 5, 2, 2, OUTLINE);
        r(g, x, y, 4, 9, 1, 1, OUTLINE);
        r(g, x, y, 11, 9, 1, 1, OUTLINE);
        r(g, x, y, 5, 10, 6, 1, OUTLINE);
    }

    /** PC apps: monitor on a stand. */
    private static void desktop(GuiGraphics g, int x, int y) {
        r(g, x, y, 1, 2, 14, 10, OUTLINE);
        r(g, x, y, 2, 3, 12, 8, 0xFF1D4ED8);
        r(g, x, y, 3, 4, 5, 2, 0xFF93C5FD); // window on the screen
        r(g, x, y, 7, 12, 2, 2, 0xFF94A3B8);
        r(g, x, y, 4, 14, 8, 1, 0xFF94A3B8);
    }

    private static void r(GuiGraphics g, int x, int y, int dx, int dy, int w, int h, int argb) {
        if ((argb >>> 24) == 0) return;
        g.fill(x + dx, y + dy, x + dx + w, y + dy + h, argb);
    }
}
