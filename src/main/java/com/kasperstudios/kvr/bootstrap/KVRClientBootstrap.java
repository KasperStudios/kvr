package com.kasperstudios.kvr.bootstrap;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.desktop.client.HostDesktopClient;
import com.kasperstudios.kvr.hack.client.SpyClient;
import com.kasperstudios.kvr.messenger.client.MessengerClient;
import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.SetPowerStatePayload;
import com.kasperstudios.kvr.power.client.PowerClient;
import com.kasperstudios.kvr.script.BuiltinApps;
import com.kasperstudios.kvr.script.chicory.ChicoryJsEngine;
import com.kasperstudios.kvr.share.client.PublicWindowsClient;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.SpatialConfig;
import com.kasperstudios.kvr.vfs.client.VfsClient;
import net.minecraft.client.Minecraft;

/**
 * The whole client side of KVR OS in the one correct order — the missing piece of the first live test
 * (nothing of the spatial UI starts unless this runs). Call it ONCE from the client entrypoint:
 *
 * <pre>{@code
 * // Fabric:   KVRFabricClient.onInitializeClient()  →  KVRClientBootstrap.init(new FabricKVRClientProvider());
 * // NeoForge: mod constructor, Dist.CLIENT          →  KVRClientBootstrap.init(new NeoForgeKVRClientProvider(modBus));
 * }</pre>
 *
 * Needs, before it: {@code KVRNetwork} ready on the client and {@code GlassesPower.install(...)} done
 * (common init). What it starts:
 * <ol>
 *   <li>{@link KVRClient} provider (key mappings, tick, world render, screens, HUD);</li>
 *   <li>{@link ChicoryJsEngine} — QuickJS wasm parsed + self-tested in the background;</li>
 *   <li>the power button sender (key O, Power window);</li>
 *   <li>{@link KVRSpatial} — windows, dock, Alt pointer (key Left Alt), gestures, power key (O), renderer;</li>
 *   <li>network clients: HackGlasses ghost view, public windows, VFS, messenger, desktop apps.</li>
 * </ol>
 */
public final class KVRClientBootstrap {

    private static boolean done;

    private KVRClientBootstrap() {}

    public static synchronized void init(KVRClientProvider provider) {
        if (done) return;
        done = true;
        KVRClient.install(provider);
        com.kasperstudios.kvr.glasses.client.WornGlassesModels.register(provider); // 3D on the head, flat icon elsewhere (0020)
        ChicoryJsEngine.install();
        PowerClient.installSender(state -> KVRNetwork.sendToServer(SetPowerStatePayload.CHANNEL, new SetPowerStatePayload(state)::write));
        KVRSpatial.initClient(new SpatialConfig(
            KVRClientBootstrap::ramWindows,
            KVRClientBootstrap::displayOn,
            () -> GlassesPower.percent(PowerClient.worn()),
            new BuiltinApps(BuiltinApps.SYSTEM_APPS)));
        // after KVRSpatial: its tick (Alt toggle, power key) runs first, the hints read the result
        if (KVRSpatial.ready()) provider.onClientTickStart(com.kasperstudios.kvr.power.client.PowerHints::tick);
        SpyClient.initClient();
        PublicWindowsClient.initClient();
        VfsClient.initClient();
        MessengerClient.initClient();
        HostDesktopClient.initClient();
        KVR.logger().info("KVR client ready: spatial UI {}, keys: Left Alt = pointer, O = power (hold 1 s = Power Off)",
            KVRSpatial.ready() ? "on" : "OFF (no render backend for this Minecraft version yet)");
    }

    /** RAM tier of the worn glasses → simultaneous app windows (SPEC 2.5: 1/2/4/8/16). */
    static int ramWindows() {
        return GlassesPower.build(PowerClient.worn()).map(b -> ramWindowsForTier(b.ram())).orElse(1);
    }

    public static int ramWindowsForTier(int tier) {
        return 1 << Math.max(0, Math.min(4, tier - 1));
    }

    /** Display lit: glasses on the head, ON and booted. HUD_OFF keeps the OS running but hides every window. */
    static boolean displayOn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && GlassesPower.displayOn(PowerClient.worn(), mc.level.getGameTime());
    }
}
