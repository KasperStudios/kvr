package com.kasperstudios.kvr.spatial.window;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 0034a — the window [X] as ONE self-contained piece: geometry, hit test, drawing. Used by
 * {@code SpatialInput} (hit test BEFORE the click reaches the window), {@link SpatialWindow#renderChrome} (drawing)
 * and, in the repo, by {@code WindowChrome} if it draws the chrome instead.
 *
 * <p>Why the click did not close windows (Creator's test): the [X] was handled deep inside the window's click path
 * ({@code SpatialWindow.click} / repo {@code WindowChrome}). On some windows that path is never reached for the
 * corner — the chrome is skipped when {@code hasSettings()} is false, an overlay/modal or the app's own
 * {@code mouseClicked} eats the click first, or {@code closeRequested} is set but never processed. Now
 * {@code SpatialInput} tests {@link #hit} first and calls {@code WindowManager.close(w)} directly — no flags, no
 * tick delay, nothing in between.
 */
public final class CloseButton {

    /** Drawn size in window pixels (16 px = 4 cm at 400 px/m). */
    public static final int SIZE = 16;
    public static final int MARGIN = 3;
    /** Extra hit area around the tile: a hand ray is less precise than a mouse. */
    public static final int HIT_PAD = 4;

    private CloseButton() {}

    public static boolean shown(SpatialWindow w) {
        return w.hasCloseButton();
    }

    public static int x(SpatialWindow w) {
        return w.width() - SIZE - MARGIN;
    }

    public static int y(SpatialWindow w) {
        return MARGIN;
    }

    /** {@code px/py} in window pixels (as in {@code RayHit}). The pad may reach past the window edge — fine. */
    public static boolean hit(SpatialWindow w, double px, double py) {
        if (!shown(w)) return false;
        int x = x(w) - HIT_PAD;
        int y = y(w) - HIT_PAD;
        int s = SIZE + HIT_PAD * 2;
        return px >= x && py >= y && px < x + s && py < y + s;
    }

    /** Opaque dark tile, light rim, 2 px white cross; red when hovered. Call AFTER the window content. */
    public static void render(GuiGraphics g, SpatialWindow w, int mx, int my) {
        if (!shown(w)) return;
        int x = x(w);
        int y = y(w);
        boolean hot = mx >= 0 && hit(w, mx, my);
        g.fill(x, y, x + SIZE, y + SIZE, hot ? 0xFFE11D48 : 0xF00B1220);
        int rim = hot ? 0xFFFFE4E6 : 0xB0FFFFFF;
        g.fill(x, y, x + SIZE, y + 1, rim);
        g.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, rim);
        g.fill(x, y, x + 1, y + SIZE, rim);
        g.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, rim);
        int pad = 5;
        int n = SIZE - pad * 2;
        for (int i = 0; i < n; i++) {
            int px = x + pad + i;
            g.fill(px, y + pad + i - 1, px + 1, y + pad + i + 1, 0xFFFFFFFF);               // \
            g.fill(px, y + SIZE - pad - i - 2, px + 1, y + SIZE - pad - i, 0xFFFFFFFF);     // /
        }
    }
}
