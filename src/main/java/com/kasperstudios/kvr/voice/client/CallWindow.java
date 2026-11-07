package com.kasperstudios.kvr.voice.client;

import com.kasperstudios.kvr.compat.client.ClientCompat;
import com.kasperstudios.kvr.script.ClientInfo;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.LazyFollowAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.voice.CallProtocol;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * "Звонки" — native system window of Calls (SPEC 6.3). Follows the head lazily like the power window.
 * <ul>
 *   <li>incoming: caller's face, bell every 2 s, Accept / Decline (× = Decline);</li>
 *   <li>calling / in a call: participants with faces, a green frame + "speaking" while someone talks, timer,
 *       Add (picker of online players) / Hang up;</li>
 *   <li>no call: the same picker works as a dialer; after a call ends the window closes by itself in 3 s.</li>
 * </ul>
 */
public final class CallWindow extends SpatialWindow {

    private static final int W = 240;
    private static final int H = 196;
    private static final int TOP = 36;
    private static final int ROW = 16;
    private static final int BTN_H = 18;
    private static final int BTN_Y = H - BTN_H - 16; // the gear sits in the bottom-right corner below
    private static final int PICKER_ROWS = 7;
    private static final long AUTO_CLOSE_MS = 3000;
    private static final int TINT_OK = 0x664ADE80;
    private static final int TINT_DANGER = 0x66F43F5E;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private boolean picking;
    private int ringTicks;

    public CallWindow() {
        super("kvr:calls", Kind.SYSTEM, W, H, new LazyFollowAnchor(0.8, -0.02, 25));
    }

    /** Restore the open window or spawn one (incoming call, Messenger, kvr.calls). */
    public static void open() {
        if (!KVRSpatial.ready()) return;
        var wm = KVRSpatial.windows();
        Optional<CallWindow> w = wm.find(CallWindow.class);
        if (w.isPresent()) wm.restore(w.get());
        else wm.open(new CallWindow());
    }

    /** Dock / launcher: open as a dialer. */
    public static void openDialer() {
        open();
        if (!CallClient.state().inCall()) KVRSpatial.windows().find(CallWindow.class).ifPresent(w -> w.picking = true);
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.calls.app");
    }

    @Override
    public String spyText() {
        CallProtocol.State s = CallClient.state();
        if (!s.inCall()) return "";
        StringBuilder sb = new StringBuilder(s.status().name()).append(':');
        for (CallProtocol.Member m : s.members()) sb.append(' ').append(m.name());
        return sb.toString();
    }

    @Override
    public void tick() {
        CallProtocol.State s = CallClient.state();
        if (s.status() == CallProtocol.Status.RINGING_IN) {
            if (ringTicks++ % 40 == 0 && com.kasperstudios.kvr.config.KvrUiConfig.get().soundToasts()) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1f));
            }
        } else {
            ringTicks = 0;
        }
        if (s.inCall()) return;
        if (!picking && System.currentTimeMillis() - CallClient.endedAt() > AUTO_CLOSE_MS) requestClose();
    }

    @Override
    public void onClosed() {
        if (CallClient.state().status() == CallProtocol.Status.RINGING_IN) CallClient.decline(); // × = Decline
        super.onClosed();
    }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        hits.clear();
        CallProtocol.State s = CallClient.state();
        GlassStyle.surface(g, 0, 0, width, height, false);
        GlassStyle.text(g, title().copy().withStyle(ChatFormatting.BOLD), 8, 8, GlassStyle.TEXT);
        status(g, s);
        g.fill(8, TOP - 6, width - 8, TOP - 5, 0x30FFFFFF);

        if (picking) picker(g, mx, my, s);
        else members(g, s);

        int half = (width - 16 - 6) / 2;
        int right = 8 + half + 6;
        switch (s.status()) {
            case RINGING_IN -> {
                button(g, mx, my, 8, half, Component.translatable("kvr.calls.accept"), TINT_OK, CallClient::accept);
                button(g, mx, my, right, half, Component.translatable("kvr.calls.decline"), TINT_DANGER, CallClient::decline);
            }
            case RINGING_OUT, ACTIVE -> {
                button(g, mx, my, 8, half, Component.translatable(picking ? "kvr.calls.back" : "kvr.calls.add"), 0, () -> picking = !picking);
                button(g, mx, my, right, half, Component.translatable("kvr.calls.hangup"), TINT_DANGER, CallClient::hangup);
            }
            case ENDED, IDLE -> {
                button(g, mx, my, 8, half, Component.translatable(picking ? "kvr.calls.back" : "kvr.calls.dial"), 0, () -> picking = !picking);
                button(g, mx, my, right, half, Component.translatable("kvr.calls.close"), 0, this::requestClose);
            }
        }
    }

    private void status(GuiGraphics g, CallProtocol.State s) {
        Component line;
        int color;
        switch (s.status()) {
            case RINGING_IN -> {
                line = Component.translatable("kvr.calls.status.incoming", s.caller());
                color = GlassStyle.ACCENT;
            }
            case RINGING_OUT -> {
                line = Component.translatable("kvr.calls.status.calling");
                color = GlassStyle.MUTED;
            }
            case ACTIVE -> {
                long sec = Math.max(0, (System.currentTimeMillis() - CallClient.activeSince()) / 1000);
                line = Component.translatable("kvr.calls.status.active", String.format("%02d:%02d", sec / 60, sec % 60));
                color = GlassStyle.OK;
            }
            case ENDED -> {
                line = s.reason().isEmpty() ? Component.translatable("kvr.calls.ended")
                    : Component.translatable("kvr.calls.ended").append(": ").append(CallClient.reasonText(s.reason()));
                color = GlassStyle.DANGER;
            }
            default -> {
                line = Component.translatable("kvr.calls.status.idle");
                color = GlassStyle.MUTED;
            }
        }
        fitted(g, line.getString(), 8, 19, width - 30, color);
    }

    private void members(GuiGraphics g, CallProtocol.State s) {
        if (s.members().isEmpty()) {
            fitted(g, Component.translatable("kvr.calls.hint").getString(), 10, TOP + 4, width - 20, GlassStyle.MUTED);
            return;
        }
        int y = TOP;
        for (CallProtocol.Member m : s.members()) {
            if (y + ROW > BTN_Y - 4) break;
            if (m.speaking()) g.fill(9, y - 1, 23, y + 13, GlassStyle.OK);
            ClientCompat.drawFace(g, m.uuid(), 10, y, 12);
            fitted(g, m.name(), 28, y + 2, 120, m.joined() ? GlassStyle.TEXT : GlassStyle.MUTED);
            Component tag = !m.joined() ? Component.translatable("kvr.calls.ringing")
                : m.speaking() ? Component.translatable("kvr.calls.speaking") : Component.empty();
            int tw = Minecraft.getInstance().font.width(tag);
            GlassStyle.text(g, tag, width - 10 - tw, y + 2, m.speaking() ? GlassStyle.OK : GlassStyle.MUTED);
            y += ROW;
        }
    }

    private void picker(GuiGraphics g, int mx, int my, CallProtocol.State s) {
        List<String> candidates = new ArrayList<>(ClientInfo.get().online());
        for (CallProtocol.Member m : s.members()) candidates.remove(m.name());
        if (candidates.isEmpty()) {
            fitted(g, Component.translatable("kvr.calls.nobody").getString(), 10, TOP + 4, width - 20, GlassStyle.MUTED);
            return;
        }
        int y = TOP - 2;
        for (int i = 0; i < Math.min(PICKER_ROWS, candidates.size()); i++) {
            String name = candidates.get(i);
            boolean hot = GlassStyle.inside(mx, my, 8, y, width - 16, ROW);
            GlassStyle.panel(g, 8, y, width - 16, ROW, hot);
            fitted(g, name, 14, y + 4, width - 60, hot ? GlassStyle.TEXT : GlassStyle.MUTED);
            GlassStyle.text(g, "\u260e", width - 22, y + 4, GlassStyle.ACCENT);
            hits.add(new Hit(8, y, width - 16, ROW, () -> {
                CallClient.call(List.of(name));
                picking = false;
            }));
            y += ROW + 2;
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int w, Component label, int tint, Runnable action) {
        boolean hot = GlassStyle.inside(mx, my, x, BTN_Y, w, BTN_H);
        GlassStyle.panel(g, x, BTN_Y, w, BTN_H, hot);
        if (tint != 0) g.fill(x + 2, BTN_Y + 2, x + w - 2, BTN_Y + BTN_H - 2, tint);
        GlassStyle.centered(g, label, x + w / 2, BTN_Y + 5, hot ? GlassStyle.TEXT : GlassStyle.MUTED);
        hits.add(new Hit(x, BTN_Y, w, BTN_H, action));
    }

    private static void fitted(GuiGraphics g, String text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        String s = text;
        if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("\u2026"))) + "\u2026";
        GlassStyle.text(g, s, x, y, color);
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (GlassStyle.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }
        return false;
    }
}
