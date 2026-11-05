package com.kasperstudios.kvr.spatial.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** WindowManager + SpatialWindow input routing with the gear chrome (no rendering, no client). */
class WindowManagerTest {

    private static final Vec3 EYE = new Vec3(0, 65.62, 0);
    private static final PlayerView VIEW = PlayerView.at(EYE, 0f, 0f);

    /** 240×150 window 1 m in front of the player; counts what reaches its content. */
    private static final class TestWindow extends SpatialWindow {
        int clicks;
        int releases;
        boolean closed;
        private final boolean settings;

        TestWindow(String id, Kind kind, boolean settings) {
            super(id, kind, 240, 150, new WorldAnchor(SpatialPose.facing(EYE.add(0, 0, 1), EYE)));
            this.settings = settings;
        }

        @Override public Component title() { return null; }
        @Override protected void renderContent(GuiGraphics g, int mx, int my, float pt) {}
        @Override public boolean hasGrabBar() { return settings; }
        @Override public boolean mouseClicked(double x, double y, int b) { clicks++; return true; }
        @Override public boolean mouseReleased(double x, double y, int b) { releases++; return true; }
        @Override public void onClosed() { closed = true; super.onClosed(); }
    }

    private static void clickBox(SpatialWindow w, WindowChrome.Box b) {
        w.click(b.x() + b.w() / 2.0, b.y() + b.h() / 2.0, 0, VIEW);
    }

    @Test
    void crossClosesTheWindowOnTheNextTick() {
        WindowManager m = new WindowManager(() -> 2, () -> false);
        TestWindow w = new TestWindow("app:a", SpatialWindow.Kind.APP, true);
        m.open(w);
        clickBox(w, WindowChrome.close(240, 150));
        assertTrue(w.closeRequested());
        assertEquals(0, w.clicks);
        m.tick();
        assertTrue(m.all().isEmpty());
        assertTrue(w.closed);
        assertEquals(0, m.appsOpen());
    }

    @Test
    void lockedWindowIgnoresClicksCrossAndMinimiseGestures() {
        WindowManager m = new WindowManager(() -> 4, () -> false);
        TestWindow w = new TestWindow("app:a", SpatialWindow.Kind.APP, true);
        m.open(w);
        w.setLocked(true);

        assertTrue(w.click(40, 40, 0, VIEW));
        assertTrue(w.release(40, 40, 0));
        assertEquals(0, w.clicks);
        assertEquals(0, w.releases);

        clickBox(w, WindowChrome.close(240, 150));
        m.tick();
        assertEquals(1, m.all().size(), "× must not close a locked window");

        m.focus(w);
        m.minimizeAll();
        m.minimizeFocused();
        assertFalse(w.isMinimized());

        clickBox(w, WindowChrome.gear(240, 150)); // padlock
        assertFalse(w.isLocked());
        w.click(40, 40, 0, VIEW);
        w.release(40, 40, 0);
        assertEquals(1, w.clicks);
        assertEquals(1, w.releases);
        m.minimizeAll();
        assertTrue(w.isMinimized());
    }

    @Test
    void releaseOfAChromeClickNeverReachesTheContent() {
        TestWindow w = new TestWindow("app:a", SpatialWindow.Kind.APP, true);
        clickBox(w, WindowChrome.gear(240, 150));
        assertTrue(w.isMenuOpen());
        w.release(40, 40, 0);
        assertEquals(0, w.releases);
    }

    @Test
    void menuSwitchesAnchorBetweenBodyAndWorld() {
        WindowManager m = new WindowManager(() -> 2, () -> false);
        TestWindow w = new TestWindow("app:a", SpatialWindow.Kind.APP, true);
        m.open(w);
        m.update(VIEW, 0.016f);
        clickBox(w, WindowChrome.gear(240, 150));
        WindowChrome.Box menu = WindowChrome.menu(240, 150);
        clickBox(w, WindowChrome.segment(menu, 1, 0));
        assertEquals(SpatialAnchor.Mode.BODY, w.anchor().mode());
        clickBox(w, WindowChrome.segment(menu, 1, 1));
        assertEquals(SpatialAnchor.Mode.WORLD, w.anchor().mode());
        assertTrue(w.isMenuOpen());
    }

    @Test
    void defaultVisibilityOnlyFillsAMissingChoice() {
        WindowManager m = new WindowManager(() -> 4, () -> true);
        TestWindow byDefault = new TestWindow("app:a", SpatialWindow.Kind.APP, true);
        TestWindow chosen = new TestWindow("app:b", SpatialWindow.Kind.APP, true);
        TestWindow panel = new TestWindow("kvr:dock", SpatialWindow.Kind.SYSTEM, false);
        chosen.setPublic(false);
        m.open(byDefault);
        m.open(chosen);
        m.open(panel);
        assertTrue(byDefault.isPublic());
        assertFalse(chosen.isPublic());
        assertFalse(panel.isPublic(), "panels without a gear are never public");
        assertEquals(1, m.publicWindows().size());

        WindowManager privateByDefault = new WindowManager(() -> 4, () -> false);
        TestWindow fresh = new TestWindow("app:c", SpatialWindow.Kind.APP, true);
        privateByDefault.open(fresh);
        assertFalse(fresh.isPublic());
        assertTrue(privateByDefault.publicWindows().isEmpty());
    }

    @Test
    void ramLimitCountsOnlyApps() {
        WindowManager m = new WindowManager(() -> 1, () -> false);
        assertEquals(WindowManager.OpenResult.OPENED, m.open(new TestWindow("app:a", SpatialWindow.Kind.APP, true)));
        assertEquals(WindowManager.OpenResult.RAM_FULL, m.open(new TestWindow("app:b", SpatialWindow.Kind.APP, true)));
        assertEquals(WindowManager.OpenResult.OPENED, m.open(new TestWindow("vanilla:1", SpatialWindow.Kind.VANILLA, true)));
        assertEquals(WindowManager.OpenResult.ALREADY_OPEN, m.open(new TestWindow("vanilla:1", SpatialWindow.Kind.VANILLA, true)));
    }
}
