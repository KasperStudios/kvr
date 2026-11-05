package com.kasperstudios.kvr.platform;

/**
 * Client facade, same pattern as {@code KVRRegistrar}/{@code KVRGui}: the loader's client entrypoint
 * installs its provider, the core asks for it and fails loudly if it is missing.
 */
public final class KVRClient {

    private static KVRClientProvider provider;

    private KVRClient() {}

    public static void install(KVRClientProvider p) {
        if (provider != null && provider != p) throw new IllegalStateException("KVRClient provider already installed: " + provider.getClass().getName());
        provider = p;
    }

    public static KVRClientProvider get() {
        if (provider == null) {
            throw new IllegalStateException("KVRClient is not installed: call KVRClient.install(...) from the loader client entrypoint before KVRSpatial.initClient()");
        }
        return provider;
    }
}
