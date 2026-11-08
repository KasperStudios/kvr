package com.kasperstudios.kvr.spatial.window;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import com.kasperstudios.kvr.spatial.space.VirtualSpaces;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Owns every open window. Enforces the RAM rule from SPEC 2.5: the number of simultaneously open
 * {@link SpatialWindow.Kind#APP} windows is capped by the RAM tier; detached vanilla screens and
 * system panels never count.
 *
 * <p>0033: APP/VANILLA windows belong to a Virtual Space slot ({@link VirtualSpaces}). Only the active slot is
 * drawn, hit by the ray and touched by the minimise gestures; the others keep ticking and keep their RAM slot.
 */
public final class WindowManager {

    public enum OpenResult { OPENED, ALREADY_OPEN, RAM_FULL }

    private final List<SpatialWindow> windows = new ArrayList<>();
    private final IntSupplier ramWindows;
    @Nullable private SpatialWindow focused;
    /** HUD_OFF: the OS keeps running (apps tick), but nothing is drawn or hit by the hand ray. */
    private boolean hidden;

    /** @param ramWindows current RAM limit (1/2/4/8/16), read from the worn glasses build */
    public WindowManager(IntSupplier ramWindows) {
        this.ramWindows = ramWindows;
    }

    public OpenResult open(SpatialWindow window) {
        if (windows.stream().anyMatch(w -> w.id().equals(window.id()))) return OpenResult.ALREADY_OPEN;
        if (window.kind() == SpatialWindow.Kind.APP && appsOpen() >= ramWindows.getAsInt()) return OpenResult.RAM_FULL;
        windows.add(window);
        VirtualSpaces.claim(window);
        focused = window;
        return OpenResult.OPENED;
    }

    public void close(SpatialWindow window) {
        if (windows.remove(window)) window.onClosed();
        VirtualSpaces.forget(window);
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
        windows.stream().filter(w -> w.kind() != SpatialWindow.Kind.SYSTEM && !w.isLocked() && VirtualSpaces.shown(w)).forEach(w -> w.setMinimized(true));
    }

    /** Gesture 3: the window in focus (last clicked or under the hand ray). */
    public void minimizeFocused() {
        if (focused != null && focused.kind() != SpatialWindow.Kind.SYSTEM && !focused.isLocked() && VirtualSpaces.shown(focused)) focused.setMinimized(true);
    }

    /** Windows other players may see (HackGlasses projection, future public windows). Only the active space. */
    public java.util.List<SpatialWindow> publicWindows() {
        return windows.stream().filter(w -> w.isPublic() && VirtualSpaces.shown(w)).toList();
    }

    /** 0033: a window of another space (dock / launcher click) switches to that space first. */
    public void restore(SpatialWindow window) {
        VirtualSpaces.reveal(window);
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

    /** Client tick. Copy first: a window may close itself (e.g. its container menu went away). */
    public void tick() {
        for (SpatialWindow w : List.copyOf(windows)) w.tick();
    }

    /** Nearest window (content or grab bar) hit by the hand ray. */
    public Optional<SpatialRaycast.RayHit> pick(SpatialRaycast.Ray ray) {
        if (hidden) return Optional.empty();
        return windows.stream()
            .filter(w -> w.isVisible() && VirtualSpaces.shown(w))
            .map(w -> SpatialRaycast.intersect(ray, w))
            .flatMap(Optional::stream)
            .min(Comparator.comparingDouble(SpatialRaycast.RayHit::distance));
    }

    /** Windows to draw this frame, including ones still playing the minimise animation. */
    public List<SpatialWindow> drawable() {
        List<SpatialWindow> out = new ArrayList<>();
        if (hidden) return out;
        for (SpatialWindow w : windows) {
            if (w.pose() != null && VirtualSpaces.shown(w) && (w.isVisible() || (w.isMinimized() && w.scale() > 0.02f))) out.add(w);
        }
        return out;
    }

    public List<SpatialWindow> all() {
        return Collections.unmodifiableList(windows);
    }

    /** Minimised windows of the active space (dock tray). */
    public List<SpatialWindow> minimized() {
        return windows.stream().filter(w -> w.isMinimized() && VirtualSpaces.shown(w)).toList();
    }

    public <T extends SpatialWindow> Optional<T> find(Class<T> type) {
        return windows.stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public void closeAll() {
        for (SpatialWindow w : List.copyOf(windows)) close(w);
        VirtualSpaces.reset(); // glasses off / left the world: back to the real world
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean hidden() {
        return hidden;
    }
}
