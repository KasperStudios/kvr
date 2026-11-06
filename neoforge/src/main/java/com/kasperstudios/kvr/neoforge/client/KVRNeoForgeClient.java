package com.kasperstudios.kvr.neoforge.client;

import com.kasperstudios.kvr.bootstrap.KVRClientBootstrap;
import com.kasperstudios.kvr.config.KvrSettingsScreen;
import com.kasperstudios.kvr.desktop.client.HostDesktopClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client wiring, called from the mod constructor on the client only:
 * <pre>{@code
 * public KVRNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
 *     ... your common init → KVRCommonBootstrap.init();
 *     if (dist.isClient()) KVRNeoForgeClient.init(modBus, container);
 * }
 * }</pre>
 * Must run in the constructor (not in FMLClientSetupEvent): the provider hands our key mappings
 * (Left Alt, O) to {@code RegisterKeyMappingsEvent}, which fires before client setup.
 */
public final class KVRNeoForgeClient {

    private KVRNeoForgeClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        KVRClientBootstrap.init(new NeoForgeKVRClientProvider(modBus));
        // "Config" button in the mod list → KVR settings (window defaults, desktop apps, kvr-desktopd consent)
        container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new KvrSettingsScreen(parent));
        // leaving a server: close desktop apps and stop kvr-desktopd
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> HostDesktopClient.reset());
    }
}
