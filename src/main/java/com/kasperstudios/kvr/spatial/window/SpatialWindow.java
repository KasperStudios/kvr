package com.kasperstudios.kvr.spatial.window;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Base of every surface in KVR OS. A window draws itself in GUI pixels with a normal
 * {@link GuiGraphics} into its own offscreen {@link WindowSurface}; {@code SpatialRenderer} then puts
 * that texture on a quad in the world. Input arrives already converted to window pixels.
 *
 * <p>Under the window floats the grab bar (ADR-002): the handle moves the window. Its colour shows
 * the anchor (cyan = Body, white = World, red = locked). System panels can hide it.
 *
 * <p>Chrome ({@link WindowChrome}, drawn over the content by {@link WindowSurface}, so every window
 * gets it without code in subclasses): × top-right closes the window, the gear bottom-right opens
 * the window menu — Body/World anchoring, Public/Private, Lock. Anchoring is changed only there (or
 * by the app through {@code kvr.window.anchor}). Input must go through
 * {@link #click}/{@link #release(double, double, int)}/{@link #scroll}.
 */
public abstract class SpatialWindow {

    /** 400 GUI px = 1 block, so a 320×200 window is 0.8×0.5 m. */
    public static final float PX_PER_METER = 400f;
    public static final int GRAB_GAP = 6;
    public static final int GRAB_W = 56;
    public static final int GRAB_H = 4;
    public static final int GRAB_HIT_PAD = 4;
    /** Gear size; content should keep its bottom-right corner free (see HostDesktopWindow). */
    public static final int GEAR = WindowChrome.GEAR;

    /** What the window costs. Only APP windows take a RAM slot (SPEC 2.5: vanilla screens are free). */
    public enum Kind { APP, VANILLA, SYSTEM }

    private static final int LOCK_RED = 0xC0F43F5E;
    private static final int SEG_ON = 0x7067E8F9;
    private static final int SEG_OFF = 0x26FFFFFF;
    private static final int HOT = 0x30FFFFFF;

    private final String id;
    private final Kind kind;
    protected int width;
    protected int height;
    private SpatialAnchor anchor;
    private SpatialPose pose;
    @Nullable private SpatialPose held;
    private boolean minimized;
    private float open;
    @Nullable private WindowSurface surface;
    private final WindowChrome chrome = new WindowChrome();
    private boolean closeRequested;
    /** The last press went to the content, so its release belongs there too. */
    private boolean pressToContent;

    protected SpatialWindow(String id, Kind kind, int width, int height, SpatialAnchor anchor) {
        this.id = id;
        this.kind = kind;
        this.width = width;
        this.height = height;
        this.anchor = anchor;
    }

    public abstract Component title();

    /** Draw the window in GUI pixels, origin top-left. {@code mouseX/Y} are -1 when not hovered. */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    public boolean mouseClicked(double x, double y, int button) {
        return false;
    }

    public boolean mouseReleased(double x, double y, int button) {
        return false;
    }

    public boolean mouseScrolled(double x, double y, double amount) {
        return false;
    }

    /** Client tick (20 Hz). For per-frame motion use {@link #update}. */
    public void tick() {}

    // --- keyboard capture (Alt mode) -----------------------------------------------------------

    /**
     * In Alt mode the keyboard goes to this window while the hand hovers it (and stays with it while
     * the hand is over empty space): desktop apps, JS apps with {@code "keyboard": true}. Only the Alt
     * toggle itself still reaches Minecraft, so the player can always leave.
     */
    public boolean wantsKeyboard() {
        return false;
    }

    /** Capture started / ended (show a keyboard badge, send focus to the app). */
    public void keyboardFocus(boolean focused) {}

    /** GLFW key event while captured: action 1 = press, 0 = release, 2 = repeat. */
    public void keyEvent(int key, int scancode, int action, int mods) {}

    /** Text input while captured (one Unicode code point). */
    public void charTyped(int codepoint, int mods) {}

    /** Hand pointer over the content, window pixels; called every client tick while hovered. */
    public void mouseMoved(double x, double y) {}

    public boolean hasGrabBar() {
        return true;
    }

    /** Drawn even while the windows are hidden (HUD off): the gesture wheel is the hand's own UI. */
    public boolean showsWhenHidden() {
        return false;
    }

    /** Gear menu + Lock shown? Default: every window that can be grabbed (not dock, toasts, wheel). */
    public boolean hasSettings() {
        return hasGrabBar();
    }

    /** The chrome × in the top-right corner. Default: every window with a gear. */
    public boolean hasCloseButton() {
        return hasSettings();
    }

    /** May this window ever be shown to other players? (HostDesktopWindow: never.) */
    protected boolean allowPublic() {
        return true;
    }

    /**
     * Does RMB over the content mean something here (detached container: split a stack / place one;
     * desktop app: right click)? If false, RMB over the window is simply swallowed — the gesture wheel
     * opens only over empty space, never "through" a window.
     */
    public boolean acceptsSecondaryClick() {
        return wantsKeyboard();
    }

    // --- input ---------------------------------------------------------------------------------

    /** Single entry for clicks in window pixels: chrome first (gear, menu, lock, ×), then the content. */
    public final boolean click(double x, double y, int button, PlayerView view) {
        pressToContent = false;
        if (!hasSettings()) {
            pressToContent = true;
            return mouseClicked(x, y, button);
        }
        // RMB/MMB never operate the chrome (no accidental × or lock); over chrome or a locked window they are swallowed
        if (button != 0 && (chrome.locked() || chrome.partAt(x, y, width, height, hasCloseButton()) != WindowChrome.Part.NONE)) return true;
        switch (chrome.click(x, y, width, height, hasCloseButton(), allowPublic())) {
            case PASS -> {
                pressToContent = true;
                return mouseClicked(x, y, button);
            }
            case CLOSE_WINDOW -> closeRequested = true;
            case ANCHOR_BODY -> switchAnchor(SpatialAnchor.Mode.BODY, view);
            case ANCHOR_WORLD -> switchAnchor(SpatialAnchor.Mode.WORLD, view);
            case CONSUMED -> {}
        }
        return true;
    }

    public final boolean release(double x, double y, int button) {
        if (!pressToContent) return true; // the press hit the chrome (or a locked window): swallow its release
        pressToContent = false;
        return !chrome.locked() && mouseReleased(x, y, button);
    }

    public final boolean scroll(double x, double y, double amount) {
        if (hasSettings() && chrome.swallowsScroll()) return true;
        return mouseScrolled(x, y, amount);
    }

    // --- chrome rendering ----------------------------------------------------------------------

    /** Drawn by WindowSurface over {@link #renderContent}: lock frame, ×, gear/padlock, open menu. */
    final void renderChrome(GuiGraphics g, int mx, int my) {
        if (!hasSettings()) return;
        boolean closable = hasCloseButton();
        WindowChrome.Part hot = chrome.partAt(mx, my, width, height, closable);
        boolean locked = chrome.locked();
        if (locked) {
            g.fill(0, 0, width, 1, LOCK_RED);
            g.fill(0, height - 1, width, height, LOCK_RED);
            g.fill(0, 0, 1, height, LOCK_RED);
            g.fill(width - 1, 0, width, height, LOCK_RED);
        }

        if (closable) {
            WindowChrome.Box c = WindowChrome.close(width, height);
            if (!locked && hot == WindowChrome.Part.CLOSE_WINDOW) fill(g, c, 0x50F43F5E);
            GlassStyle.centered(g, Component.literal("\u00d7"), c.x() + c.w() / 2 + 1, c.y() + 2,
                locked ? 0x50E2E8F0 : hot == WindowChrome.Part.CLOSE_WINDOW ? GlassStyle.TEXT : GlassStyle.MUTED);
        }

        WindowChrome.Box gb = WindowChrome.gear(width, height);
        boolean gearHot = hot == WindowChrome.Part.GEAR;
        fill(g, gb, gearHot || chrome.menuOpen() ? 0x70FFFFFF : 0x40000000);
        if (locked) padlock(g, gb.x(), gb.y(), gearHot ? GlassStyle.TEXT : GlassStyle.DANGER);
        else GlassStyle.centered(g, Component.literal("\u2699"), gb.x() + GEAR / 2 + 1, gb.y() + 2, gearHot ? GlassStyle.TEXT : GlassStyle.MUTED);

        if (chrome.menuOpen()) renderMenu(g, hot);
    }

    private void renderMenu(GuiGraphics g, WindowChrome.Part hot) {
        WindowChrome.Box m = WindowChrome.menu(width, height);
        GlassStyle.surface(g, m.x(), m.y(), m.w(), m.h(), false);
        boolean labels = WindowChrome.hasLabels(m);

        // header + ×
        GlassStyle.text(g, Component.translatable("kvr.window.menu.title"), m.x() + 5, m.y() + 3, GlassStyle.ACCENT);
        WindowChrome.Box x = WindowChrome.menuClose(m);
        if (hot == WindowChrome.Part.MENU_CLOSE) fill(g, x, HOT);
        GlassStyle.centered(g, Component.literal("\u00d7"), x.x() + x.w() / 2 + 1, x.y() + 2, hot == WindowChrome.Part.MENU_CLOSE ? GlassStyle.TEXT : GlassStyle.MUTED);
        g.fill(m.x() + 3, m.y() + WindowChrome.ROW - 1, m.x() + m.w() - 3, m.y() + WindowChrome.ROW, 0x30FFFFFF);

        // anchor: Body / World (Follow = dock-like system panels, neither segment lit)
        SpatialAnchor.Mode mode = anchor.mode();
        if (labels) GlassStyle.text(g, Component.translatable("kvr.window.menu.anchor"), m.x() + 5, m.y() + WindowChrome.ROW + 3, GlassStyle.MUTED);
        segment(g, WindowChrome.segment(m, 1, 0), "kvr.window.menu.body", mode == SpatialAnchor.Mode.BODY, hot == WindowChrome.Part.ANCHOR_BODY, true);
        segment(g, WindowChrome.segment(m, 1, 1), "kvr.window.menu.world", mode == SpatialAnchor.Mode.WORLD, hot == WindowChrome.Part.ANCHOR_WORLD, true);

        // visibility: Public / Private
        boolean pub = isPublic();
        if (labels) GlassStyle.text(g, Component.translatable("kvr.window.menu.visibility"), m.x() + 5, m.y() + 2 * WindowChrome.ROW + 3, GlassStyle.MUTED);
        segment(g, WindowChrome.segment(m, 2, 0), "kvr.window.menu.public", pub, hot == WindowChrome.Part.VIS_PUBLIC, allowPublic());
        segment(g, WindowChrome.segment(m, 2, 1), "kvr.window.menu.private", !pub, hot == WindowChrome.Part.VIS_PRIVATE, true);

        // lock
        WindowChrome.Box lock = WindowChrome.row(m, 3);
        if (hot == WindowChrome.Part.LOCK) g.fill(lock.x() + 1, lock.y(), lock.x() + lock.w() - 1, lock.y() + lock.h(), 0x30F43F5E);
        padlock(g, lock.x() + 3, lock.y() + 1, GlassStyle.DANGER);
        GlassStyle.text(g, Component.translatable("kvr.window.menu.lock"), lock.x() + 17, lock.y() + 3, GlassStyle.DANGER);
    }

    private static void segment(GuiGraphics g, WindowChrome.Box b, String key, boolean on, boolean hot, boolean enabled) {
        fill(g, b, on ? SEG_ON : hot && enabled ? HOT : SEG_OFF);
        int color = !enabled ? 0x50E2E8F0 : on || hot ? GlassStyle.TEXT : GlassStyle.MUTED;
        GlassStyle.centered(g, Component.translatable(key), b.x() + b.w() / 2, b.y() + 2, color);
    }

    /** 11×11 padlock from fills (no font glyph for it): shackle, body, keyhole. */
    private static void padlock(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 3, y + 1, x + 8, y + 2, c);
        g.fill(x + 3, y + 1, x + 4, y + 5, c);
        g.fill(x + 7, y + 1, x + 8, y + 5, c);
        g.fill(x + 2, y + 5, x + 9, y + 10, c);
        g.fill(x + 5, y + 6, x + 6, y + 9, 0xFF0F172A);
    }

    private static void fill(GuiGraphics g, WindowChrome.Box b, int argb) {
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), argb);
    }

    // --- lifecycle -----------------------------------------------------------------------------

    /** Short plain text a HackGlasses wearer (and, for public windows, other players) may read (≤ 256 chars). */
    public String spyText() {
        return "";
    }

    public boolean isVisible() {
        return !minimized;
    }

    /** Called once when removed from the manager. */
    public void onClosed() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
    }

    /** Per-frame: resolve the anchor (or keep the pose the hand is holding) and animate open/close. */
    public final void update(PlayerView view, float dt) {
        pose = held != null ? held : anchor.resolve(view, dt);
        float target = minimized ? 0f : 1f;
        open += (target - open) * (1f - (float) Math.exp(-14f * dt));
    }

    /** Grab in progress: the hand overrides the anchor. */
    public final void hold(SpatialPose pose) {
        this.held = pose;
    }

    /** Grab released: re-base the current anchor so the window stays where it was dropped. */
    public final void release(PlayerView view) {
        if (held == null) return;
        anchor = anchor.rebase(held, view);
        held = null;
    }

    /** Body: travels with the player (pulled within arm's reach); World: frozen where it is now. */
    public final void switchAnchor(SpatialAnchor.Mode mode, PlayerView view) {
        if (pose != null && anchor.mode() != mode) anchor = SpatialAnchor.of(mode, pose, view);
    }

    /** Render the window into its offscreen texture (called by SpatialRenderer once per frame). */
    public final void paint(int mouseX, int mouseY, float partialTick) {
        if (surface == null) surface = new WindowSurface(id);
        surface.render(this, mouseX, mouseY, partialTick);
    }

    /** Texture the world pass draws; null until the first paint. */
    @Nullable
    public final ResourceLocation texture() {
        return surface == null ? null : surface.texture();
    }

    /** Ease-out scale for the open/minimise animation. */
    public final float scale() {
        float t = Math.max(0f, Math.min(1f, open));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public final double widthMeters() {
        return width / PX_PER_METER * scale();
    }

    public final double heightMeters() {
        return height / PX_PER_METER * scale();
    }

    public final String id() { return id; }
    public final Kind kind() { return kind; }
    public final int width() { return width; }
    public final int height() { return height; }
    public final SpatialAnchor anchor() { return anchor; }
    public final void setAnchor(SpatialAnchor anchor) { this.anchor = anchor; }
    @Nullable public final SpatialPose pose() { return pose; }
    public final boolean isHeld() { return held != null; }
    public final boolean isMinimized() { return minimized; }
    public final void setMinimized(boolean minimized) { this.minimized = minimized; }

    public final boolean isLocked() { return hasSettings() && chrome.locked(); }
    public final void setLocked(boolean locked) { if (hasSettings()) chrome.setLocked(locked); }
    public final boolean isMenuOpen() { return chrome.menuOpen(); }

    /**
     * Public: materialised for other players nearby. Private: not shown to anyone — but a HackGlasses wearer
     * (secret artifact) can intercept it, unless {@link #mayLeaveDevice()} is false.
     */
    public final boolean isPublic() {
        return hasSettings() && allowPublic() && Boolean.TRUE.equals(chrome.publicChoice());
    }

    /** False for desktop-app windows: they are never reported to the server, not even as Private. */
    public final boolean mayLeaveDevice() {
        return allowPublic();
    }

    public final void setPublic(boolean value) { chrome.setPublic(value && allowPublic()); }
    /** False until the user or the app picked Public/Private; WindowManager then applies the config default. */
    public final boolean hasPublicChoice() { return chrome.publicChoice() != null; }

    /** The × was clicked; {@link WindowManager#tick()} closes the window. */
    public final boolean closeRequested() { return closeRequested; }
    public final void requestClose() { closeRequested = true; }
}
