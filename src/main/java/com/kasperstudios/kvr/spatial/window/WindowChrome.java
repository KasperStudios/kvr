package com.kasperstudios.kvr.spatial.window;

import org.jetbrains.annotations.Nullable;

/**
 * Window chrome as pure logic (no Minecraft classes, fully unit-tested): the close cross in the
 * top-right corner, the gear in the bottom-right corner and the gear menu.
 *
 * <pre>
 * ┌ Настройки окна ─────────── × ┐   header, × closes the menu (the only "close" in the menu)
 * │ Привязка   [ Тело ] [ Мир  ] │   Body: travels with the player / World: frozen in the world
 * │ Видимость  [Публично][Лично] │   Public: materialises for other players / Private: owner only
 * │ Заблокировать окно           │   Lock: misclick protection, the gear turns into a padlock
 * └──────────────────────────────┘
 * </pre>
 *
 * Rules:
 * <ul>
 *   <li>Picking Body/World or Public/Private keeps the menu open (you see the result); the menu
 *       closes with its ×, the gear, or a click anywhere else in the window (that click is swallowed).</li>
 *   <li>Locked: every click is swallowed (content, ×, grab bar is checked by the caller); only the
 *       padlock in the gear's place reacts and unlocks.</li>
 * </ul>
 * Anchor state lives in the window (it needs the player's view); everything else lives here.
 */
public final class WindowChrome {

    public static final int GEAR = 11;
    public static final int CLOSE = 11;
    public static final int INSET = 3;
    public static final int MENU_W = 184;
    public static final int ROW = 14;
    /** header, anchor, visibility, lock */
    public static final int ROWS = 4;
    static final int LABEL_W = 64;
    /** Narrower menus drop the label column and keep only the segments. */
    static final int LABELS_FROM = 170;

    public enum Part { NONE, CLOSE_WINDOW, GEAR, MENU, MENU_CLOSE, ANCHOR_BODY, ANCHOR_WORLD, VIS_PUBLIC, VIS_PRIVATE, LOCK }

    /** What the window must do with a click after the chrome had its say. */
    public enum Action { PASS, CONSUMED, CLOSE_WINDOW, ANCHOR_BODY, ANCHOR_WORLD }

    public record Box(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private boolean menuOpen;
    private boolean locked;
    @Nullable private Boolean publicChoice;

    // --- geometry (window pixels, origin top-left) ----------------------------------------------

    public static Box gear(int w, int h) {
        return new Box(w - GEAR - INSET, h - GEAR - INSET, GEAR, GEAR);
    }

    public static Box close(int w, int h) {
        return new Box(w - CLOSE - INSET, INSET, CLOSE, CLOSE);
    }

    /** Above the gear, right-aligned; clamped inside the window. */
    public static Box menu(int w, int h) {
        int mw = Math.max(40, Math.min(MENU_W, w - 4));
        int mh = ROWS * ROW;
        int x = Math.max(2, w - mw - 4);
        int y = Math.max(2, gear(w, h).y() - 2 - mh);
        return new Box(x, y, mw, mh);
    }

    public static Box row(Box menu, int i) {
        return new Box(menu.x(), menu.y() + i * ROW, menu.w(), ROW);
    }

    public static boolean hasLabels(Box menu) {
        return menu.w() >= LABELS_FROM;
    }

    /** Segment {@code index} (0 = left) of a two-way switch row (1 = anchor, 2 = visibility). */
    public static Box segment(Box menu, int row, int index) {
        int left = hasLabels(menu) ? LABEL_W : 4;
        int segW = (menu.w() - left - 4) / 2;
        return new Box(menu.x() + left + index * segW, menu.y() + row * ROW + 1, segW - 2, ROW - 2);
    }

    public static Box menuClose(Box menu) {
        return new Box(menu.x() + menu.w() - 13, menu.y() + 1, 12, 12);
    }

    // --- hit test --------------------------------------------------------------------------------

    public Part partAt(double x, double y, int w, int h, boolean closable) {
        if (gear(w, h).contains(x, y)) return Part.GEAR;
        if (menuOpen) {
            Box m = menu(w, h);
            if (m.contains(x, y)) {
                if (menuClose(m).contains(x, y)) return Part.MENU_CLOSE;
                if (segment(m, 1, 0).contains(x, y)) return Part.ANCHOR_BODY;
                if (segment(m, 1, 1).contains(x, y)) return Part.ANCHOR_WORLD;
                if (segment(m, 2, 0).contains(x, y)) return Part.VIS_PUBLIC;
                if (segment(m, 2, 1).contains(x, y)) return Part.VIS_PRIVATE;
                if (row(m, 3).contains(x, y)) return Part.LOCK;
                return Part.MENU;
            }
        }
        if (closable && close(w, h).contains(x, y)) return Part.CLOSE_WINDOW;
        return Part.NONE;
    }

    /**
     * One click in window pixels. State changes (menu, lock, visibility) are applied here; anchor
     * switches and closing are returned to the window, which owns the anchor and its manager.
     */
    public Action click(double x, double y, int w, int h, boolean closable, boolean publicAllowed) {
        Part p = partAt(x, y, w, h, closable);
        if (locked) {
            if (p == Part.GEAR) locked = false;
            return Action.CONSUMED; // misclick protection: nothing reaches the app, the × or the world
        }
        return switch (p) {
            case GEAR -> {
                menuOpen = !menuOpen;
                yield Action.CONSUMED;
            }
            case MENU_CLOSE -> {
                menuOpen = false;
                yield Action.CONSUMED;
            }
            case ANCHOR_BODY -> Action.ANCHOR_BODY;
            case ANCHOR_WORLD -> Action.ANCHOR_WORLD;
            case VIS_PUBLIC -> {
                if (publicAllowed) publicChoice = Boolean.TRUE;
                yield Action.CONSUMED;
            }
            case VIS_PRIVATE -> {
                publicChoice = Boolean.FALSE;
                yield Action.CONSUMED;
            }
            case LOCK -> {
                locked = true;
                menuOpen = false;
                yield Action.CONSUMED;
            }
            case MENU -> Action.CONSUMED;
            case CLOSE_WINDOW -> {
                menuOpen = false;
                yield Action.CLOSE_WINDOW;
            }
            case NONE -> {
                if (!menuOpen) yield Action.PASS;
                menuOpen = false; // click beside the open menu just closes it
                yield Action.CONSUMED;
            }
        };
    }

    /** Wheel: swallowed while locked or while the menu is open. */
    public boolean swallowsScroll() {
        return locked || menuOpen;
    }

    // --- state -----------------------------------------------------------------------------------

    public boolean menuOpen() { return menuOpen; }
    public boolean locked() { return locked; }

    public void setLocked(boolean value) {
        locked = value;
        if (value) menuOpen = false;
    }

    public void closeMenu() { menuOpen = false; }

    /** null = the user/app never chose; {@code WindowManager.open} then applies the config default. */
    @Nullable public Boolean publicChoice() { return publicChoice; }

    public void setPublic(boolean value) { publicChoice = value; }
}
