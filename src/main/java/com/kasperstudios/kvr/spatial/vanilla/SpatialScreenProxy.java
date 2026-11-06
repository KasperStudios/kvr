package com.kasperstudios.kvr.spatial.vanilla;

import com.kasperstudios.kvr.mixin.client.AbstractContainerScreenAccessor;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla Screen Detachment (ADR-002 2.1): any container screen gets a small "pin" button; pressing
 * it turns the flat blocking screen into a floating window while the menu stays open and synced.
 *
 * <p>The screen object itself keeps running: we paint it into the window surface (cropped to the
 * container image) and forward clicks in its own coordinates, so slots, shift-clicks, furnace
 * progress and tooltips are 100% vanilla. Detached screens are {@link Kind#VANILLA} and never use
 * RAM slots. LMB and RMB both reach the screen (RMB = take half / place one, like on a flat screen).
 *
 * <p>Vanilla allows one open container menu per player: the player inventory is always available,
 * plus one chest/furnace/etc. Opening another container replaces the menu and the old window
 * closes itself. Hanging many containers at once needs server-side multi-menu support (later block).
 *
 * <p>Player inventory (E, survival/adventure): detachable since 0018. While another container is open
 * the server's current menu is that container, so clicks in the inventory window would be rejected
 * and desync — the window is then dimmed and inert until the container closes. The creative
 * inventory is not detachable (tabs outside the image, search field needs the keyboard).
 */
public final class SpatialScreenProxy extends SpatialWindow {

    private static final int PAD = 6;
    private static final double SPAWN_DISTANCE = 0.9;
    @Nullable private static Screen painting;

    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final Component title;

    private SpatialScreenProxy(AbstractContainerScreen<?> screen, SpatialPose pose) {
        super(idOf(screen), Kind.VANILLA, size(screen, true), size(screen, false), new WorldAnchor(pose));
        this.screen = screen;
        this.menu = screen.getMenu();
        this.title = screen.getTitle();
    }

    private static String idOf(AbstractContainerScreen<?> s) {
        return "vanilla:" + s.getMenu().containerId;
    }

    private static int size(AbstractContainerScreen<?> s, boolean w) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
        return (w ? a.kvr$imageWidth() : a.kvr$imageHeight()) + PAD * 2;
    }

    /** {@code KVRClient.onScreenInit} hook: adds the pin button above the container's top-right corner. */
    public static void attachPinButton(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof AbstractContainerScreen<?> acs) || screen instanceof CreativeModeInventoryScreen) return;
        // Also allow attaching pin button if glasses are on head OR carried, or spatial ready
        if (!KVRSpatial.ready()) return;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) acs;
        int x = a.kvr$leftPos() + a.kvr$imageWidth() - 20;
        int y = Math.max(0, a.kvr$topPos() - 14);
        addWidget.accept(Button.builder(Component.translatable("kvr.screen.pin"), b -> {
                // the screen keeps living inside the window: its own pin button must not show up (or pin twice) there
                b.visible = false;
                b.active = false;
                pin(acs);
            })
            .bounds(x, y, 20, 12)
            .tooltip(Tooltip.create(Component.translatable("kvr.screen.pin.tooltip")))
            .build());
    }

    private static void pin(AbstractContainerScreen<?> acs) {
        Minecraft mc = Minecraft.getInstance();
        // Pinning the inventory again (E → 3D) replaces the old inventory window instead of failing as "already open".
        // Must run while mc.screen is still set: onClosed() then leaves the inventory menu alone.
        String id = idOf(acs);
        List<SpatialWindow> same = KVRSpatial.windows().all().stream().filter(w -> w.id().equals(id)).toList();
        same.forEach(KVRSpatial.windows()::close);

        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
        // Bypass setScreen(null): Screen#removed()/onClose() would close the menu on the server.
        mc.screen = null;
        mc.mouseHandler.grabMouse();
        KVRSpatial.windows().open(new SpatialScreenProxy(acs, SpatialPose.facing(center, view.eye())));
        KVRSpatial.cursor().setActive(true);
    }

    /** {@code ScreenMixin} skips the dimmed/blurred background while we paint into the surface. */
    public static boolean isPainting(Screen screen) {
        return painting == screen;
    }

    @Override
    public Component title() {
        return title;
    }

    /** Slots: RMB takes half a stack / places one item, exactly like on the flat screen. */
    @Override
    public boolean acceptsSecondaryClick() {
        return true;
    }

    /** The server accepts clicks only for the player's current menu (see class doc). */
    private boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.containerMenu == menu;
    }

    /** Item names in the container: exactly what a hacker wants to see in someone's chest. */
    @Override
    public String spyText() {
        StringBuilder sb = new StringBuilder();
        for (var slot : menu.slots) {
            if (!slot.hasItem()) continue;
            var stack = slot.getItem();
            sb.append(stack.getCount()).append('x').append(stack.getHoverName().getString()).append(", ");
            if (sb.length() > 240) break;
        }
        return sb.toString();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean personal = menu instanceof InventoryMenu;
        if (mc.player == null || mc.player.containerMenu != menu && !personal) {
            KVRSpatial.windows().close(this);
            return;
        }
        // InventoryScreen#containerTick swaps itself for the creative screen in creative mode (setScreen!)
        if (personal && mc.gameMode != null && mc.gameMode.hasInfiniteItems()) {
            KVRSpatial.windows().close(this);
            return;
        }
        screen.tick();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.surface(g, 0, 0, width, height, false);
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int ox = a.kvr$leftPos() - PAD;
        int oy = a.kvr$topPos() - PAD;
        boolean active = active();
        RenderCompat.pushTranslate(g, -ox, -oy);
        painting = screen;
        // vanilla screens assume a white shader colour; a leftover tint turned every item into one colour
        RenderCompat.resetShaderColor();
        try {
            screen.render(g, mx < 0 || !active ? -1 : mx + ox, my < 0 || !active ? -1 : my + oy, partialTick);
        } finally {
            RenderCompat.resetShaderColor();
            painting = null;
            RenderCompat.pop(g);
        }
        if (!active) {
            g.fill(0, 0, width, height, 0xA0000000);
            Component busy = Component.translatable("kvr.screen.inactive");
            int y = height / 2 - 4;
            for (var line : Minecraft.getInstance().font.split(busy, width - 16)) {
                int w = Minecraft.getInstance().font.width(line);
                GlassStyle.text(g, line, (width - w) / 2, y, GlassStyle.MUTED);
                y += 10;
            }
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return forward(x, y, button, false);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return forward(x, y, button, true);
    }

    /** Window pixels → the screen's own coordinates, then into vanilla's input API. */
    private boolean forward(double x, double y, int button, boolean release) {
        if (!active()) return true; // inert inventory while a chest is open: swallow, never desync
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        double sx = x + a.kvr$leftPos() - PAD;
        double sy = y + a.kvr$topPos() - PAD;
        //? if >=1.21.9 {
        /*// VERIFY on 1.21.11: GuiEventListener takes MouseButtonEvent since 1.21.9
        net.minecraft.client.input.MouseButtonEvent e =
            new net.minecraft.client.input.MouseButtonEvent(sx, sy, new net.minecraft.client.input.MouseButtonInfo(button, 0));
        return release ? screen.mouseReleased(e) : screen.mouseClicked(e, false);
        *///?} else {
        return release ? screen.mouseReleased(sx, sy, button) : screen.mouseClicked(sx, sy, button);
        //?}
    }

    @Override
    public void onClosed() {
        super.onClosed();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu) return;
        if (menu instanceof InventoryMenu) {
            // like closing the flat inventory: the 2×2 crafting grid and the carried stack go back to the player.
            // Not while a flat screen is open (re-pin, chat): closeContainer() would also close that screen.
            if (mc.screen == null) mc.player.closeContainer();
        } else {
            mc.player.closeContainer();
        }
    }
}
