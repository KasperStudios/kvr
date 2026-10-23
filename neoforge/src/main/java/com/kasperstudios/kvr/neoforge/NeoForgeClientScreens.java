package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.client.ChassisAssemblyScreen;
import com.kasperstudios.kvr.menu.KVRMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the loader-independent KVR screens on the client.
 *
 * <p>Kept in its own class so the client event type is only touched when a client actually boots.
 */
final class NeoForgeClientScreens {

    private NeoForgeClientScreens() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent event) ->
                event.register(KVRMenus.chassisAssembly(), ChassisAssemblyScreen::new));
    }
}
