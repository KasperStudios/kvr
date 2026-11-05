package com.kasperstudios.kvr.spatial.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Visual language of KVR OS: glassmorphism panels from nine-slice GUI sprites
 * ({@code assets/kvr/textures/gui/sprites/*.png} + {@code .mcmeta} with {@code nine_slice} scaling),
 * so windows stay crisp at any size without custom shaders. The sprites are generated on the
 * KVR textures page (category "GUI-спрайты"). All drawing goes through {@link RenderCompat#sprite}.
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
    public static final int MUTED = 0xB0E2E8F0;
    public static final int ACCENT = 0xFF67E8F9;
    public static final int OK = 0xFF4ADE80;
    public static final int DANGER = 0xFFF43F5E;
    public static final int PADDING = 8;

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

    public static void panel(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        RenderCompat.sprite(g, hovered ? PANEL_HOVER : PANEL, x, y, w, h);
    }

    public static void text(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(Minecraft.getInstance().font, text, x, y, color, false);
    }

    public static void centered(GuiGraphics g, Component text, int cx, int y, int color) {
        int w = Minecraft.getInstance().font.width(text);
        g.drawString(Minecraft.getInstance().font, text, cx - w / 2, y, color, false);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
