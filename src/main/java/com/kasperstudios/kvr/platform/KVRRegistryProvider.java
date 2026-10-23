package com.kasperstudios.kvr.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Access point to the loader-specific {@link KVRRegistrar}.
 *
 * <p>Architectury rewrites {@link #get()} into {@code KVRRegistryProviderImpl.get()} in the loader
 * package, so the platform module has to provide that class - see the fabric/neoforge branches.
 */
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
        return installed();
    }

    /** Backing accessor used by {@link #get()} and by the platform-side implementation. */
    public static KVRRegistrar installed() {
        KVRRegistrar registrar = installed;
        if (registrar == null) {
            throw new IllegalStateException(
                    "KVR registrar is not installed - the loader entrypoint must call KVRRegistryProvider.install()");
        }
        return registrar;
    }
}
