package com.kasperstudios.kvr.platform;

import java.util.Set;

/**
 * Access point to the loader-specific {@link KVRGui}.
 *
 * <p>Dedicated servers have no client implementation, so the default is a no-op that reports
 * itself honestly instead of throwing.
 */
public final class KVRGuiProvider {

    private static final KVRGui NOOP = new KVRGui() {
        @Override
        public Set<String> screens() {
            return Set.of();
        }

        @Override
        public boolean open(String screenId) {
            return false;
        }
    };

    private static KVRGui installed = NOOP;

    private KVRGuiProvider() {
    }

    public static void install(KVRGui gui) {
        if (gui == null) {
            throw new IllegalArgumentException("gui implementation must not be null");
        }
        installed = gui;
    }

    /** Test hook. */
    public static void reset() {
        installed = NOOP;
    }

    public static KVRGui get() {
        return installed;
    }
}
