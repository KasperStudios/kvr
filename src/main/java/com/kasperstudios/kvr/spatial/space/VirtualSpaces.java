package com.kasperstudios.kvr.spatial.space;

import com.kasperstudios.kvr.config.KvrSpaceConfig;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;

/**
 * 0033 — Virtual Space slots (workspaces), ADR-001 gesture 4.
 *
 * <p>Slot {@link #REAL} is the clean world, slots 1..{@link #slots()} are virtual spaces. Every APP / VANILLA
 * window belongs to the slot that was active when it opened; SYSTEM windows (dock, toasts, settings, launcher,
 * power, gesture wheel, picker) are global.
 *
 * <p>0034 (Creator): a space is an isolated SESSION. Switching between spaces (1 ↔ 2) only hides the other
 * space's windows (they keep ticking and keep their poses); going back to the real world ENDS the session — every
 * window opened inside any space is closed (apps stop, RAM slots are freed). Real-world windows are untouched.
 *
 * <p>Ownership lives here (weak map), not in {@link SpatialWindow}: {@code WindowManager} calls {@link #claim},
 * {@link #shown}, {@link #reveal}, {@link #forget}, {@link #reset}. Client thread only.
 */
public final class VirtualSpaces {

    public static final int REAL = 0;
    private static final long ENTER_MS = 350;
    private static final long LEAVE_MS = 250;
    private static final long PULSE_MS = 500;

    private static final Map<SpatialWindow, Integer> owner = new WeakHashMap<>();
    private static int active = REAL;
    private static int previous = REAL;
    private static long changedAt;

    private VirtualSpaces() {}

    // --- state ---------------------------------------------------------------------------------

    public static int active() {
        return active;
    }

    public static boolean inSpace() {
        return active != REAL;
    }

    public static int slots() {
        return KvrSpaceConfig.get().slots();
    }

    /** "Space 2 · Media" / "Real world". */
    public static Component name(int slot) {
        if (slot == REAL) return Component.translatable("kvr.space.real");
        Component label = slot <= 3 ? Component.translatable("kvr.space.label." + slot) : Component.translatable("kvr.space.label.n");
        return Component.translatable("kvr.space.name", slot, label);
    }

    // --- window ownership (called by WindowManager) --------------------------------------------

    /** On open: tag APP / VANILLA windows with the active slot. Already-tagged windows keep their slot. */
    public static void claim(SpatialWindow w) {
        if (w.kind() == SpatialWindow.Kind.SYSTEM || owner.containsKey(w)) return;
        owner.put(w, active);
    }

    public static void forget(SpatialWindow w) {
        owner.remove(w);
    }

    /** Drawn / hit / minimised by gestures in the current slot? Untagged windows are shown (safe default). */
    public static boolean shown(SpatialWindow w) {
        if (w.kind() == SpatialWindow.Kind.SYSTEM) return true;
        Integer s = owner.get(w);
        return s == null || s == active;
    }

    /** -1 for global (SYSTEM / untagged) windows. */
    public static int spaceOf(SpatialWindow w) {
        if (w.kind() == SpatialWindow.Kind.SYSTEM) return -1;
        return owner.getOrDefault(w, -1);
    }

    /** Dock / launcher restored a window that lives in another slot: jump there (like macOS Spaces). */
    public static void reveal(SpatialWindow w) {
        int s = spaceOf(w);
        if (s >= 0 && s != active) switchTo(s);
    }

    /** Number of APP / VANILLA windows in a slot (picker cards). */
    public static int count(int slot) {
        if (!KVRSpatial.ready()) return 0;
        int n = 0;
        for (SpatialWindow w : KVRSpatial.windows().all()) {
            if (w.kind() != SpatialWindow.Kind.SYSTEM && owner.getOrDefault(w, REAL) == slot) n++;
        }
        return n;
    }

    /** Glasses off / left the server: every window is gone, back to the real world without a toast. */
    public static void reset() {
        owner.clear();
        if (active != REAL) {
            previous = active;
            active = REAL;
            changedAt = System.currentTimeMillis();
        }
    }

    // --- switching -----------------------------------------------------------------------------

    public static void switchTo(int slot) {
        int s = Math.max(REAL, Math.min(slots(), slot));
        if (s == active) return;
        previous = active;
        active = s;
        changedAt = System.currentTimeMillis();
        int closed = s == REAL ? endSession() : 0;
        if (KVRSpatial.ready()) {
            KVRSpatial.toasts().push(new ToastPanel.Toast(name(s),
                Component.translatable(s == REAL ? "kvr.space.left" : "kvr.space.entered", s == REAL ? closed : count(s)),
                GlassStyle.id("gesture/virtual_space"), cocoonArgb(), 2200));
        }
    }

    /** 0034: back in the real world — close every window that was opened inside a space. Returns how many. */
    private static int endSession() {
        if (!KVRSpatial.ready()) return 0;
        int n = 0;
        for (SpatialWindow w : java.util.List.copyOf(KVRSpatial.windows().all())) {
            if (w.kind() == SpatialWindow.Kind.SYSTEM) continue;
            Integer slot = owner.get(w);
            if (slot != null && slot != REAL) {
                KVRSpatial.windows().close(w);
                n++;
            }
        }
        return n;
    }

    /** World → 1 → 2 → … → N → World. */
    public static void cycle() {
        switchTo(active >= slots() ? REAL : active + 1);
    }

    /** Gesture 4 ({@code BuiltinApps.enterVirtualSpace}). */
    public static void onGesture() {
        if (KvrSpaceConfig.get().gesture() == KvrSpaceConfig.GestureMode.CYCLE) {
            cycle();
            return;
        }
        SpacePickerWindow.toggle();
    }

    // --- cocoon animation ----------------------------------------------------------------------

    /** 0..1: grows in when a space is entered, fades out when leaving to the real world. */
    public static float cocoonLevel() {
        long dt = System.currentTimeMillis() - changedAt;
        if (active != REAL) return previous == REAL ? Math.min(1f, dt / (float) ENTER_MS) : 1f;
        if (previous == REAL) return 0f;
        return Math.max(0f, 1f - dt / (float) LEAVE_MS);
    }

    /** 1 → 0 flash right after any switch (brighter grid while the windows swap). */
    public static float switchPulse() {
        long dt = System.currentTimeMillis() - changedAt;
        return dt >= PULSE_MS ? 0f : 1f - dt / (float) PULSE_MS;
    }

    public static int cocoonArgb() {
        return 0xFF000000 | KvrSpaceConfig.get().cocoonRgb();
    }
}
