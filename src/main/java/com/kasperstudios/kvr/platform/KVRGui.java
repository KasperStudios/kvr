package com.kasperstudios.kvr.platform;

/**
 * Loader facade for opening KVR screens.
 *
 * <p>Screen classes are client-only, so the core never names them: it asks for a screen by id
 * ({@code "chassis_assembly"}, {@code "kvr_store"}, ...) and the client-side implementation
 * resolves and opens it.
 */
public interface KVRGui {

    /** Screen ids the client can open. */
    java.util.Set<String> screens();

    /**
     * Opens a registered screen.
     *
     * @return {@code false} when the id is unknown or the caller is a dedicated server
     */
    boolean open(String screenId);
}
