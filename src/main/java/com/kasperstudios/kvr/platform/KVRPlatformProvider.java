package com.kasperstudios.kvr.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Access point to the loader-specific {@link KVRPlatform}.
 *
 * <p>Loader entrypoints install their implementation during mod construction, so the core can be
 * unit tested against a fake platform, and a missing installation fails loudly instead of
 * silently degrading.
 *
 * <p>Architectury rewrites {@link #get()} into a call to {@code KVRPlatformProviderImpl.get()} placed
 * in the loader package ({@code ...platform.fabric}, {@code ...platform.neoforge}). Both paths return
 * the instance installed here, which is why the same code works transformed and untransformed.
 */
public final class KVRPlatformProvider {

    private static KVRPlatform installed;

    private KVRPlatformProvider() {
    }

    public static void install(KVRPlatform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        installed = platform;
    }

    /** Test hook. */
    public static void reset() {
        installed = null;
    }

    @ExpectPlatform
    public static KVRPlatform get() {
        return installed();
    }

    /** Backing accessor used by {@link #get()} and by the platform-side implementation. */
    public static KVRPlatform installed() {
        KVRPlatform platform = installed;
        if (platform == null) {
            throw new IllegalStateException(
                    "KVR platform is not installed - the loader entrypoint must call KVRPlatformProvider.install()");
        }
        return platform;
    }
}
