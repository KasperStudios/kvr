package com.kasperstudios.kvr.spatial.space;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 0033 — slot picker for gesture 4: one card per slot (Real world, Space 1..N) with the window count; click a card
 * to switch, the picker closes. Gesture 4 again while it is open closes it. A SYSTEM window, so it is global and
 * takes no RAM slot.
 */
public final class SpacePickerWindow extends SpatialWindow {

    private static final int CARD_W = 74;
    private static final int CARD_H = 78;
    private static final int GAP = 6;
    private static final int PAD = 8;
    private static final int TOP = 24;
    private static final int H = TOP + CARD_H + 22;

    private SpacePickerWindow(int cards, WorldAnchor anchor) {
        super("kvr:space_picker", Kind.SYSTEM, PAD * 2 + cards * CARD_W + (cards - 1) * GAP, H, anchor);
    }

    public static void toggle() {
        if (!KVRSpatial.ready()) return;
        var wm = KVRSpatial.windows();
        Optional<SpacePickerWindow> open = wm.find(SpacePickerWindow.class);
        if (open.isPresent()) {
            wm.close(open.get());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(0.85));
        wm.open(new SpacePickerWindow(VirtualSpaces.slots() + 1, new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.space.picker");
    }

    @Override
    public boolean hasGrabBar() {
        return false;
    }

    @Override
    public boolean hasCloseButton() {
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), PAD, 8, GlassStyle.TEXT);
        int cocoon = VirtualSpaces.cocoonArgb();
        int slots = VirtualSpaces.slots();
        for (int s = 0; s <= slots; s++) {
            int x = cardX(s);
            boolean on = s == VirtualSpaces.active();
            boolean hot = GlassStyle.inside(mx, my, x, TOP, CARD_W, CARD_H);
            GlassStyle.panel(g, x, TOP, CARD_W, CARD_H, hot || on);
            int stripe = s == VirtualSpaces.REAL ? GlassStyle.MUTED : cocoon;
            g.fill(x + 3, TOP + 3, x + CARD_W - 3, TOP + 5, stripe);
            if (on) {
                g.fill(x, TOP, x + CARD_W, TOP + 1, GlassStyle.ACCENT);
                g.fill(x, TOP + CARD_H - 1, x + CARD_W, TOP + CARD_H, GlassStyle.ACCENT);
                g.fill(x, TOP, x + 1, TOP + CARD_H, GlassStyle.ACCENT);
                g.fill(x + CARD_W - 1, TOP, x + CARD_W, TOP + CARD_H, GlassStyle.ACCENT);
            }
            String big = s == VirtualSpaces.REAL ? "\u25ce" : String.valueOf(s);
            GlassStyle.centered(g, Component.literal(big).withStyle(ChatFormatting.BOLD), x + CARD_W / 2, TOP + 16, on ? GlassStyle.TEXT : stripe);
            Component label = s == VirtualSpaces.REAL ? Component.translatable("kvr.space.real")
                : s <= 3 ? Component.translatable("kvr.space.label." + s) : Component.translatable("kvr.space.label.n");
            GlassStyle.centered(g, fit(label.getString(), CARD_W - 8), x + CARD_W / 2, TOP + 36, on ? GlassStyle.TEXT : GlassStyle.MUTED);
            GlassStyle.centered(g, Component.translatable("kvr.space.windows", VirtualSpaces.count(s)), x + CARD_W / 2, TOP + 52, GlassStyle.MUTED);
            if (on) GlassStyle.centered(g, Component.translatable("kvr.space.current"), x + CARD_W / 2, TOP + 64, GlassStyle.ACCENT);
        }
        Component hint = Component.translatable(KvrSpaceConfig.get().gesture() == KvrSpaceConfig.GestureMode.PICKER ? "kvr.space.hint" : "kvr.space.hint_cycle");
        GlassStyle.text(g, fit(hint.getString(), width - PAD * 2), PAD, TOP + CARD_H + 7, GlassStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int s = 0; s <= VirtualSpaces.slots(); s++) {
            if (GlassStyle.inside(mx, my, cardX(s), TOP, CARD_W, CARD_H)) {
                VirtualSpaces.switchTo(s);
                KVRSpatial.windows().close(this);
                return true;
            }
        }
        return true; // a click on the picker never leaks into the world
    }

    private static int cardX(int slot) {
        return PAD + slot * (CARD_W + GAP);
    }

    private static Component fit(String s, int maxW) {
        var font = Minecraft.getInstance().font;
        if (font.width(s) <= maxW) return Component.literal(s);
        return Component.literal(font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026");
    }
}
