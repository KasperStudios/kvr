package com.kasperstudios.kvr.platform.fabric;

import com.kasperstudios.kvr.platform.KVRRegistrar;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;

/**
 * Target of Architectury's platform transformation for {@link KVRRegistryProvider#get()}.
 */
public final class KVRRegistryProviderImpl {

    private KVRRegistryProviderImpl() {
    }

    public static KVRRegistrar get() {
        return KVRRegistryProvider.installed();
    }
}
