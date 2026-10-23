package com.kasperstudios.kvr.platform.neoforge;

import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;

/**
 * Target of Architectury's platform transformation for {@link KVRPlatformProvider#get()}.
 */
public final class KVRPlatformProviderImpl {

    private KVRPlatformProviderImpl() {
    }

    public static KVRPlatform get() {
        return KVRPlatformProvider.installed();
    }
}
