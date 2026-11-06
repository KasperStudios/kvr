package com.kasperstudios.kvr.fabric.client;

import com.kasperstudios.kvr.bootstrap.KVRClientBootstrap;
import com.kasperstudios.kvr.desktop.client.HostDesktopClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Fabric client entrypoint. fabric.mod.json:
 * <pre>
 * "entrypoints": {
 *   "main":    ["com.kasperstudios.kvr.fabric.KVRFabric"],                 // your common init → KVRCommonBootstrap.init()
 *   "client":  ["com.kasperstudios.kvr.fabric.client.KVRFabricClient"],
 *   "modmenu": ["com.kasperstudios.kvr.fabric.client.KvrModMenu"]
 * }
 * </pre>
 * If you already have a {@code KVRFabricClient}, keep your own lines (client networking, block colour /
 * render layers for the dock plate) and add the two calls marked below.
 */
public final class KVRFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ← your client-side KVRNetwork setup goes BEFORE this line, if it has one

        // 1) the whole KVR OS client: Chicory, spatial UI, keys Alt + O, renderer, network clients
        net.minecraft.client.gui.screens.MenuScreens.register(com.kasperstudios.kvr.menu.KVRMenus.chassisAssembly(), com.kasperstudios.kvr.client.ChassisAssemblyScreen::new);
        KVRClientBootstrap.init(new FabricKVRClientProvider());

        // 2) leaving a server: close desktop apps and stop kvr-desktopd
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(HostDesktopClient::reset));

        // dock plate glasses + hologram are translucent (0004):
        // net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(KVRContent.DOCK_PLATE.get(),
        //     net.minecraft.client.renderer.RenderType.translucent());
    }
}
