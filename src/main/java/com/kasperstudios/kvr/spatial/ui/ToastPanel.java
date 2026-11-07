package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.anchor.LazyFollowAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * KVR Toast Engine as a floating notification panel (Quest style): slightly above the line of sight,
 * lazily following the head with a narrow comfort angle so a fresh toast is always in view.
 * {@code kvr.toast({title, text, icon, duration})} from QuickJS ends up in {@link #push}.
 */
public final class ToastPanel extends SpatialWindow {

    public record Toast(Component title, Component text, ResourceLocation icon, int accent, long durationMs) {}

    private record Shown(Toast toast, long since) {}

    private static final int W = 240;
    private static final int ROW = 40;
    private static final int MAX = 3;
    private static final long SLIDE_MS = 250;

    private final Deque<Shown> shown = new ArrayDeque<>();

    /** Monotonic ms, like vanilla Util.getMillis() (that class moved package on newer nodes, 0026). */
    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    public ToastPanel() {
        super("kvr:toasts", Kind.SYSTEM, W, ROW * MAX, new LazyFollowAnchor(1.0, 0.18, 22));
    }

    public void push(Toast toast) {
        shown.addFirst(new Shown(toast, nowMs()));
        while (shown.size() > MAX) shown.removeLast();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.2f));
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.toasts");
    }

    @Override
    public boolean hasGrabBar() {
        return false;
    }

    @Override
    public boolean isVisible() {
        return !shown.isEmpty();
    }

    @Override
    public String spyText() {
        StringBuilder sb = new StringBuilder();
        for (Shown s : shown) sb.append(s.toast().title().getString()).append(": ").append(s.toast().text().getString()).append('\n');
        return sb.toString();
    }

    @Override
    public void tick() {
        long now = nowMs();
        shown.removeIf(s -> now - s.since() > s.toast().durationMs());
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        Font font = Minecraft.getInstance().font;
        long now = nowMs();
        int y = 0;
        for (Shown s : shown) {
            float t = Math.min(1f, (now - s.since()) / (float) SLIDE_MS);
            int slide = Math.round((1f - t) * (1f - t) * 40f);
            int x = slide;
            GlassStyle.surface(g, x, y + 2, W - slide, ROW - 4, false);
            g.fill(x + 6, y + 8, x + 30, y + ROW - 8, (s.toast().accent() & 0x00FFFFFF) | 0x40000000);
            GlassStyle.sprite(g, s.toast().icon(), x + 10, y + 12, 16, 16);
            int tx = x + 38;
            int maxW = W - slide - 44;
            GlassStyle.text(g, s.toast().title().copy().withStyle(ChatFormatting.BOLD), tx, y + 9, GlassStyle.TEXT);
            String body = font.plainSubstrByWidth(s.toast().text().getString(), maxW);
            GlassStyle.text(g, body, tx, y + 21, GlassStyle.MUTED);
            y += ROW;
        }
    }
}
