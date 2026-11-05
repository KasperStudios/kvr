package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
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
 *   <li>LMB on content → {@code mouseClicked}/{@code mouseReleased} in window pixels.</li>
 *   <li>LMB on the grab bar → {@link GrabController}; on the toggle → Body ⇄ World.</li>
 *   <li>RMB (Alt mode, glasses on) → gesture wheel at the hand; release picks the gesture.</li>
 *   <li>Wheel → grab depth while holding, otherwise scroll in the hovered window.</li>
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
        cursor.tick();
        windows.tick();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);

        if (!cursor.active() || mc.screen != null) {
            grab.end(view);
            closeWheel(false);
            hover = null;
            lastRay = null;
            return;
        }

        SpatialRaycast.Ray ray = cursor.ray(view);
        lastRay = ray;
        if (!grab.holding()) hover = windows.pick(ray).orElse(null);

        handleAttack(mc, view, ray);
        if (glassesWorn.getAsBoolean()) handleUse(mc, view, ray);
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
                    case GRAB_BAR -> grab.begin(hover);
                    case ANCHOR_TOGGLE -> w.switchAnchor(w.anchor().mode() == SpatialAnchor.Mode.WORLD ? SpatialAnchor.Mode.BODY : SpatialAnchor.Mode.WORLD, view);
                    case CONTENT -> w.mouseClicked(hover.px(), hover.py(), 0);
                }
            }
            if (attackWasDown && !down && hover.region() == SpatialRaycast.Region.CONTENT) w.mouseReleased(hover.px(), hover.py(), 0);
        }
        attackWasDown = down;
    }

    private void handleUse(Minecraft mc, PlayerView view, SpatialRaycast.Ray ray) {
        KeyMapping use = mc.options.keyUse;
        while (use.consumeClick()) {
            if (wheel == null) {
                wheel = GestureWheel.spawn(ray, view);
                windows.open(wheel);
            }
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
        return hover != null && hover.region() == SpatialRaycast.Region.CONTENT && hover.window().mouseScrolled(hover.px(), hover.py(), amount);
    }

    /** While true, vanilla must not keep mining or using items \"through\" the panel. */
    public boolean capturesMouse() {
        return cursor.active() && (hover != null || grab.holding() || wheel != null);
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
