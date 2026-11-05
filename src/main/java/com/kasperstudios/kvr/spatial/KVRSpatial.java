package com.kasperstudios.kvr.spatial;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import com.kasperstudios.kvr.spatial.input.SpatialCursor;
import com.kasperstudios.kvr.spatial.input.SpatialInput;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.render.SpatialRenderer;
import com.kasperstudios.kvr.spatial.ui.DockBar;
import com.kasperstudios.kvr.spatial.ui.GestureWheel;
import com.kasperstudios.kvr.spatial.ui.PowerWindow;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.spatial.vanilla.SpatialScreenProxy;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import net.minecraft.network.chat.Component;

/**
 * Client bootstrap of the spatial UI. Called once from each loader's client entrypoint after
 * {@code KVRClient.install(...)}; everything below talks to the loader only through that facade.
 *
 * <pre>{@code
 * KVRClient.install(new FabricKVRClientProvider());
 * KVRSpatial.initClient(new SpatialConfig(
 *     () -> HardwareTable.ramWindows(worn()),   // Block 2 hardware code
 *     () -> !worn().isEmpty() && powered(),
 *     () -> batteryPercent(worn()),
 *     appHost));                                // QuickJS app runtime (Block 5)
 * }</pre>
 */
public final class KVRSpatial {

    private static SpatialConfig config;
    private static WindowManager windows;
    private static SpatialCursor cursor;
    private static SpatialInput input;
    private static ToastPanel toasts;
    private static DockBar dock;
    private static final com.kasperstudios.kvr.power.client.PowerKey POWER_KEY = new com.kasperstudios.kvr.power.client.PowerKey();

    private KVRSpatial() {}

    public static void initClient(SpatialConfig cfg) {
        if (config != null) return; // idempotent, like KVR.init()
        if (!RenderCompat.SUPPORTED) {
            KVR.logger().warn("KVR spatial UI is not available on this Minecraft version yet (render backend pending)");
            return;
        }
        config = cfg;
        KVRClientProvider client = KVRClient.get();

        cursor = new SpatialCursor();
        client.registerKeyMapping(cursor.toggleKey());
        client.registerKeyMapping(POWER_KEY.key());

        windows = new WindowManager(cfg.ramWindows());
        toasts = new ToastPanel();
        dock = new DockBar(cfg, windows);
        input = new SpatialInput(windows, cursor, cfg.glassesWorn(), KVRSpatial::runGesture);

        client.onClientTickStart(KVRSpatial::tickStart);
        client.onWorldRender(SpatialRenderer::render);
        client.onScreenInit(SpatialScreenProxy::attachPinButton);
        client.onHudRender(com.kasperstudios.kvr.spatial.render.AlarmOverlay::render);
        KVR.logger().info("KVR spatial UI ready");
    }

    /** Dock status click, or automatically on overload. */
    public static void openPowerWindow() {
        if (windows.find(PowerWindow.class).isEmpty()) windows.open(new PowerWindow());
        windows.find(PowerWindow.class).ifPresent(windows::restore);
    }

    private static void tickStart() {
        if (net.minecraft.client.Minecraft.getInstance().player != null) POWER_KEY.tick();
        com.kasperstudios.kvr.script.ClientInfo.tick(); // snapshot for kvr.player / kvr.players (script threads)
        boolean display = config.glassesWorn().getAsBoolean();
        boolean os = display || osRunning();
        boolean shown = windows.find(DockBar.class).isPresent();
        if (os && !shown) {
            windows.open(dock);
            windows.open(toasts);
        } else if (!os && shown) {
            windows.closeAll(); // glasses off or powered down: every surface and app ends
        }
        // HUD_OFF (SPEC 2.7): display dark, OS alive — apps keep ticking, windows are just not drawn
        windows.setHidden(os && !display);
        // Note 7: the save button must be in front of the player at once (vignette + power key cover HUD_OFF)
        if (shown && display && com.kasperstudios.kvr.power.client.PowerClient.overloading() && windows.find(PowerWindow.class).isEmpty()) {
            openPowerWindow();
            cursor.setActive(true);
        }
        input.tickStart();
    }

    /** OS running = worn glasses ON or HUD_OFF and booted (display may be dark). */
    private static boolean osRunning() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null && com.kasperstudios.kvr.power.GlassesPower.active(com.kasperstudios.kvr.power.client.PowerClient.worn(), mc.level.getGameTime());
    }

    private static void runGesture(GestureWheel.Gesture gesture) {
        switch (gesture) {
            case LAUNCHER -> config.apps().openLauncher();
            case MINIMIZE_ALL -> windows.minimizeAll();
            case MINIMIZE_FOCUSED -> windows.minimizeFocused();
            case VIRTUAL_SPACE -> config.apps().enterVirtualSpace();
            case NEW_SPACE -> config.apps().createSpace();
        }
    }

    /** Open an app window, with the RAM toast when the tier limit is reached. */
    public static WindowManager.OpenResult openApp(SpatialWindow app) {
        WindowManager.OpenResult r = windows.open(app);
        if (r == WindowManager.OpenResult.RAM_FULL) {
            toasts.push(new ToastPanel.Toast(
                Component.translatable("kvr.window.ram_full.title"),
                Component.translatable("kvr.window.ram_full", windows.appsOpen(), windows.ramLimit()),
                GlassStyle.id("gesture/minimize_all"),
                GlassStyle.DANGER,
                4000));
        }
        return r;
    }

    public static boolean ready() { return config != null; }
    public static SpatialConfig config() { return config; }
    public static WindowManager windows() { return windows; }
    public static SpatialCursor cursor() { return cursor; }
    public static SpatialInput input() { return input; }
    public static ToastPanel toasts() { return toasts; }
}
