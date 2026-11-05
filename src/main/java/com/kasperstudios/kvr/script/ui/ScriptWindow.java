package com.kasperstudios.kvr.script.ui;

import com.kasperstudios.kvr.script.ScriptHost;
import com.kasperstudios.kvr.script.VrApp;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

/**
 * A running .vrapp as a spatial window (Kind APP — takes a RAM slot). Paints the latest widget tree
 * committed by the script: column layout, rows via {@code Stack({dir:'row'})}, glass or gradient
 * background, shake. Clicks become UI events in the script's inbox. Closing the window stops the app.
 */
public final class ScriptWindow extends SpatialWindow implements ScriptHost.Listener {

    private static final int PAD = 10;
    private static final int TITLE_H = 24;
    private static final int BTN_H = 20;
    private static final int SLIDER_H = 8;

    private record Hit(int id, String type, int x, int y, int w, int h, double min, double max, boolean readOnly) {}

    private final ScriptHost host;
    private final List<Hit> hits = new ArrayList<>();
    private boolean started;
    private long shakeUntil;
    private float shakeIntensity;

    public ScriptWindow(VrApp app, String prelude, int cpuTier, SpatialAnchor anchor) {
        super("app:" + app.id(), Kind.APP, app.width(), app.height(), anchor);
        this.host = new ScriptHost(app, prelude, cpuTier, this);
    }

    /** First tick happens only after WindowManager accepted the window (RAM limit), so no orphan threads. */
    @Override
    public void tick() {
        if (!started) {
            started = true;
            host.start();
        }
    }

    public ScriptHost host() {
        return host;
    }

    @Override
    public Component title() {
        UiNode ui = host.ui();
        return Component.literal(ui != null ? ui.str("title", host.app().name()) : host.app().name());
    }

    @Override
    public void onClosed() {
        host.stop();
        super.onClosed();
    }

    public String spyText() {
        UiNode ui = host.ui();
        if (ui == null) return "";
        StringBuilder sb = new StringBuilder();
        collectText(ui, sb);
        return sb.toString();
    }

    private static void collectText(UiNode n, StringBuilder sb) {
        if (sb.length() > 240) return;
        if ("text".equals(n.type())) sb.append(n.str("text", "")).append('\n');
        if ("button".equals(n.type())) sb.append('[').append(n.str("label", "")).append("] ");
        for (UiNode c : n.children()) collectText(c, sb);
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        int ox = 0;
        int oy = 0;
        if (System.currentTimeMillis() < shakeUntil) {
            int a = Math.max(1, Math.round(shakeIntensity));
            ox = ThreadLocalRandom.current().nextInt(-a, a + 1);
            oy = ThreadLocalRandom.current().nextInt(-a, a + 1);
        }
        RenderCompat.pushTranslate(g, ox, oy);
        try {
            UiNode ui = host.ui();
            background(g, ui);
            GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), PAD, 9, GlassStyle.TEXT);
            g.fill(PAD, TITLE_H - 2, width - PAD, TITLE_H - 1, 0x30FFFFFF);
            switch (host.state()) {
                case NO_ENGINE -> message(g, Component.translatable("kvr.app.no_engine"), GlassStyle.MUTED);
                case CRASHED -> message(g, Component.translatable("kvr.app.crashed").append(": " + host.error()), GlassStyle.DANGER);
                case STARTING -> message(g, Component.translatable("kvr.app.starting"), GlassStyle.MUTED);
                default -> {
                    if (ui != null) column(g, ui.children(), PAD, TITLE_H + 6, width - 2 * PAD, mx - ox, my - oy);
                    if (host.state() == ScriptHost.State.NOT_RESPONDING) notResponding(g, mx - ox, my - oy);
                }
            }
        } finally {
            RenderCompat.pop(g);
        }
    }

    private void background(GuiGraphics g, UiNode ui) {
        String bg = ui == null ? "glass" : ui.obj("style").has("background") ? ui.obj("style").get("background").getAsString() : "glass";
        if (bg.startsWith("linear(") && bg.endsWith(")")) {
            String[] c = bg.substring(7, bg.length() - 1).split(",");
            if (c.length == 2) {
                g.fillGradient(0, 0, width, height, argb(c[0].trim(), 0xE0), argb(c[1].trim(), 0xE0));
                return;
            }
        }
        GlassStyle.panel(g, 0, 0, width, height, false);
    }

    private int column(GuiGraphics g, List<UiNode> nodes, int x, int y, int w, int mx, int my) {
        for (UiNode n : nodes) y = node(g, n, x, y, w, mx, my) + 4;
        return y;
    }

    /** @return bottom y of the node */
    private int node(GuiGraphics g, UiNode n, int x, int y, int w, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        switch (n.type()) {
            case "text" -> {
                int color = n.props().has("color") ? argb(n.str("color", "#f1f5f9"), 0xFF) : GlassStyle.TEXT;
                Component text = Component.literal(n.str("text", ""));
                if (n.num("weight", 400) >= 700) text = text.copy().withStyle(ChatFormatting.BOLD);
                int jx = n.bool("glitch") && ThreadLocalRandom.current().nextInt(12) == 0 ? ThreadLocalRandom.current().nextInt(-2, 3) : 0;
                List<FormattedCharSequence> lines = font.split(text, Math.max(10, w));
                for (FormattedCharSequence line : lines) {
                    g.drawString(font, line, x + jx, y, color, false);
                    y += 10;
                }
                return y;
            }
            case "button" -> {
                boolean hot = GlassStyle.inside(mx, my, x, y, w, BTN_H);
                GlassStyle.panel(g, x, y, w, BTN_H, hot);
                String variant = n.str("variant", "");
                if ("success".equals(variant)) g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, 0x5522C55E);
                if ("danger".equals(variant)) g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, 0x55F43F5E);
                GlassStyle.centered(g, Component.literal(n.str("label", "")), x + w / 2, y + 6, GlassStyle.TEXT);
                hits.add(new Hit(n.id(), "button", x, y, w, BTN_H, 0, 0, true));
                return y + BTN_H;
            }
            case "slider" -> {
                double min = n.num("min", 0);
                double max = n.num("max", 100);
                double v = n.num("value", 0);
                double k = max > min ? Math.max(0, Math.min(1, (v - min) / (max - min))) : 0;
                g.fill(x, y + 2, x + w, y + SLIDER_H, 0x30FFFFFF);
                g.fill(x, y + 2, x + (int) Math.round(w * k), y + SLIDER_H, n.bool("glow") ? GlassStyle.OK : GlassStyle.ACCENT);
                hits.add(new Hit(n.id(), "slider", x, y, w, SLIDER_H + 2, min, max, n.bool("readOnly")));
                return y + SLIDER_H + 2;
            }
            case "stack" -> {
                if (!"row".equals(n.str("dir", "column"))) return column(g, n.children(), x, y, w, mx, my) - 4;
                int count = Math.max(1, n.children().size());
                int cw = (w - (count - 1) * 4) / count;
                int bottom = y;
                for (int i = 0; i < n.children().size(); i++) bottom = Math.max(bottom, node(g, n.children().get(i), x + i * (cw + 4), y, cw, mx, my));
                return bottom;
            }
            default -> {
                return y;
            }
        }
    }

    private void message(GuiGraphics g, Component text, int color) {
        Font font = Minecraft.getInstance().font;
        int y = TITLE_H + 8;
        for (FormattedCharSequence line : font.split(text, width - 2 * PAD)) {
            g.drawString(font, line, PAD, y, color, false);
            y += 10;
        }
    }

    private void notResponding(GuiGraphics g, int mx, int my) {
        g.fill(0, TITLE_H, width, height, 0xA0000000);
        GlassStyle.centered(g, Component.translatable("kvr.app.not_responding", host.app().name()), width / 2, height / 2 - 16, GlassStyle.DANGER);
        int bw = 80;
        int bx = (width - bw) / 2;
        int by = height / 2;
        GlassStyle.panel(g, bx, by, bw, BTN_H, GlassStyle.inside(mx, my, bx, by, bw, BTN_H));
        GlassStyle.centered(g, Component.translatable("kvr.app.close"), width / 2, by + 6, GlassStyle.TEXT);
        hits.add(0, new Hit(-1, "close", bx, by, bw, BTN_H, 0, 0, true));
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit h : hits) {
            if (!GlassStyle.inside(mx, my, h.x(), h.y(), h.w(), h.h())) continue;
            switch (h.type()) {
                case "close" -> KVRSpatial.windows().close(this);
                case "button" -> host.post("{\"type\":\"click\",\"id\":" + h.id() + "}");
                case "slider" -> {
                    if (h.readOnly()) return false;
                    double v = h.min() + (h.max() - h.min()) * Math.max(0, Math.min(1, (mx - h.x()) / h.w()));
                    host.post("{\"type\":\"change\",\"id\":" + h.id() + ",\"value\":" + v + "}");
                }
                default -> {}
            }
            return true;
        }
        return false;
    }

    // --- ScriptHost.Listener (called on the script thread -> hop to the client thread) ----------

    @Override
    public void toast(String title, String text) {
        Minecraft.getInstance().execute(() -> KVRSpatial.toasts().push(new ToastPanel.Toast(
            Component.literal(title), Component.literal(text), GlassStyle.id("gesture/launcher"), GlassStyle.ACCENT, 4000)));
    }

    @Override
    public void notResponding() {
        Minecraft.getInstance().execute(() -> KVRSpatial.toasts().push(new ToastPanel.Toast(
            Component.translatable("kvr.app.not_responding", host.app().name()), Component.translatable("kvr.app.close_hint"),
            GlassStyle.id("power/warning"), GlassStyle.DANGER, 5000)));
    }

    @Override
    public void shake(float intensity, int durationMs) {
        Minecraft.getInstance().execute(() -> {
            shakeIntensity = Math.max(1f, Math.min(12f, intensity));
            shakeUntil = System.currentTimeMillis() + Math.max(0, Math.min(5000, durationMs));
        });
    }

    @Override
    public void resize(int w, int h) {
        Minecraft.getInstance().execute(() -> {
            if (w > 0) width = Math.max(120, Math.min(1024, w));
            if (h > 0) height = Math.max(80, Math.min(768, h));
        });
    }

    @Override
    public void sound(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return;
        Minecraft.getInstance().execute(() ->
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(loc), 1f)));
    }

    private static int argb(String hex, int alpha) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return (alpha << 24) | (Integer.parseInt(h.length() > 6 ? h.substring(0, 6) : h, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return (alpha << 24) | 0xF1F5F9;
        }
    }
}
