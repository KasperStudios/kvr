package com.kasperstudios.kvr.fabric.client;

import com.kasperstudios.kvr.config.KvrSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * "Config" button of KVR in ModMenu → {@link KvrSettingsScreen} (window defaults, desktop apps switch,
 * kvr-desktopd consent). Needs {@code modCompileOnly("com.terraformersmc:modmenu:<version for 1.21.1>")}
 * and in fabric.mod.json: {@code "entrypoints": { "modmenu": ["com.kasperstudios.kvr.fabric.client.KvrModMenu"] }}.
 * Without ModMenu installed the class is simply never loaded.
 */
public final class KvrModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return KvrSettingsScreen::new;
    }
}
