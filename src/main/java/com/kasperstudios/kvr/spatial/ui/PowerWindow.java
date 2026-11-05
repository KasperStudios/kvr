package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.power.PowerState;
import com.kasperstudios.kvr.power.client.PowerClient;
import com.kasperstudios.kvr.spatial.anchor.LazyFollowAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * "Питание" — a floating system window (not a HUD): battery, draw vs. safe discharge, temperature
 * and the three power buttons. Opens from the dock status block, and by itself in front of the
 * player when the worn glasses start overloading — the big red Power Off is the skill save.
 * The × and the gear come from the window chrome; Lock is dropped by itself during an overload
 * (a locked window must never block the save button).
 */
public final class PowerWindow extends SpatialWindow {

    private static final int W = 220;
    private static final int H = 146;
    private static final int BTN_Y = 102;
    private static final int BTN_H = 26;

    public PowerWindow() {
        super("kvr:power", Kind.SYSTEM, W, H, new LazyFollowAnchor(0.75, -0.08, 25));
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.power.window");
    }

    @Override
    public void tick() {
        if (isLocked() && PowerClient.overloading()) setLocked(false);
    }

    @Override
    public String spyText() {
        return PowerClient.snapshot()
            .map(s -> s.data().state().name() + " " + s.data().percent(s.build()) + "% "
                + String.format("%.0f/%d FE/t ", s.build().draw(s.data().state()), s.build().maxDischarge())
                + (s.data().overloading() ? "OVERLOAD " + s.data().secondsLeft(s.gameTime()) + "s" : ""))
            .orElse("");
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        Optional<PowerClient.Snapshot> snap = PowerClient.snapshot();
        boolean alarm = snap.map(s -> s.data().overloading()).orElse(false);
        GlassStyle.panel(g, 0, 0, width, height, alarm && PowerClient.strobeOn());

        GlassStyle.sprite(g, alarm ? GlassStyle.id("power/warning") : GlassStyle.id("power/power"), 10, 9, 16, 16);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 32, 13, GlassStyle.TEXT);

        if (snap.isEmpty()) {
            GlassStyle.text(g, Component.translatable("kvr.power.no_glasses"), 10, 40, GlassStyle.MUTED);
            return;
        }
        var s = snap.get();
        int pct = s.data().percent(s.build());

        // battery bar
        GlassStyle.sprite(g, GlassStyle.BATTERY, 10, 32, 16, 16);
        g.fill(32, 36, 32 + 120, 44, 0x30FFFFFF);
        g.fill(32, 36, 32 + Math.round(1.2f * pct), 44, pct <= 20 ? GlassStyle.DANGER : GlassStyle.OK);
        GlassStyle.text(g, Component.literal(pct + "%"), 158, 36, GlassStyle.TEXT);

        // state + draw
        GlassStyle.text(g, Component.translatable("kvr.power.state." + s.data().state().name().toLowerCase()), 10, 54, GlassStyle.TEXT);
        Component draw = Component.literal(String.format("%.1f / %d FE/t", s.build().draw(s.data().state()), s.build().maxDischarge()));
        GlassStyle.text(g, draw, 110, 54, s.build().overloads(s.data().state()) ? GlassStyle.DANGER : GlassStyle.MUTED);

        // temperature / countdown
        GlassStyle.sprite(g, GlassStyle.id("power/thermometer"), 10, 68, 16, 16);
        int temp = (int) Math.round(s.data().temperature(s.gameTime()));
        GlassStyle.text(g, Component.literal(temp + "\u00b0C"), 32, 72, alarm ? GlassStyle.DANGER : GlassStyle.MUTED);
        if (alarm) {
            Component left = Component.translatable("kvr.power.overload_left", s.data().secondsLeft(s.gameTime())).withStyle(ChatFormatting.BOLD);
            GlassStyle.text(g, left, 80, 72, GlassStyle.DANGER);
        } else if (s.data().booting(s.gameTime())) {
            GlassStyle.text(g, Component.translatable("kvr.power.booting"), 80, 72, GlassStyle.ACCENT);
        }

        // buttons
        if (alarm) {
            button(g, mx, my, 10, width - 20, Component.translatable("kvr.power.btn.off_now"), true);
        } else {
            int bw = (width - 20 - 8) / 3;
            button(g, mx, my, 10, bw, Component.translatable("kvr.power.btn.on"), false);
            button(g, mx, my, 10 + bw + 4, bw, Component.translatable("kvr.power.btn.hud_off"), false);
            button(g, mx, my, 10 + 2 * (bw + 4), bw, Component.translatable("kvr.power.btn.off"), false);
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int w, Component label, boolean danger) {
        boolean hot = GlassStyle.inside(mx, my, x, BTN_Y, w, BTN_H);
        GlassStyle.panel(g, x, BTN_Y, w, BTN_H, hot || danger);
        if (danger) g.fill(x + 2, BTN_Y + 2, x + w - 2, BTN_Y + BTN_H - 2, 0x66F43F5E);
        GlassStyle.centered(g, label, x + w / 2, BTN_Y + 9, danger ? GlassStyle.TEXT : GlassStyle.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my < BTN_Y || my > BTN_Y + BTN_H) return false;
        if (PowerClient.overloading()) {
            PowerClient.request(PowerState.OFF);
            return true;
        }
        int bw = (width - 20 - 8) / 3;
        int i = (int) ((mx - 10) / (bw + 4));
        if (mx < 10 || i > 2) return false;
        PowerClient.request(i == 0 ? PowerState.ON : i == 1 ? PowerState.HUD_OFF : PowerState.OFF);
        return true;
    }
}
