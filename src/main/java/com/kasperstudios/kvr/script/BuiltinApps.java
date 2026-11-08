package com.kasperstudios.kvr.script;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.client.PowerClient;
import com.kasperstudios.kvr.script.ui.ScriptWindow;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;

/**
 * {@link SpatialConfig.AppHost} for 5a: apps bundled in the mod jar under
 * {@code assets/kvr/apps/<id>/} (config.json + main.js), shown in the dock and launched as
 * {@link ScriptWindow}s in front of the player. 5b/5d replace the source with the glasses' VFS.
 *
 * <pre>{@code new SpatialConfig(ramWindows, glassesWorn, batteryPercent, new BuiltinApps(BuiltinApps.SYSTEM_APPS))}</pre>
 */
public final class BuiltinApps implements SpatialConfig.AppHost {

    /** The apps shipped in the jar; ChicoryJsEngineTest boots exactly these on the real prelude. */
    public static final List<String> SYSTEM_APPS = List.of("hello", "kav", "notes", "calc", "messenger");

    /**
     * 0027, Creator: clean glasses ship with exactly THREE programs — App Market, Messenger (comms) and Settings.
     * Market and Settings are native windows, so only the JS one is listed here. Everything else in
     * {@link #SYSTEM_APPS} is installed from App Market (built-in catalog, {@code assets/kvr/apps/index.json}).
     */
    public static final List<String> PREINSTALLED = List.of("messenger");

    private static final double SPAWN_DISTANCE = 0.9;

    private final List<String> ids;
    private static final Map<String, VrApp> cache = new LinkedHashMap<>();
    private String prelude;

    public BuiltinApps(List<String> ids) {
        List<String> all = new ArrayList<>(ids);
        if (com.kasperstudios.kvr.desktop.client.HostDesktopClient.enabledLocally()) all.add(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID);
        this.ids = List.copyOf(all);
    }

    @Override
    public List<SpatialConfig.DockItem> dockItems() {
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        out.add(com.kasperstudios.kvr.apps.client.AppCatalog.marketItem()); // 0027: 1) App Market
        for (String id : ids) {                                             //       2) Messenger (+ PC apps if enabled)
            if (id.equals(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID)) {
                boolean on = KVRSpatial.windows().all().stream().anyMatch(w -> w instanceof com.kasperstudios.kvr.desktop.client.HostDesktopWindow);
                out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.translatable("kvr.desktop.title"), on));
                continue;
            }
            boolean running = KVRSpatial.windows().all().stream().anyMatch(w -> w.id().equals("app:" + id));
            out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.literal(app(id).map(VrApp::name).orElse(id)), running));
        }
        out.add(new SpatialConfig.DockItem(com.kasperstudios.kvr.spatial.ui.SettingsWindow.DOCK_ID, GlassStyle.id("gesture/launcher"), //   3) Settings
            Component.translatable("kvr.settings.app"), KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.SettingsWindow.class).isPresent()));
        out.addAll(com.kasperstudios.kvr.apps.client.AppCatalog.packageItems()); // pinned .vrapp / dev apps (0028)
        out.addAll(com.kasperstudios.kvr.desktop.client.DesktopDock.items());     // pinned PC apps (0028)
        return out;
    }

    @Override
    public void launch(String appId) {
        if (appId.equals(com.kasperstudios.kvr.spatial.ui.SettingsWindow.DOCK_ID)) { // 0027
            com.kasperstudios.kvr.spatial.ui.SettingsWindow.open();
            return;
        }
        if (com.kasperstudios.kvr.desktop.client.DesktopDock.handles(appId)) { // 0028: pinned PC app
            com.kasperstudios.kvr.desktop.client.DesktopDock.launch(appId);
            return;
        }
        if (com.kasperstudios.kvr.apps.client.AppCatalog.handles(appId)) { // 0025
            com.kasperstudios.kvr.apps.client.AppCatalog.launch(appId);
            return;
        }
        if (appId.equals(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID)) {
            com.kasperstudios.kvr.desktop.client.HostDesktopClient.open();
            return;
        }
        // 0031: only the preinstalled JS app(s) start from the jar. Anything else (notes, calc, kav, hello, ide, player…)
        // must be INSTALLED in the worn glasses — then AppCatalog.handles() above already launched it as a package.
        // Before 0031 every bundled id started from here, so the launcher could run apps that were never installed.
        if (!ids.contains(appId)) {
            KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.apps.market"),
                Component.translatable("kvr.launcher.not_installed", app(appId).map(VrApp::name).orElse(appId)),
                GlassStyle.id("gesture/launcher"), GlassStyle.ACCENT, 3500));
            com.kasperstudios.kvr.apps.client.AppCatalog.openMarket();
            return;
        }
        var open = KVRSpatial.windows().all().stream().filter(w -> w.id().equals("app:" + appId)).findFirst();
        if (open.isPresent()) {
            KVRSpatial.windows().restore(open.get());
            return;
        }
        Optional<VrApp> app = app(appId);
        if (app.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
        int cpu = GlassesPower.build(PowerClient.worn()).map(PowerBuild::cpu).orElse(1);
        KVRSpatial.openApp(new ScriptWindow(app.get(), prelude(), cpu, new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    @Override
    public void openLauncher() {
        KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.AppLauncherWindow.class).ifPresentOrElse(
            KVRSpatial.windows()::restore,
            () -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                com.kasperstudios.kvr.spatial.PlayerView view = com.kasperstudios.kvr.spatial.PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
                net.minecraft.world.phys.Vec3 center = view.eye().add(view.look().scale(0.95));
                KVRSpatial.windows().open(new com.kasperstudios.kvr.spatial.ui.AppLauncherWindow(this, new com.kasperstudios.kvr.spatial.anchor.WorldAnchor(com.kasperstudios.kvr.spatial.SpatialPose.facing(center, view.eye()))));
            }
        );
    }

    /** 0033: gesture 4 — Virtual Space slots (picker or cycle, Settings → Space). */
    @Override
    public void enterVirtualSpace() {
        com.kasperstudios.kvr.spatial.space.VirtualSpaces.onGesture();
    }

    @Override
    public void createSpace() {
        soon();
    }

    private void soon() {
        KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.os.title"), Component.translatable("kvr.os.soon"),
            GlassStyle.id("gesture/launcher"), GlassStyle.ACCENT, 3000));
    }

    public static Optional<VrApp> app(String id) {
        if (cache.containsKey(id)) return Optional.of(cache.get(id));
        try {
            VrApp app = VrApp.parse(read("apps/" + id + "/config.json"), read("apps/" + id + "/main.js"));
            cache.put(id, app);
            return Optional.of(app);
        } catch (IOException | RuntimeException e) {
            KVR.logger().warn("KVR builtin app {} failed to load: {}", id, e.toString());
            return Optional.empty();
        }
    }

    private String prelude() {
        if (prelude == null) {
            try {
                prelude = read("kvr_os/prelude.js");
            } catch (IOException e) {
                throw new IllegalStateException("KVR OS prelude missing: assets/kvr/kvr_os/prelude.js", e);
            }
        }
        return prelude;
    }

    private static String read(String path) throws IOException {
        ResourceLocation loc = GlassStyle.id(path);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) throw new IOException("missing assets/kvr/" + path);
        try (Reader r = res.get().openAsReader()) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString();
        }
    }
}
