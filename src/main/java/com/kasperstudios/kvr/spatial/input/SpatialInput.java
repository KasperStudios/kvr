package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.ui.GestureWheel;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Routes the hand to windows once per client tick, at tick START — before vanilla
 * {@code handleKeybinds} — so clicks that land on a window are consumed and never reach the world.
 * Held-button mining/using behind a panel is stopped by {@code MinecraftMixin} via {@link #capturesMouse()}.
 *
 * <ul>
 *   <li>LMB on content → chrome (×, gear menu, lock) or {@code mouseClicked}/{@code mouseReleased} in window pixels.</li>
 *   <li>LMB on the grab bar → {@link GrabController} (not while the window is locked).</li>
 *   <li>RMB (Alt mode, glasses on) → gesture wheel at the hand; release picks the gesture.</li>
 *   <li>Wheel → grab depth while holding, otherwise scroll in the hovered window.</li>
 *   <li>Keyboard (Alt mode): automatically captured by the hovered window if it {@code wantsKeyboard()}
 *       and stays with it while the hand points at empty space; hovering another window or leaving
 *       Alt mode gives it back. {@code KeyboardHandlerMixin} routes keys here; the Alt toggle always passes.</li>
 * </ul>
 */
public final class SpatialInput {

    private final WindowManager windows;
    private final SpatialCursor cursor;
    private final BooleanSupplier glassesWorn;
    private final Consumer<GestureWheel.Gesture> gestures;
    private final GrabController grab = new GrabController();

    @Nullable private SpatialRaycast.RayHit hover;
    @Nullable private SpatialRaycast.Ray lastRay;
    @Nullable private GestureWheel wheel;
    @Nullable private SpatialWindow keyboard;
    /** RMB pressed on a desktop app: its release belongs there too. */
    @Nullable private SpatialWindow rightDown;
    private boolean attackWasDown;

    public SpatialInput(WindowManager windows, SpatialCursor cursor, BooleanSupplier glassesWorn, Consumer<GestureWheel.Gesture> gestures) {
        this.windows = windows;
        this.cursor = cursor;
        this.glassesWorn = glassesWorn;
        this.gestures = gestures;
    }

    public void tickStart() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null || camera.getEntity() == null) return;
        cursor.tick();
        windows.tick();
        PlayerView view = PlayerView.capture(camera, 1f);

        if (!cursor.active() || mc.screen != null) {
            grab.end(view);
            closeWheel(false);
            hover = null;
            lastRay = null;
            if (rightDown != null) {
                rightDown.release(-1, -1, 1);
                rightDown = null;
            }
            updateKeyboard(false);
            return;
        }

        SpatialRaycast.Ray ray = cursor.ray(view);
        lastRay = ray;
        if (!grab.holding()) hover = windows.pick(ray).orElse(null);
        updateKeyboard(true);
        if (hover != null && hover.region() == SpatialRaycast.Region.CONTENT && !hover.window().isLocked()) {
            hover.window().mouseMoved(hover.px(), hover.py());
        }

        handleAttack(mc, view, ray);
        if (glassesWorn.getAsBoolean()) handleUse(mc, view, ray);
    }

    /** Who gets the keyboard this tick (see class doc). */
    private void updateKeyboard(boolean altMode) {
        SpatialWindow next = keyboard;
        if (!altMode || windows.hidden()) {
            next = null;
        } else if (hover != null) {
            SpatialWindow w = hover.window();
            next = w.wantsKeyboard() && !w.isLocked() ? w : null;
        }
        if (next != null && (!windows.all().contains(next) || !next.isVisible() || next.isLocked())) next = null;
        if (next == keyboard) return;
        if (keyboard != null) keyboard.keyboardFocus(false);
        keyboard = next;
        if (next != null) {
            KeyMapping.releaseAll(); // W held when the capture starts must not keep the player walking
            next.keyboardFocus(true);
        }
    }

    /** From {@code KeyboardHandlerMixin}: true = the key went to a window and vanilla must ignore it. */
    public boolean keyboardKey(int key, int scancode, int action, int mods) {
        SpatialWindow w = keyboard;
        if (w == null || cursor.toggleKey().matches(key, scancode)) return false;
        w.keyEvent(key, scancode, action, mods);
        return true;
    }

    public boolean keyboardChar(int codepoint, int mods) {
        SpatialWindow w = keyboard;
        if (w == null) return false;
        w.charTyped(codepoint, mods);
        return true;
    }

    public Optional<SpatialWindow> keyboardTarget() {
        return Optional.ofNullable(keyboard);
    }

    private void handleAttack(Minecraft mc, PlayerView view, SpatialRaycast.Ray ray) {
        KeyMapping attack = mc.options.keyAttack;
        boolean down = attack.isDown();

        if (grab.holding()) {
            swallow(attack);
            if (down) grab.drag(ray, view);
            else grab.end(view);
            attackWasDown = down;
            return;
        }

        if (hover != null) {
            SpatialWindow w = hover.window();
            while (attack.consumeClick()) {
                windows.focus(w);
                switch (hover.region()) {
                    case GRAB_BAR -> { if (!w.isLocked()) grab.begin(hover); }
                    case CONTENT -> w.click(hover.px(), hover.py(), 0, view);
                }
            }
            if (attackWasDown && !down && hover.region() == SpatialRaycast.Region.CONTENT) w.release(hover.px(), hover.py(), 0);
        }
        attackWasDown = down;
    }

    private void handleUse(Minecraft mc, PlayerView view, SpatialRaycast.Ray ray) {
        KeyMapping use = mc.options.keyUse;
        while (use.consumeClick()) {
            // over a desktop app (any window that takes the keyboard) RMB is the app's right click
            if (wheel == null && rightDown == null && hover != null && hover.region() == SpatialRaycast.Region.CONTENT
                && hover.window().wantsKeyboard() && !hover.window().isLocked()) {
                rightDown = hover.window();
                rightDown.click(hover.px(), hover.py(), 1, view);
                continue;
            }
            if (wheel == null && rightDown == null) {
                wheel = GestureWheel.spawn(ray, view);
                windows.open(wheel);
            }
        }
        if (rightDown != null) {
            if (!use.isDown()) {
                boolean over = hover != null && hover.window() == rightDown;
                rightDown.release(over ? hover.px() : -1, over ? hover.py() : -1, 1);
                rightDown = null;
            }
            return;
        }
        if (wheel == null) return;
        wheel.hover(ray);
        if (!use.isDown()) closeWheel(true);
    }

    private void closeWheel(boolean commit) {
        if (wheel == null) return;
        if (commit && wheel.hovered() != null) gestures.accept(wheel.hovered());
        windows.close(wheel);
        wheel = null;
    }

    private static void swallow(KeyMapping key) {
        while (key.consumeClick()) {
            // consumed: the click belongs to the window, not the world
        }
    }

    /** Mouse wheel from {@code MouseHandlerMixin}; true = consumed. */
    public boolean scroll(double amount) {
        if (!cursor.active()) return false;
        if (grab.holding()) return grab.scroll(amount);
        return hover != null && hover.region() == SpatialRaycast.Region.CONTENT && hover.window().scroll(hover.px(), hover.py(), amount);
    }

    /** While true, vanilla must not keep mining or using items "through" the panel. */
    public boolean capturesMouse() {
        return cursor.active() && (hover != null || grab.holding() || wheel != null || rightDown != null);
    }

    public Optional<SpatialRaycast.RayHit> hover() {
        return Optional.ofNullable(hover);
    }

    public Optional<SpatialRaycast.Ray> ray() {
        return Optional.ofNullable(lastRay);
    }

    public boolean holding(SpatialWindow w) {
        return grab.held() == w;
    }
}
