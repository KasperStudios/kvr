package com.kasperstudios.kvr.fabric;

import com.kasperstudios.kvr.client.ChassisAssemblyScreen;
import com.kasperstudios.kvr.menu.KVRMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client entrypoint: binds loader-independent KVR screens to their menu types. */
public final class KVRFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(KVRMenus.chassisAssembly(), ChassisAssemblyScreen::new);
    }
}
