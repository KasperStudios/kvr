package com.kasperstudios.kvr.platform;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.core.KVRNames;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loader-independent screen registry used by the client side.
 *
 * <p>Screens are registered as openers so this class never names a {@code Screen} type and stays safe
 * to load in a server environment; content that owns a screen registers its opener when it lands.
 */
public class SimpleKVRGui implements KVRGui {

    private final String loader;
    private final Map<String, Runnable> openers = new LinkedHashMap<>();

    public SimpleKVRGui(String loader) {
        this.loader = loader;
    }

    /** Registers the opener of a screen. Screen ids follow the same rules as registry paths. */
    public void register(String screenId, Runnable opener) {
        String id = KVRNames.validated(screenId);
        if (opener == null) {
            throw new IllegalArgumentException("opener for screen '" + id + "' must not be null");
        }
        if (openers.putIfAbsent(id, opener) != null) {
            throw new IllegalStateException("screen '" + id + "' is already registered on " + loader);
        }
    }

    @Override
    public Set<String> screens() {
        return Set.copyOf(openers.keySet());
    }

    @Override
    public boolean open(String screenId) {
        Runnable opener = screenId == null ? null : openers.get(screenId);
        if (opener == null) {
            KVR.logger().debug("KVR screen '{}' is not registered on {}", screenId, loader);
            return false;
        }
        opener.run();
        return true;
    }
}
