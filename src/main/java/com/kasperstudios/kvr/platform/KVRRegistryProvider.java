package com.kasperstudios.kvr.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/** Access point to the loader-specific {@link KVRRegistrar}. */
public final class KVRRegistryProvider {

    private static KVRRegistrar installed;

    private KVRRegistryProvider() {
    }

    public static void install(KVRRegistrar registrar) {
        if (registrar == null) {
            throw new IllegalArgumentException("registrar must not be null");
        }
        installed = registrar;
    }

    /** Test hook. */
    public static void reset() {
        installed = null;
    }

    @ExpectPlatform
    public static KVRRegistrar get() {
        KVRRegistrar registrar = installed;
        if (registrar == null) {
            throw new IllegalStateException(
                    "KVR registrar is not installed - the loader entrypoint must call KVRRegistryProvider.install()");
        }
        return registrar;
    }
}
