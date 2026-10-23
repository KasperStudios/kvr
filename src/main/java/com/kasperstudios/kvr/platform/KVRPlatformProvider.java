package com.kasperstudios.kvr.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Access point to the loader-specific {@link KVRPlatform}.
 *
 * <p>Loader entrypoints install their implementation during mod construction, so the core can be
 * unit tested against a fake platform, and a missing installation fails loudly instead of
 * silently degrading.
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
        KVRPlatform platform = installed;
        if (platform == null) {
            throw new IllegalStateException(
                    "KVR platform is not installed - the loader entrypoint must call KVRPlatformProvider.install()");
        }
        return platform;
    }
}
