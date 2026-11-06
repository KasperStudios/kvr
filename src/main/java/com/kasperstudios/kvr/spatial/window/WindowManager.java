package com.kasperstudios.kvr.spatial.window;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns every open window. Enforces the RAM rule from SPEC 2.5: the number of simultaneously open
 * {@link SpatialWindow.Kind#APP} windows is capped by the RAM tier; detached vanilla screens and
 * system panels never count.
 *
 * <p>Window settings from the gear menu: the × is honoured on the next {@link #tick()}, locked
 * windows ignore the minimise gestures, new windows without an explicit Public/Private choice get
 * {@code default_window_public} from {@code kvr-client.toml}.
 */
public final class WindowManager {

    public enum OpenResult { OPENED, ALREADY_OPEN, RAM_FULL }

    private final List<SpatialWindow> windows = new ArrayList<>();
    private final IntSupplier ramWindows;
    private final BooleanSupplier defaultPublic;
    @Nullable private SpatialWindow focused;
    /** HUD_OFF: the OS keeps running (apps tick), but nothing is drawn or hit by the hand ray. */
    private boolean hidden;

    /** @param ramWindows current RAM limit (1/2/4/8/16), read from the worn glasses build */
    public WindowManager(IntSupplier ramWindows) {
        this(ramWindows, () -> KvrConfig.client().defaultWindowPublic());
    }

    /** Test / tooling constructor: explicit default visibility, no config file. */
    public WindowManager(IntSupplier ramWindows, BooleanSupplier defaultPublic) {
        this.ramWindows = ramWindows;
        this.defaultPublic = defaultPublic;
    }

    public OpenResult open(SpatialWindow window) {
        if (windows.stream().anyMatch(w -> w.id().equals(window.id()))) return OpenResult.ALREADY_OPEN;
        if (window.kind() == SpatialWindow.Kind.APP && appsOpen() >= ramWindows.getAsInt()) return OpenResult.RAM_FULL;
        if (window.hasSettings() && !window.hasPublicChoice()) window.setPublic(defaultPublic.getAsBoolean());
        windows.add(window);
        focused = window;
        return OpenResult.OPENED;
    }

    public void close(SpatialWindow window) {
        if (windows.remove(window)) window.onClosed();
        if (focused == window) focused = null;
    }

    public int appsOpen() {
        return (int) windows.stream().filter(w -> w.kind() == SpatialWindow.Kind.APP).count();
    }

    public int ramLimit() {
        return ramWindows.getAsInt();
    }

    /** Gesture 2: hide every app/vanilla window into the dock (locked windows stay). */
    public void minimizeAll() {
        windows.stream().filter(w -> w.kind() != SpatialWindow.Kind.SYSTEM && !w.isLocked()).forEach(w -> w.setMinimized(true));
    }

    /** Gesture 3: the window in focus (last clicked or under the hand ray), unless it is locked. */
    public void minimizeFocused() {
        SpatialWindow target = focused;
        if (target == null || target.isMinimized() || target.kind() == SpatialWindow.Kind.SYSTEM || target.isLocked()) {
            // Fallback: pick the closest non-minimized app/vanilla window
            target = windows.stream()
                    .filter(w -> w.kind() != SpatialWindow.Kind.SYSTEM && !w.isMinimized() && !w.isLocked())
                    .findFirst()
                    .orElse(null);
        }
        if (target != null) {
            target.setMinimized(true);
        }
    }

    /** Windows set to Public in the gear menu: materialised for other players, readable by HackGlasses. */
    public List<SpatialWindow> publicWindows() {
        return windows.stream().filter(SpatialWindow::isPublic).toList();
    }

    public void restore(SpatialWindow window) {
        window.setMinimized(false);
        focused = window;
    }

    public void focus(SpatialWindow window) {
        focused = window;
    }

    /** Per frame, before rendering. */
    public void update(PlayerView view, float dt) {
        for (SpatialWindow w : windows) w.update(view, dt);
    }

    /**
     * Client tick. Windows whose × was clicked are closed first; then every window ticks.
     * Copy first: a window may close itself (e.g. its container menu went away).
     */
    public void tick() {
        for (SpatialWindow w : List.copyOf(windows)) {
            if (w.closeRequested()) close(w);
        }
        for (SpatialWindow w : List.copyOf(windows)) w.tick();
    }

    /** Nearest window (content or grab bar) hit by the hand ray. */
    public Optional<SpatialRaycast.RayHit> pick(SpatialRaycast.Ray ray) {
        if (hidden) return Optional.empty();
        return windows.stream()
            .filter(SpatialWindow::isVisible)
            .map(w -> SpatialRaycast.intersect(ray, w))
            .flatMap(Optional::stream)
            .min(Comparator.comparingDouble(SpatialRaycast.RayHit::distance));
    }

    /** Windows to draw this frame, including ones still playing the minimise animation. */
    public List<SpatialWindow> drawable() {
        List<SpatialWindow> out = new ArrayList<>();
        if (hidden) return out;
        for (SpatialWindow w : windows) if (w.pose() != null && (w.isVisible() || (w.isMinimized() && w.scale() > 0.02f))) out.add(w);
        return out;
    }

    public List<SpatialWindow> all() {
        return Collections.unmodifiableList(windows);
    }

    public List<SpatialWindow> minimized() {
        return windows.stream().filter(SpatialWindow::isMinimized).toList();
    }

    public <T extends SpatialWindow> Optional<T> find(Class<T> type) {
        return windows.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public void closeAll() {
        for (SpatialWindow w : List.copyOf(windows)) close(w);
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean hidden() {
        return hidden;
    }
}
