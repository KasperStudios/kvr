package com.kasperstudios.kvr.spatial.window;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base of every surface in KVR OS. A window draws itself in GUI pixels with a normal
 * {@link GuiGraphics} into its own offscreen {@link WindowSurface}; {@code SpatialRenderer} then puts
 * that texture on a quad in the world. Input arrives already converted to window pixels.
 *
 * <p>Under the window floats the grab bar (ADR-002): the handle moves the window, the small toggle
 * next to it switches Body/World anchoring. System panels can hide it.
 */
public abstract class SpatialWindow {

    /** 400 GUI px = 1 block, so a 320×200 window is 0.8×0.5 m. */
    public static final float PX_PER_METER = 400f;
    public static final int GRAB_GAP = 6;
    public static final int GRAB_W = 56;
    public static final int GRAB_H = 4;
    public static final int GRAB_HIT_PAD = 4;
    public static final int TOGGLE_SIZE = 8;
    public static final int TOGGLE_GAP = 6;

    /** What the window costs. Only APP windows take a RAM slot (SPEC 2.5: vanilla screens are free). */
    public enum Kind { APP, VANILLA, SYSTEM }

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

    public boolean hasGrabBar() {
        return true;
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

    public final void switchAnchor(SpatialAnchor.Mode mode, PlayerView view) {
        if (pose != null) anchor = SpatialAnchor.of(mode, pose, view);
    }

    /** Render the window into its offscreen texture (called by SpatialRenderer once per frame). */
    public final void paint(int mouseX, int mouseY, float partialTick) {
        if (surface == null) surface = new WindowSurface();
        surface.render(this, mouseX, mouseY, partialTick);
    }

    public final int textureId() {
        return surface == null ? 0 : surface.textureId();
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
}
