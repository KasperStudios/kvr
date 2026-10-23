package com.kasperstudios.kvr.platform;

import java.nio.file.Path;

/**
 * Everything the KVR core needs to know about the mod loader it runs on.
 *
 * <p>Implemented once per loader ({@code fabric}, {@code neoforge}); the core only ever sees this
 * interface.
 */
public interface KVRPlatform {

    /** Loader name, e.g. {@code "fabric"}. */
    String name();

    /** Version of the KVR mod as resolved by the loader metadata. */
    String modVersion();

    /** Whether another mod (KVR Store app host, CC:Tweaked, voice chat) is present. */
    boolean isModLoaded(String modId);

    /** Root directory of the running game instance. */
    Path gameDir();

    /** Directory where KVR keeps its config and virtual-disk data. */
    Path configDir();

    /** {@code true} on a client, {@code false} on a dedicated server. */
    boolean isClient();
}
