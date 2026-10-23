package com.kasperstudios.kvr.core;

import net.minecraft.resources.ResourceLocation;

/**
 * Builds namespaced identifiers for everything KVR registers.
 *
 * <p>This is the only file in the codebase where the Minecraft version matters at compile time:
 * {@code new ResourceLocation(...)} was replaced by {@code fromNamespaceAndPath(...)} in 1.21.
 * Stonecutter strips the branch that does not belong to the version being built.
 */
public final class KVRIds {

    public static final String NAMESPACE = KVRNames.NAMESPACE;

    private KVRIds() {
    }

    public static ResourceLocation id(String path) {
        String validated = KVRNames.validated(path);
        //? if <1.21 {
        /*return new ResourceLocation(NAMESPACE, validated);
        *///?} else {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, validated);
        //?}
    }
}
