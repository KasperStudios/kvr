package com.kasperstudios.kvr.core;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds namespaced identifiers for everything KVR registers.
 *
 * <p>The single version seam in the core: {@code new ResourceLocation(...)} was replaced by
 * {@code fromNamespaceAndPath(...)} in 1.21, and the class itself was renamed in 1.21.11 (handled by
 * the Stonecutter replacement in {@code gradle/kvr-versioning.gradle.kts}). Loader branches have their
 * own seams where loader APIs moved - see {@code NeoForgePlatform#isClient}.
 */
public final class KVRIds {

    private KVRIds() {
    }

    public static ResourceLocation id(String path) {
        String validated = KVRNames.validated(path);
        //? if <1.21 {
        /*return new ResourceLocation(KVRNames.NAMESPACE, validated);
        *///?} else {
        return ResourceLocation.fromNamespaceAndPath(KVRNames.NAMESPACE, validated);
        //?}
    }
}
