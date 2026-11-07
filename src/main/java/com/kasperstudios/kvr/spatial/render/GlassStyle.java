package com.kasperstudios.kvr.spatial.render;

import com.kasperstudios.kvr.config.KvrConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Visual language of KVR OS: glassmorphism panels from nine-slice GUI sprites
 * ({@code assets/kvr/textures/gui/sprites/*.png} + {@code .mcmeta} with {@code nine_slice} scaling),
 * so windows stay crisp at any size without custom shaders. The sprites are generated on the
 * KVR textures page (category "GUI-спрайты"). All drawing goes through {@link RenderCompat#sprite}.
 *
 * <p>Readability (0018): a window background is {@link #surface} = a dark backdrop (opacity from
 * {@code window_opacity} in kvr-client.toml / settings screen) under the glass sprite, and all text
 * goes through {@link #text} with a drop shadow. A bright sky or snow behind a window no longer eats
 * the letters. Never call {@code g.drawString(..., false)} directly in window code.
 */
public final class GlassStyle {

    public static final String NAMESPACE = "kvr";

    public static final ResourceLocation PANEL = id("panel");
    public static final ResourceLocation PANEL_HOVER = id("panel_hover");
    public static final ResourceLocation GRAB_HANDLE = id("grab_handle");
    public static final ResourceLocation ANCHOR_BODY = id("anchor_body");
    public static final ResourceLocation ANCHOR_WORLD = id("anchor_world");
    public static final ResourceLocation BATTERY = id("battery");

    /** Same PNG as a plain texture, for world quads (grab bar, pointer). */
    public static final ResourceLocation GRAB_HANDLE_TEXTURE = id("textures/gui/sprites/grab_handle.png");

    public static final int TEXT = 0xFFF1F5F9;
    /** Opaque since 0018: translucent text over translucent glass was the hardest to read. */
    public static final int MUTED = 0xFFB4C0CE;
    /**
     * Theme accent (0027: Settings → Personalisation). Deliberately NOT a compile-time constant any more, so every
     * use reads the current theme; refreshed by {@link #syncTheme()} at the start of each window's {@link #surface}.
     */
    public static int ACCENT = 0xFF67E8F9;
    public static final int OK = 0xFF4ADE80;
    public static final int DANGER = 0xFFF43F5E;
    public static final int PADDING = 8;
    /** RGB of the backdrop under window glass (deep slate); alpha comes from the config. */
    private static final int BACKDROP_RGB = 0x0B1220;

    private GlassStyle() {}

    public static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
        //?} else {
        /*return new ResourceLocation(NAMESPACE, path);
        *///?}
    }

    /** Border of the nine-slice sprites (must match their .mcmeta); 0 = stretch. Used on 1.20.1. */
    public static int nineSliceBorder(ResourceLocation sprite) {
        return switch (sprite.getPath()) {
            case "panel", "panel_hover" -> 5;
            case "grab_handle" -> 3;
            default -> 0;
        };
    }

    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        RenderCompat.sprite(g, sprite, x, y, w, h);
    }

    /** Glass sprite only (buttons, icon tiles, nested cards): blends over what is already there. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        RenderCompat.sprite(g, hovered ? PANEL_HOVER : PANEL, x, y, w, h);
    }

    /**
     * Background of a whole window or floating menu: the dark backdrop first (inset 2 px so it stays
     * inside the sprite's rounded corners), then the glass on top of it.
     */
    public static void surface(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        com.kasperstudios.kvr.config.KvrUiConfig.Prefs p = syncTheme();
        int a = p.backdrop() == com.kasperstudios.kvr.config.KvrUiConfig.Backdrop.OPAQUE ? 0xFF : backdropAlpha();
        if (a > 0 && w > 4 && h > 4) {
            if (p.backdrop() == com.kasperstudios.kvr.config.KvrUiConfig.Backdrop.GRADIENT) {
                // accent-tinted top fading into the deep slate backdrop
                int top = a << 24 | mix(BACKDROP_RGB, ACCENT & 0xFFFFFF, 0.28f);
                g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, top, a << 24 | BACKDROP_RGB);
            } else {
                g.fill(x + 2, y + 1, x + w - 2, y + h - 1, a << 24 | BACKDROP_RGB);
                g.fill(x + 1, y + 2, x + 2, y + h - 2, a << 24 | BACKDROP_RGB);
                g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, a << 24 | BACKDROP_RGB);
            }
        }
        panel(g, x, y, w, h, hovered);
    }

    /** Applies the theme accent from {@code kvr-ui.toml}; returns the prefs (cached record, cheap per frame). */
    public static com.kasperstudios.kvr.config.KvrUiConfig.Prefs syncTheme() {
        com.kasperstudios.kvr.config.KvrUiConfig.Prefs p = com.kasperstudios.kvr.config.KvrUiConfig.get();
        ACCENT = p.theme().accent;
        return p;
    }

    private static int mix(int rgbA, int rgbB, float t) {
        int r = Math.round(((rgbA >> 16) & 0xFF) * (1 - t) + ((rgbB >> 16) & 0xFF) * t);
        int gg = Math.round(((rgbA >> 8) & 0xFF) * (1 - t) + ((rgbB >> 8) & 0xFF) * t);
        int b = Math.round((rgbA & 0xFF) * (1 - t) + (rgbB & 0xFF) * t);
        return r << 16 | gg << 8 | b;
    }

    /** 0..255 from {@code window_opacity} (percent). */
    public static int backdropAlpha() {
        return Math.round(KvrConfig.client().windowOpacity() * 2.55f);
    }

    // --- text: always with a shadow -------------------------------------------------------------

    public static void text(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, FormattedCharSequence text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, true);
    }

    public static void text(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, true);
    }

    public static void centered(GuiGraphics g, Component text, int cx, int y, int color) {
        int w = Minecraft.getInstance().font.width(text);
        g.drawString(Minecraft.getInstance().font, text, cx - w / 2, y, color, true);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
