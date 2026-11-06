package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.desktop.DesktopProtocol;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A desktop app running inside kvr-desktopd's hidden compositor, shown only here: it never has a
 * window on the real desktop. Kind APP (takes a RAM slot), always private (pixels never leave the PC).
 *
 * <p>Layout: title bar (our own decoration — the app is asked for server-side decorations, so it draws
 * none), the app surface, a status strip at the bottom that keeps the gear clear of the app.
 * The app is asked to render at exactly the content size × {@link #PIXELS_PER_GUI_PX}, so no scaling.
 *
 * <p>Input: LMB/RMB/wheel/pointer in app pixels; in Alt mode the keyboard is captured automatically
 * while the hand hovers the window (badge in the title bar). Keys go as GLFW scancodes (Linux: evdev + 8).
 *
 * <p>D2: menus and popups arrive already composited into the frame; the app's cursor is drawn at the hand
 * pointer; the system clipboard is offered to the app on keyboard capture and on Ctrl+V / Shift+Insert,
 * text the app copies lands in the system clipboard ({@link DesktopClipboard}).
 */
public final class HostDesktopWindow extends SpatialWindow {

    private static final int TITLE_H = 18;
    private static final int BAR_H = 14;
    private static final int CONTENT_W = 480;
    private static final int CONTENT_H = 300;
    /** App pixels per GUI pixel: 480×300 GUI px → the app renders 960×600 (≈ 1.2 m wide in the world). */
    static final int PIXELS_PER_GUI_PX = 2;
    private static final int FPS_FOCUSED = 60;
    private static final int FPS_VISIBLE = 30;

    private final DesktopStream stream;
    private final RenderCompat.StreamTexture texture;
    /** The app's cursor image (D2), uploaded when {@link DesktopStream.CursorState#version()} changes. */
    private final RenderCompat.StreamTexture cursorTexture;
    private int cursorUploaded = -1;
    private int sentW = -1;
    private int sentH = -1;
    private int sentFps = -1;
    private boolean keyboard;
    private float lastX = -1;
    private float lastY = -1;

    public HostDesktopWindow(DesktopStream stream, SpatialAnchor anchor) {
        super("desktop:" + stream.id(), Kind.APP, CONTENT_W, CONTENT_H + TITLE_H + BAR_H, anchor);
        this.stream = stream;
        this.texture = new RenderCompat.StreamTexture(GlassStyle.id("desktop/stream_" + stream.id()));
        this.cursorTexture = new RenderCompat.StreamTexture(GlassStyle.id("desktop/cursor_" + stream.id()));
    }

    public DesktopStream stream() {
        return stream;
    }

    @Override
    public Component title() {
        String t = stream.title().isEmpty() ? stream.appId() : stream.title();
        return t.isEmpty() ? Component.translatable("kvr.desktop.title") : Component.literal(t);
    }

    /** Pixels of the real desktop never leave this computer: Public is disabled in the gear menu. */
    @Override
    protected boolean allowPublic() {
        return false;
    }

    @Override
    public boolean wantsKeyboard() {
        return !stream.closed();
    }

    // --- lifecycle -----------------------------------------------------------------------------

    @Override
    public void tick() {
        if (stream.closed()) {
            requestClose();
            return;
        }
        int w = contentW() * PIXELS_PER_GUI_PX;
        int h = contentH() * PIXELS_PER_GUI_PX;
        boolean shown = isVisible() && !KVRSpatial.windows().hidden();
        int fps = !shown ? 0 : keyboard ? FPS_FOCUSED : FPS_VISIBLE;
        if (w != sentW || h != sentH || fps != sentFps) {
            sentW = w;
            sentH = h;
            sentFps = fps;
            stream.bridge().send(new DesktopProtocol.Configure(stream.id(), w, h, fps, shown));
        }
    }

    @Override
    public void onClosed() {
        if (!stream.closed()) stream.bridge().send(new DesktopProtocol.Close(stream.id()));
        texture.close();
        cursorTexture.close();
        super.onClosed();
    }

    private int contentW() { return width; }
    private int contentH() { return height - TITLE_H - BAR_H; }

    // --- rendering -----------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        stream.upload((px, f, full) -> texture.upload(px, f.width(), f.height(), f.stride(),
            f.format() == DesktopProtocol.FORMAT_BGRA, rects(f), full));

        GlassStyle.surface(g, 0, 0, width, height, false);
        Component name = title().copy().withStyle(ChatFormatting.BOLD);
        GlassStyle.text(g, name, 8, 5, GlassStyle.TEXT);
        if (keyboard) GlassStyle.text(g, Component.translatable("kvr.desktop.keyboard_on"), width - 92, 5, GlassStyle.ACCENT);

        int top = TITLE_H;
        int ch = contentH();
        if (texture.ready()) {
            g.blit(texture.texture(), 0, top, width, ch, 0f, 0f, texture.width(), texture.height(), texture.width(), texture.height());
            if (mx >= 0 && my >= top && my < top + ch) drawCursor(g, mx, my);
        } else {
            g.fill(0, top, width, top + ch, 0x60000000);
            GlassStyle.text(g, Component.translatable("kvr.desktop.waiting"), 8, top + 8, GlassStyle.MUTED);
        }

        int by = height - BAR_H;
        g.fill(0, by, width - GEAR - 6, height, 0x30000000);
        Component hint = keyboard ? Component.translatable("kvr.desktop.keyboard_hint") : Component.translatable("kvr.desktop.hover_hint");
        GlassStyle.text(g, hint, 8, by + 3, GlassStyle.MUTED);
    }

    private static List<int[]> rects(DesktopProtocol.Frame f) {
        List<int[]> out = new ArrayList<>(f.damage().size());
        for (DesktopProtocol.Rect r : f.damage()) out.add(new int[] {r.x(), r.y(), r.w(), r.h()});
        return out;
    }

    /**
     * The app's own cursor at the hand pointer (D2): the shape the app asked for (text beam over a text
     * field, resize arrows on a split view…). Nothing until the app set one; hidden when the app hides it.
     */
    private void drawCursor(GuiGraphics g, int mx, int my) {
        DesktopStream.CursorState c = stream.cursor();
        if (c == null || c.shape() == DesktopProtocol.CURSOR_HIDDEN) return;
        if (c.shape() == DesktopProtocol.CURSOR_NAMED) {
            DesktopCursors.draw(g, c.name(), mx, my);
            return;
        }
        if (c.width() <= 0 || c.height() <= 0 || c.pixels() == null || c.pixels().length < c.width() * c.height() * 4) return;
        if (c.version() != cursorUploaded) {
            java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(c.width() * c.height() * 4);
            px.put(c.pixels(), 0, c.width() * c.height() * 4).flip();
            cursorTexture.upload(px, c.width(), c.height(), c.width() * 4, true, List.of(), true);
            cursorUploaded = c.version();
        }
        // app pixels → GUI pixels, same scale as the app surface itself
        float sx = (float) width / Math.max(1, texture.width());
        float sy = (float) contentH() / Math.max(1, texture.height());
        int x = Math.round(mx - c.hotX() * sx);
        int y = Math.round(my - c.hotY() * sy);
        int w = Math.max(1, Math.round(c.width() * sx));
        int h = Math.max(1, Math.round(c.height() * sy));
        RenderCompat.blitPremultiplied(g, cursorTexture.texture(), x, y, w, h, c.width(), c.height());
    }

    // --- input ---------------------------------------------------------------------------------

    /** Window GUI pixels → app surface pixels; null outside the app area. */
    private float[] appPoint(double x, double y) {
        int ch = contentH();
        if (y < TITLE_H || y >= TITLE_H + ch || x < 0 || x >= width) return null;
        int aw = texture.ready() ? texture.width() : Math.max(1, sentW);
        int ah = texture.ready() ? texture.height() : Math.max(1, sentH);
        return new float[] {(float) (x / width * aw), (float) ((y - TITLE_H) / ch * ah)};
    }

    @Override
    public void mouseMoved(double x, double y) {
        float[] p = appPoint(x, y);
        if (p == null || (Math.abs(p[0] - lastX) < 0.5f && Math.abs(p[1] - lastY) < 0.5f)) return;
        lastX = p[0];
        lastY = p[1];
        stream.bridge().send(new DesktopProtocol.Pointer(stream.id(), p[0], p[1]));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        float[] p = appPoint(x, y);
        if (p == null) return false;
        stream.bridge().send(new DesktopProtocol.Pointer(stream.id(), p[0], p[1]));
        stream.bridge().send(new DesktopProtocol.Button(stream.id(), evdevButton(button), true));
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        float[] p = appPoint(x, y);
        if (p != null) stream.bridge().send(new DesktopProtocol.Pointer(stream.id(), p[0], p[1]));
        stream.bridge().send(new DesktopProtocol.Button(stream.id(), evdevButton(button), false));
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        float[] p = appPoint(x, y);
        if (p == null) return false;
        stream.bridge().send(new DesktopProtocol.Axis(stream.id(), 0f, (float) amount));
        return true;
    }

    private static int evdevButton(int glfw) {
        return switch (glfw) {
            case 1 -> DesktopProtocol.BTN_RIGHT;
            case 2 -> DesktopProtocol.BTN_MIDDLE;
            default -> DesktopProtocol.BTN_LEFT;
        };
    }

    @Override
    public void keyboardFocus(boolean focused) {
        keyboard = focused;
        // what the player copied outside is ready before the first Ctrl+V
        if (focused) DesktopClipboard.offer(stream.bridge());
        stream.bridge().send(new DesktopProtocol.Focus(stream.id(), focused));
    }

    @Override
    public void keyEvent(int key, int scancode, int action, int mods) {
        // the app repeats keys itself (wl_keyboard repeat_info), so GLFW repeats are dropped
        if (action == GLFW.GLFW_REPEAT) return;
        boolean paste = (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0)
            || (key == GLFW.GLFW_KEY_INSERT && (mods & GLFW.GLFW_MOD_SHIFT) != 0);
        // the socket keeps order: the daemon owns the fresh text before the app sees the key
        if (paste && action == GLFW.GLFW_PRESS) DesktopClipboard.offer(stream.bridge());
        stream.bridge().send(new DesktopProtocol.Key(stream.id(), scancode, key, action == GLFW.GLFW_PRESS));
    }
}
