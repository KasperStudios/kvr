package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.desktop.HostDesktopServer;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Desktop apps inside KVR (D1). Dock item {@code "desktop"} → checks, in order:
 * <ol>
 *   <li>{@code enable_host_desktop_windows} in kvr-client.toml;</li>
 *   <li>server policy (asked once per connection);</li>
 *   <li>consent for this exact kvr-desktopd binary (else → {@link DesktopConsentScreen});</li>
 *   <li>binary present (else, if consented to a pinned download → verified download);</li>
 * </ol>
 * then starts the daemon in the background, connects {@link DesktopBridge} and opens the
 * {@link DesktopLauncherWindow}. Every toplevel the daemon reports becomes a {@link HostDesktopWindow}.
 *
 * <p>Client init: {@code HostDesktopClient.initClient()} (after {@code KVRNetwork}).
 * On disconnect: {@code HostDesktopClient.reset()} — closes the app windows and stops the daemon.
 */
public final class HostDesktopClient implements DesktopBridge.Listener {

    public static final String DOCK_ID = "desktop";
    private static final HostDesktopClient INSTANCE = new HostDesktopClient();
    @Nullable private static HostDesktopServer.Policy policy;
    private static boolean openWhenAnswered;
    @Nullable private static DesktopBridge bridge;
    private static boolean downloading;

    private HostDesktopClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(HostDesktopServer.POLICY, buf -> {
            HostDesktopServer.Policy p = HostDesktopServer.Policy.read(buf);
            Minecraft.getInstance().execute(() -> {
                policy = p;
                if (openWhenAnswered) {
                    openWhenAnswered = false;
                    open();
                }
            });
        });
    }

    public static boolean enabledLocally() {
        return KvrConfig.client().hostDesktopWindows();
    }

    /** Disconnect: forget the server answer, close app windows, stop the daemon (apps exit with it). */
    public static void reset() {
        policy = null;
        openWhenAnswered = false;
        DesktopBridge b = bridge;
        bridge = null;
        if (b != null) b.close();
        DesktopDaemon.stop();
    }

    /** Dock item. */
    public static void open() {
        if (!enabledLocally()) {
            toast("kvr.desktop.disabled_client");
            return;
        }
        if (policy == null) {
            openWhenAnswered = true;
            KVRNetwork.sendToServer(HostDesktopServer.QUERY, buf -> {});
            return;
        }
        if (!policy.enabled()) {
            toast("kvr.desktop.disabled_server");
            return;
        }
        DesktopDaemon.Info info = DesktopDaemon.inspect();
        Minecraft mc = Minecraft.getInstance();
        if (!info.consented()) {
            mc.setScreen(new DesktopConsentScreen(null, HostDesktopClient::open));
            return;
        }
        if (!info.exists()) {
            if (!info.canDownload()) {
                toast("kvr.desktop.no_binary");
                return;
            }
            if (downloading) return;
            downloading = true;
            toast("kvr.desktop.downloading");
            DesktopDaemon.download(info).whenComplete((path, err) -> mc.execute(() -> {
                downloading = false;
                if (err != null) toastText("kvr.desktop.download_failed", rootMessage(err));
                else open();
            }));
            return;
        }
        if (!ensureBridge()) return;
        KVRSpatial.windows().find(DesktopLauncherWindow.class).ifPresentOrElse(KVRSpatial.windows()::restore,
            () -> KVRSpatial.windows().open(new DesktopLauncherWindow(new WorldAnchor(spawnPose(0.95, 0.0)))));
    }

    /** Daemon running and bridge connected (or connecting). False = error already shown. */
    private static boolean ensureBridge() {
        DesktopBridge b = bridge;
        if (b != null && b.state() != DesktopBridge.State.CLOSED && DesktopDaemon.alive()) return true;
        try {
            Path socket = DesktopDaemon.start();
            DesktopBridge fresh = new DesktopBridge(socket, DesktopDaemon.token(), INSTANCE);
            bridge = fresh;
            fresh.start();
            return true;
        } catch (IOException | RuntimeException e) {
            toastText("kvr.desktop.daemon_failed", e.getMessage());
            return false;
        }
    }

    /** Launcher entry clicked. */
    static void launch(List<String> argv) {
        if (policy != null && desktopWindows() >= policy.maxWindows()) {
            toast("kvr.desktop.limit");
            return;
        }
        if (!ensureBridge()) return;
        DesktopBridge b = bridge;
        if (b != null) b.launch(argv);
    }

    private static long desktopWindows() {
        return KVRSpatial.windows().all().stream().filter(w -> w instanceof HostDesktopWindow).count();
    }

    // --- bridge events (client thread) ---------------------------------------------------------

    @Override
    public void streamOpened(DesktopStream stream) {
        if (!KVRSpatial.ready()) {
            stream.bridge().send(new com.kasperstudios.kvr.desktop.DesktopProtocol.Close(stream.id()));
            return;
        }
        // dialogs (req 0) open a little to the right, so they do not cover their parent
        HostDesktopWindow w = new HostDesktopWindow(stream, new WorldAnchor(spawnPose(1.2, stream.req() == 0 ? 0.35 : 0.0)));
        if (KVRSpatial.openApp(w) != WindowManager.OpenResult.OPENED) {
            stream.bridge().send(new com.kasperstudios.kvr.desktop.DesktopProtocol.Close(stream.id()));
        }
    }

    @Override
    public void streamClosed(DesktopStream stream) {
        KVRSpatial.windows().all().stream()
            .filter(w -> w instanceof HostDesktopWindow h && h.stream() == stream)
            .findFirst().ifPresent(w -> w.requestClose());
    }

    @Override
    public void launchFailed(int req, String message) {
        toastText("kvr.desktop.launch_failed", message);
    }

    @Override
    public void bridgeClosed(String reason) {
        if (bridge != null && bridge.state() == DesktopBridge.State.CLOSED) bridge = null;
        if (KVRSpatial.ready() && enabledLocally()) toastText("kvr.desktop.daemon_failed", reason);
    }

    // --- helpers -------------------------------------------------------------------------------

    private static SpatialPose spawnPose(double distance, double right) {
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 fwd = view.horizontalLook();
        Vec3 r = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 center = view.eye().add(view.look().scale(distance)).add(r.scale(right));
        return SpatialPose.facing(center, view.eye());
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) c = c.getCause();
        return c.getMessage() == null ? c.toString() : c.getMessage();
    }

    private static void toast(String key) {
        KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.desktop.title"), Component.translatable(key),
            GlassStyle.id("gesture/launcher"), GlassStyle.DANGER, 4000));
    }

    private static void toastText(String key, String arg) {
        KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.desktop.title"), Component.translatable(key, arg == null ? "" : arg),
            GlassStyle.id("gesture/launcher"), GlassStyle.DANGER, 6000));
    }
}
