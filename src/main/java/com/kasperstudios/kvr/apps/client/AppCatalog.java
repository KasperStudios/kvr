package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.AppPermissions;
import com.kasperstudios.kvr.apps.VrAppException;
import com.kasperstudios.kvr.apps.VrAppManifest;
import com.kasperstudios.kvr.apps.VrAppPackage;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.client.PowerClient;
import com.kasperstudios.kvr.script.VrApp;
import com.kasperstudios.kvr.script.ui.ScriptWindow;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.kasperstudios.kvr.spatial.window.WindowManager;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the OS needs from the App Manager, in one place: dock entries for the App Market, installed
 * packages and developer apps; launching them (hardware requirements, permissions, cache/download);
 * hot reload of developer apps; refreshing the installed list when the OS boots.
 *
 * <p>{@code BuiltinApps} delegates to this class for every dock id it does not own ({@link #handles}).
 * Client tick: {@link #tick()} (registered by {@code KVRClientBootstrap}).
 */
public final class AppCatalog {

    public static final String MARKET_ID = "market";
    public static final String DEV_PREFIX = "dev:";
    private static final int DOCK_MAX_INSTALLED = 8;
    private static final int DOCK_MAX_DEV = 4;
    private static final double SPAWN_DISTANCE = 0.9;
    private static final int RELOAD_CHECK_TICKS = 10;

    /** Running dev app id → file stamp it was started with. */
    private static final Map<String, Long> DEV_RUNNING = new HashMap<>();
    private static boolean osWasUp;
    private static boolean inWorld;
    private static int ticks;
    private static String prelude;

    private AppCatalog() {}

    // --- dock --------------------------------------------------------------------------------------

    /** App Market first, then installed packages, then developer folders. Called every frame: cheap. */
    public static List<SpatialConfig.DockItem> dockItems() {
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        out.add(marketItem());
        out.addAll(packageItems());
        return out;
    }

    /** 0027: the App Market dock entry (first of the three preinstalled programs). */
    public static SpatialConfig.DockItem marketItem() {
        return new SpatialConfig.DockItem(MARKET_ID, GlassStyle.id("gesture/launcher"), Component.translatable("kvr.apps.market"),
            KVRSpatial.windows().find(AppMarketWindow.class).isPresent());
    }

    /** Installed packages (incl. built-in catalog apps once installed), then developer folders. */
    public static List<SpatialConfig.DockItem> packageItems() {
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        int n = 0;
        for (AppsClient.Installed i : AppsClient.installed()) {
            if (n++ >= DOCK_MAX_INSTALLED) break;
            String id = i.manifest().id();
            out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.literal(i.manifest().name()), running(id)));
        }
        if (DevApps.enabled()) {
            n = 0;
            for (DevApps.Folder f : DevApps.folders()) {
                if (n++ >= DOCK_MAX_DEV) break;
                String id = f.manifest().id();
                out.add(new SpatialConfig.DockItem(DEV_PREFIX + id, GlassStyle.id("gesture/launcher"), Component.literal("DEV " + f.manifest().name()), running(id)));
            }
        }
        return out;
    }

    /** Dock ids this class launches (everything except the OS apps bundled in the jar). */
    public static boolean handles(String dockId) {
        return dockId.equals(MARKET_ID) || dockId.startsWith(DEV_PREFIX) || AppsClient.installed(dockId).isPresent();
    }

    public static void launch(String dockId) {
        if (dockId.equals(MARKET_ID)) {
            openMarket();
        } else if (dockId.startsWith(DEV_PREFIX)) {
            launchDev(dockId.substring(DEV_PREFIX.length()));
        } else {
            AppsClient.installed(dockId).ifPresent(AppCatalog::launchInstalled);
        }
    }

    private static boolean running(String appId) {
        return window(appId).isPresent();
    }

    private static Optional<SpatialWindow> window(String appId) {
        return KVRSpatial.windows().all().stream().filter(w -> w.id().equals("app:" + appId)).findFirst();
    }

    // --- App Market --------------------------------------------------------------------------------

    public static void openMarket() {
        WindowManager wm = KVRSpatial.windows();
        Optional<AppMarketWindow> open = wm.find(AppMarketWindow.class);
        if (open.isPresent()) {
            wm.restore(open.get());
            return;
        }
        wm.open(new AppMarketWindow(new WorldAnchor(spawnPose())));
        AppsClient.refresh();
        if (StoreClient.state() == StoreClient.State.IDLE || StoreClient.state() == StoreClient.State.ERROR) StoreClient.refresh();
        if (DevApps.enabled()) DevApps.rescan();
    }

    // --- launching ---------------------------------------------------------------------------------

    public static void launchInstalled(AppsClient.Installed app) {
        String id = app.manifest().id();
        if (restoreIfOpen(id) || !hardwareOk(app.manifest())) return;
        AppsClient.fetch(app, bytes -> {
            try {
                VrAppPackage pkg = VrAppPackage.read(bytes);
                PackageIcons.register(id, pkg.icon());
                open(pkg, null);
            } catch (VrAppException e) {
                fail(app.manifest().name(), e.getMessage());
            }
        }, f -> fail(app.manifest().name(), Component.translatable(f.langKey()).getString() + " " + f.message()));
    }

    public static void launchDev(String appId) {
        if (restoreIfOpen(appId)) return;
        try {
            VrAppPackage pkg = DevApps.load(appId);
            if (!hardwareOk(pkg.manifest())) return;
            PackageIcons.register(DEV_PREFIX + appId, pkg.icon());
            if (open(pkg, null)) DEV_RUNNING.put(appId, DevApps.stamp(appId));
        } catch (VrAppException e) {
            fail(appId, e.getMessage());
        }
    }

    private static boolean restoreIfOpen(String appId) {
        Optional<SpatialWindow> w = window(appId);
        w.ifPresent(KVRSpatial.windows()::restore);
        return w.isPresent();
    }

    /** CONCEPT: heavy apps (browser) need T3+ hardware; checked against the worn build. */
    private static boolean hardwareOk(VrAppManifest m) {
        if (!m.requires().any()) return true;
        Optional<PowerBuild> b = GlassesPower.build(PowerClient.worn());
        Optional<String> unmet = b.isEmpty() ? Optional.of("glasses") : m.requires().unmet(b.get().cpu(), b.get().gpu(), b.get().ram(), b.get().storage());
        if (unmet.isEmpty()) return true;
        toast(Component.literal(m.name()), Component.translatable("kvr.apps.requires", unmet.get()), GlassStyle.DANGER);
        return false;
    }

    /** @param anchor reuse (hot reload keeps the window where it was) or null = in front of the player */
    private static boolean open(VrAppPackage pkg, SpatialAnchor anchor) throws VrAppException {
        VrApp app = pkg.toVrApp();
        AppPermissions.grant(app.id(), pkg.manifest().permissions());
        int cpu = GlassesPower.build(PowerClient.worn()).map(PowerBuild::cpu).orElse(1);
        SpatialAnchor a = anchor != null ? anchor : new WorldAnchor(spawnPose());
        return KVRSpatial.openApp(new ScriptWindow(app, prelude(), cpu, a)) == WindowManager.OpenResult.OPENED;
    }

    private static SpatialPose spawnPose() {
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
        return SpatialPose.facing(center, view.eye());
    }

    // --- tick: list refresh, hot reload ------------------------------------------------------------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (inWorld) {
                AppsClient.reset();
                DEV_RUNNING.clear();
                osWasUp = false;
            }
            inWorld = false;
            return;
        }
        inWorld = true;
        AppsClient.tick();
        DevApps.tick();
        if (!KVRSpatial.ready()) return;
        boolean up = KVRSpatial.config().glassesWorn().getAsBoolean();
        if (up && !osWasUp) AppsClient.refresh(f -> registerCachedIcons()); // OS booted: what is installed on THESE glasses
        if (!up && osWasUp && PowerClient.worn().isEmpty()) AppsClient.reset(); // glasses taken off: their apps go with them
        osWasUp = up;
        if (++ticks % RELOAD_CHECK_TICKS == 0) hotReload();
    }

    private static void registerCachedIcons() {
        for (AppsClient.Installed i : AppsClient.installed()) {
            if (i.manifest().icon().isEmpty() || PackageIcons.has(i.manifest().id())) continue;
            AppCache.get(i.sha256()).ifPresent(bytes -> {
                try {
                    PackageIcons.register(i.manifest().id(), VrAppPackage.read(bytes).icon());
                } catch (VrAppException ignored) {
                    // broken cache entries are deleted by AppCache on the next read; no icon meanwhile
                }
            });
        }
    }

    private static void hotReload() {
        for (Map.Entry<String, Long> e : List.copyOf(DEV_RUNNING.entrySet())) {
            String id = e.getKey();
            Optional<SpatialWindow> w = window(id);
            if (w.isEmpty()) {
                DEV_RUNNING.remove(id);
                continue;
            }
            long stamp = DevApps.stamp(id);
            if (stamp <= 0 || stamp == e.getValue()) continue;
            DEV_RUNNING.put(id, stamp); // a broken save is reported once, not every half second
            try {
                VrAppPackage pkg = DevApps.load(id);
                SpatialAnchor anchor = w.get().anchor();
                KVRSpatial.windows().close(w.get());
                PackageIcons.register(DEV_PREFIX + id, pkg.icon());
                if (open(pkg, anchor)) toast(Component.literal(pkg.manifest().name()), Component.translatable("kvr.apps.reloaded"), GlassStyle.OK);
            } catch (VrAppException ex) {
                fail(id, ex.getMessage()); // the old version keeps running
            }
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    static void toast(Component title, Component text, int color) {
        KVRSpatial.toasts().push(new ToastPanel.Toast(title, text, GlassStyle.id("gesture/launcher"), color, 4000));
    }

    private static void fail(String what, String message) {
        KVR.logger().warn("KVR apps: {} failed to start: {}", what, message);
        toast(Component.literal(what), Component.literal(message.length() > 120 ? message.substring(0, 120) + "…" : message), GlassStyle.DANGER);
    }

    private static String prelude() {
        if (prelude == null) {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(GlassStyle.id("kvr_os/prelude.js"));
            if (res.isEmpty()) throw new IllegalStateException("KVR OS prelude missing: assets/kvr/kvr_os/prelude.js");
            try (Reader r = res.get().openAsReader()) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                prelude = sb.toString();
            } catch (IOException e) {
                throw new IllegalStateException("KVR OS prelude unreadable", e);
            }
        }
        return prelude;
    }
}
