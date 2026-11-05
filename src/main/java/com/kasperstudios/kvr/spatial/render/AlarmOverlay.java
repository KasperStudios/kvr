package com.kasperstudios.kvr.spatial.render;

import com.kasperstudios.kvr.power.client.PowerClient;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Approved Note 7 effect: a blood-red vignette around the view, pulsing with the window strobe. It
 * is a screen-space effect like vanilla's vignette or freeze overlay, not a HUD panel — all real UI
 * stays in the world. Drawn via {@code KVRClientProvider.onHudRender}.
 */
public final class AlarmOverlay {

    private static final int BAND = 48;

    private AlarmOverlay() {}

    public static void render(GuiGraphics g) {
        if (!PowerClient.overloading()) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int peak = PowerClient.strobeOn() ? 0xB0 : 0x50;
        for (int i = 0; i < BAND; i++) {
            float k = 1f - i / (float) BAND;
            int a = Math.round(peak * k * k);
            int c = (a << 24) | 0xB0101A;
            g.fill(0, i, w, i + 1, c);
            g.fill(0, h - i - 1, w, h - i, c);
            g.fill(i, BAND, i + 1, h - BAND, c);
            g.fill(w - i - 1, BAND, w - i, h - BAND, c);
        }
    }
}
