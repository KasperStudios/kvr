package com.kasperstudios.kvr;

import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the platform-independent KVR core.
 *
 * <p>Loader entrypoints (Fabric {@code ModInitializer}, NeoForge {@code @Mod}) install their
 * {@link KVRPlatform} implementation and then call {@link #init()}. Nothing in this package may
 * reference a mod loader type - see {@code ArchitectureIsolationTest}.
 */
public final class KVR {

    public static final String MOD_ID = "kvr";
    public static final String MOD_NAME = "KVR";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static boolean initialized;
    private static String platformName = "unknown";

    private KVR() {
    }

    public static Logger logger() {
        return LOGGER;
    }

    /** Boots the KVR OS core. Safe to call once per process; later calls are ignored. */
    public static void init() {
        if (initialized) {
            LOGGER.debug("KVR core already initialized, ignoring repeated init()");
            return;
        }
        KVRPlatform platform = KVRPlatformProvider.get();
        platformName = platform.name();
        initialized = true;
        LOGGER.info("KVR OS core {} booting on {} ({})",
                platform.modVersion(), platform.name(), platform.isClient() ? "client" : "dedicated server");
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** Name of the loader the core was booted on, or {@code "unknown"} before {@link #init()}. */
    public static String platformName() {
        return platformName;
    }

    /** Test hook: returns the core to its pre-boot state. */
    static void reset() {
        initialized = false;
        platformName = "unknown";
    }
}
