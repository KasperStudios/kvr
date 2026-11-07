package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.KVR;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Dock icons of installed packages ({@code "icon": "assets/icon.png"} in config.json). The PNG is decoded
 * once, sampled down to 16×16 and drawn from fills (one per run of equal pixels) — the same technique as
 * {@code AppIcons}, so no dynamic GPU texture and no version-specific blit API is needed.
 */
public final class PackageIcons {

    private static final int SIZE = 16;
    private static final int MAX_DIM = 256;
    private static final Map<String, int[]> ICONS = new ConcurrentHashMap<>();

    private PackageIcons() {}

    /** @param key dock id ({@code weather.station} or {@code dev:weather.station}); null png removes the icon */
    public static void register(String key, byte[] png) {
        if (png == null) {
            ICONS.remove(key);
            return;
        }
        try (NativeImage img = NativeImage.read(new ByteArrayInputStream(png))) {
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 1 || h < 1 || w > MAX_DIM || h > MAX_DIM) throw new IOException("icon must be 1..256 px, got " + w + "x" + h);
            int[] px = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) px[y * SIZE + x] = argb(img, x * w / SIZE, y * h / SIZE);
            }
            ICONS.put(key, px);
        } catch (IOException | RuntimeException e) {
            KVR.logger().warn("KVR apps: icon of {} unreadable: {}", key, e.toString());
            ICONS.remove(key);
        }
    }

    public static boolean has(String key) {
        return ICONS.containsKey(key);
    }

    /** @return false if there is no icon for {@code key} (caller draws its fallback) */
    public static boolean draw(GuiGraphics g, String key, int x, int y) {
        int[] px = ICONS.get(key);
        if (px == null) return false;
        for (int row = 0; row < SIZE; row++) {
            int col = 0;
            while (col < SIZE) {
                int c = px[row * SIZE + col];
                int end = col + 1;
                while (end < SIZE && px[row * SIZE + end] == c) end++;
                if ((c >>> 24) >= 16) g.fill(x + col, y + row, x + end, y + row + 1, c);
                col = end;
            }
        }
        return true;
    }

    private static int argb(NativeImage img, int x, int y) {
        //? if >=1.21.2 {
        /*return img.getPixel(x, y);
        *///?} else {
        int abgr = img.getPixelRGBA(x, y); // despite the name: ABGR
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
        //?}
    }
}
