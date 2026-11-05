package com.kasperstudios.kvr.spatial.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Gear menu, lock and × as pure logic: no Minecraft classes involved. */
class WindowChromeTest {

    private static final int W = 240;
    private static final int H = 150;
    private static final double CONTENT_X = 40;
    private static final double CONTENT_Y = 40;

    private static WindowChrome.Action click(WindowChrome c, WindowChrome.Box b) {
        return c.click(b.x() + b.w() / 2.0, b.y() + b.h() / 2.0, W, H, true, true);
    }

    private static WindowChrome.Box menu() {
        return WindowChrome.menu(W, H);
    }

    @Test
    void gearOpensMenuAndAClickBesideItClosesItWithoutReachingTheApp() {
        WindowChrome c = new WindowChrome();
        assertEquals(WindowChrome.Action.CONSUMED, click(c, WindowChrome.gear(W, H)));
        assertTrue(c.menuOpen());
        assertEquals(WindowChrome.Action.CONSUMED, c.click(CONTENT_X, CONTENT_Y, W, H, true, true));
        assertFalse(c.menuOpen());
        assertEquals(WindowChrome.Action.PASS, c.click(CONTENT_X, CONTENT_Y, W, H, true, true));
    }

    @Test
    void menuStaysOpenWhilePickingAndClosesOnItsCross() {
        WindowChrome c = new WindowChrome();
        click(c, WindowChrome.gear(W, H));
        assertEquals(WindowChrome.Action.ANCHOR_BODY, click(c, WindowChrome.segment(menu(), 1, 0)));
        assertEquals(WindowChrome.Action.ANCHOR_WORLD, click(c, WindowChrome.segment(menu(), 1, 1)));
        assertNull(c.publicChoice());
        click(c, WindowChrome.segment(menu(), 2, 0));
        assertEquals(Boolean.TRUE, c.publicChoice());
        click(c, WindowChrome.segment(menu(), 2, 1));
        assertEquals(Boolean.FALSE, c.publicChoice());
        assertTrue(c.menuOpen(), "picking options keeps the menu open");
        assertEquals(WindowChrome.Action.CONSUMED, click(c, WindowChrome.menuClose(menu())));
        assertFalse(c.menuOpen());
    }

    @Test
    void lockSwallowsEverythingAndOnlyThePadlockUnlocks() {
        WindowChrome c = new WindowChrome();
        click(c, WindowChrome.gear(W, H));
        click(c, WindowChrome.row(menu(), 3));
        assertTrue(c.locked());
        assertFalse(c.menuOpen());
        assertTrue(c.swallowsScroll());
        assertEquals(WindowChrome.Action.CONSUMED, c.click(CONTENT_X, CONTENT_Y, W, H, true, true));
        assertEquals(WindowChrome.Action.CONSUMED, click(c, WindowChrome.close(W, H)), "× is disabled while locked");
        assertTrue(c.locked());
        click(c, WindowChrome.gear(W, H));
        assertFalse(c.locked());
        assertFalse(c.menuOpen(), "unlocking does not pop the menu open");
        assertEquals(WindowChrome.Action.PASS, c.click(CONTENT_X, CONTENT_Y, W, H, true, true));
    }

    @Test
    void crossClosesTheWindowOnlyWhenClosable() {
        WindowChrome c = new WindowChrome();
        assertEquals(WindowChrome.Action.CLOSE_WINDOW, click(c, WindowChrome.close(W, H)));
        WindowChrome.Box x = WindowChrome.close(W, H);
        assertEquals(WindowChrome.Action.PASS, c.click(x.x() + 2, x.y() + 2, W, H, false, true));
    }

    @Test
    void publicCannotBePickedWhereItIsNotAllowed() {
        WindowChrome c = new WindowChrome();
        click(c, WindowChrome.gear(W, H));
        WindowChrome.Box pub = WindowChrome.segment(menu(), 2, 0);
        c.click(pub.x() + 2, pub.y() + 2, W, H, true, false);
        assertNull(c.publicChoice());
    }

    @Test
    void menuAndControlsFitInsideEveryWindowSize() {
        int[][] sizes = {{120, 80}, {180, 76}, {188, 178}, {220, 146}, {240, 150}, {480, 300}, {1024, 768}};
        for (int[] s : sizes) {
            int w = s[0];
            int h = s[1];
            WindowChrome.Box m = WindowChrome.menu(w, h);
            assertTrue(m.x() >= 0 && m.y() >= 0 && m.x() + m.w() <= w && m.y() + m.h() <= h, w + "x" + h + " menu " + m);
            WindowChrome.Box g = WindowChrome.gear(w, h);
            assertTrue(g.x() + g.w() <= w && g.y() + g.h() <= h, w + "x" + h + " gear");
            for (int row = 1; row <= 2; row++) {
                WindowChrome.Box a = WindowChrome.segment(m, row, 0);
                WindowChrome.Box b = WindowChrome.segment(m, row, 1);
                assertTrue(a.w() >= 20 && a.x() + a.w() <= b.x() && b.x() + b.w() <= m.x() + m.w(), w + "x" + h + " segments row " + row);
            }
        }
    }
}
