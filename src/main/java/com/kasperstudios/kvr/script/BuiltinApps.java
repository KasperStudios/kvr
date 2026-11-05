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
 * <pre>{@code new SpatialConfig(ramWindows, glassesWorn, batteryPercent, new BuiltinApps(List.of("hello", "kav")))}</pre>
 */
public final class BuiltinApps implements SpatialConfig.AppHost {

    private static final double SPAWN_DISTANCE = 0.9;

    private final List<String> ids;
    private final Map<String, VrApp> cache = new LinkedHashMap<>();
    private String prelude;

    public BuiltinApps(List<String> ids) {
        this.ids = List.copyOf(ids);
    }

    @Override
    public List<SpatialConfig.DockItem> dockItems() {
        List<SpatialConfig.DockItem> out = new ArrayList<>();
        for (String id : ids) {
            boolean running = KVRSpatial.windows().all().stream().anyMatch(w -> w.id().equals("app:" + id));
            out.add(new SpatialConfig.DockItem(id, GlassStyle.id("gesture/launcher"), Component.literal(app(id).map(VrApp::name).orElse(id)), running));
        }
        return out;
    }

    @Override
    public void launch(String appId) {
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
        soon();
    }

    @Override
    public void enterVirtualSpace() {
        soon();
    }

    @Override
    public void createSpace() {
        soon();
    }

    private void soon() {
        KVRSpatial.toasts().push(new ToastPanel.Toast(Component.translatable("kvr.os.title"), Component.translatable("kvr.os.soon"),
            GlassStyle.id("gesture/launcher"), GlassStyle.ACCENT, 3000));
    }

    private Optional<VrApp> app(String id) {
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
