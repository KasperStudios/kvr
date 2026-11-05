package com.kasperstudios.kvr.spatial;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import com.kasperstudios.kvr.spatial.input.SpatialCursor;
import com.kasperstudios.kvr.spatial.input.SpatialInput;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.SpatialRenderer;
import com.kasperstudios.kvr.spatial.ui.DockBar;
import com.kasperstudios.kvr.spatial.ui.GestureWheel;
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

    private KVRSpatial() {}

    public static void initClient(SpatialConfig cfg) {
        if (config != null) return; // idempotent, like KVR.init()
        config = cfg;
        KVRClientProvider client = KVRClient.get();

        cursor = new SpatialCursor();
        client.registerKeyMapping(cursor.toggleKey());

        windows = new WindowManager(cfg.ramWindows());
        toasts = new ToastPanel();
        dock = new DockBar(cfg, windows);
        input = new SpatialInput(windows, cursor, cfg.glassesWorn(), KVRSpatial::runGesture);

        client.onClientTickStart(KVRSpatial::tickStart);
        client.onWorldRender(SpatialRenderer::render);
        client.onScreenInit(SpatialScreenProxy::attachPinButton);
        KVR.logger().info("KVR spatial UI ready");
    }

    private static void tickStart() {
        boolean worn = config.glassesWorn().getAsBoolean();
        boolean shown = windows.find(DockBar.class).isPresent();
        if (worn && !shown) {
            windows.open(dock);
            windows.open(toasts);
        } else if (!worn && shown) {
            windows.closeAll(); // glasses off: every surface disappears with them
        }
        input.tickStart();
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
