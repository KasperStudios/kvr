package com.kasperstudios.kvr.spatial.vanilla;

import com.kasperstudios.kvr.mixin.client.AbstractContainerScreenAccessor;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
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
 * RAM slots.
 *
 * <p>Vanilla allows one open container menu per player: the player inventory is always available,
 * plus one chest/furnace/etc. Opening another container replaces the menu and the old window
 * closes itself. Hanging many containers at once needs server-side multi-menu support (later block).
 */
public final class SpatialScreenProxy extends SpatialWindow {

    private static final int PAD = 6;
    private static final double SPAWN_DISTANCE = 0.9;
    @Nullable private static Screen painting;

    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final Component title;

    private SpatialScreenProxy(AbstractContainerScreen<?> screen, SpatialPose pose) {
        super("vanilla:" + screen.getMenu().containerId, Kind.VANILLA, size(screen, true), size(screen, false), new WorldAnchor(pose));
        this.screen = screen;
        this.menu = screen.getMenu();
        this.title = screen.getTitle();
    }

    private static int size(AbstractContainerScreen<?> s, boolean w) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
        return (w ? a.kvr$imageWidth() : a.kvr$imageHeight()) + PAD * 2;
    }

    /** {@code KVRClient.onScreenInit} hook: adds the pin button above the container's top-right corner. */
    public static void attachPinButton(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof AbstractContainerScreen<?> acs) || screen instanceof CreativeModeInventoryScreen) return;
        if (!KVRSpatial.ready() || !KVRSpatial.config().glassesWorn().getAsBoolean()) return;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) acs;
        int x = a.kvr$leftPos() + a.kvr$imageWidth() - 20;
        int y = a.kvr$topPos() - 14;
        addWidget.accept(Button.builder(Component.translatable("kvr.screen.pin"), b -> pin(acs))
            .bounds(x, y, 20, 12)
            .tooltip(Tooltip.create(Component.translatable("kvr.screen.pin.tooltip")))
            .build());
    }

    private static void pin(AbstractContainerScreen<?> acs) {
        Minecraft mc = Minecraft.getInstance();
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

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu && !(menu instanceof InventoryMenu)) {
            KVRSpatial.windows().close(this);
            return;
        }
        screen.tick();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.panel(g, 0, 0, width, height, false);
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int ox = a.kvr$leftPos() - PAD;
        int oy = a.kvr$topPos() - PAD;
        g.pose().pushPose();
        g.pose().translate(-ox, -oy, 0);
        painting = screen;
        try {
            screen.render(g, mx < 0 ? -1 : mx + ox, my < 0 ? -1 : my + oy, partialTick);
        } finally {
            painting = null;
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        return screen.mouseClicked(x + a.kvr$leftPos() - PAD, y + a.kvr$topPos() - PAD, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        return screen.mouseReleased(x + a.kvr$leftPos() - PAD, y + a.kvr$topPos() - PAD, button);
    }

    @Override
    public void onClosed() {
        super.onClosed();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu == menu && !(menu instanceof InventoryMenu)) mc.player.closeContainer();
    }
}
