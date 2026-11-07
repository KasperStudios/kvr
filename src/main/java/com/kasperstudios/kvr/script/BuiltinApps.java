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
    public static final List<String> SYSTEM_APPS = List.of("hello", "kav", "notes", "calc", "messenger", "player", "ide");
    public static final String SETTINGS_ID = "settings";

    private static final double SPAWN_DISTANCE = 0.9;

    private final List<String> ids;
    private final Map<String, VrApp> cache = new LinkedHashMap<>();
    private String prelude;

    public BuiltinApps(List<String> ids) {
        List<String> all = new ArrayList<>(ids);
        all.add(SETTINGS_ID);
        if (com.kasperstudios.kvr.desktop.client.HostDesktopClient.enabledLocally()) all.add(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID);
        this.ids = List.copyOf(all);
    }

    @Override
    public List<SpatialConfig.DockItem> dockItems() {
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        for (String id : ids) {
            if (id.equals(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID)) {
                boolean on = KVRSpatial.windows().all().stream().anyMatch(w -> w instanceof com.kasperstudios.kvr.desktop.client.HostDesktopWindow);
                out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.translatable("kvr.desktop.title"), on));
                continue;
            }
            if (id.equals("settings")) {
                boolean on = KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.SettingsWindow.class).isPresent();
                out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.translatable("kvr.settings.title"), on));
                continue;
            }
            boolean running = KVRSpatial.windows().all().stream().anyMatch(w -> w.id().equals("app:" + id));
            out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.literal(app(id).map(VrApp::name).orElse(id)), running));
        }
        out.addAll(com.kasperstudios.kvr.apps.client.AppCatalog.dockItems()); // 0025: market, installed packages and dev apps
        return out;
    }

    @Override
    public void launch(String appId) {
        if (com.kasperstudios.kvr.apps.client.AppCatalog.handles(appId)) { // 0025: packages & dev apps
            com.kasperstudios.kvr.apps.client.AppCatalog.launch(appId);
            return;
        }
        if (appId.equals(com.kasperstudios.kvr.desktop.client.HostDesktopClient.DOCK_ID)) {
            com.kasperstudios.kvr.desktop.client.HostDesktopClient.open();
            return;
        }
        if (appId.equals("settings")) {
            var open = KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.SettingsWindow.class);
            if (open.isPresent()) {
                KVRSpatial.windows().restore(open.get());
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
            Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
            KVRSpatial.windows().open(new com.kasperstudios.kvr.spatial.ui.SettingsWindow(new WorldAnchor(SpatialPose.facing(center, view.eye()))));
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
        var open = KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.AppLauncherWindow.class);
        if (open.isPresent()) {
            KVRSpatial.windows().restore(open.get());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
        KVRSpatial.windows().open(new com.kasperstudios.kvr.spatial.ui.AppLauncherWindow(this, new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    @Override
    public void enterVirtualSpace() {
        var open = KVRSpatial.windows().find(com.kasperstudios.kvr.spatial.ui.VirtualMirrorWindow.class);
        if (open.isPresent()) {
            KVRSpatial.windows().restore(open.get());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        // Spawn behind or slightly to the side of the player
        Vec3 side = view.look().cross(new Vec3(0, 1, 0)).normalize();
        Vec3 center = view.eye().add(view.look().scale(0.8)).add(side.scale(0.7));
        KVRSpatial.windows().open(new com.kasperstudios.kvr.spatial.ui.VirtualMirrorWindow(new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    @Override
    public void createSpace() {
        Minecraft mc = Minecraft.getInstance();
        PlayerView view = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 center = view.eye().add(view.look().scale(SPAWN_DISTANCE));
        KVRSpatial.windows().open(new com.kasperstudios.kvr.spatial.ui.WorkspaceWindow(new WorldAnchor(SpatialPose.facing(center, view.eye()))));
    }

    private void soon() {
        KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.os.title"), Component.translatable("kvr.os.soon"),
            GlassStyle.id("gesture/launcher"), GlassStyle.ACCENT, 3000));
    }

    public Optional<VrApp> app(String id) {
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
