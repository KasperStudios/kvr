package com.kasperstudios.kvr.core;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation of KVR resource paths.
 *
 * <p>Every item, block, packet, screen and script asset id passes through here, so the rules live
 * in exactly one place: lowercase, no namespace separator, no whitespace. Pure Java on purpose -
 * this is the one seam unit tests can exercise without booting Minecraft.
 */
public final class KVRNames {

    /** Namespace owned by the mod. */
    public static final String NAMESPACE = "kvr";

    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9_.\\-/]+");

    private KVRNames() {
    }

    /**
     * Validates and returns a registry path.
     *
     * @throws IllegalArgumentException when the path is null, blank, uppercase, contains spaces
     *                                  or tries to carry its own namespace
     */
    public static String validated(String path) {
        if (path == null) {
            throw new IllegalArgumentException("KVR resource path must not be null");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("KVR resource path must not be blank");
        }
        if (path.indexOf(':') >= 0) {
            throw new IllegalArgumentException("KVR resource path '" + path
                    + "' must not contain a namespace - KVR always registers in '" + NAMESPACE + "'");
        }
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException("KVR resource path '" + path
                    + "' must not start, end or repeat the '/' separator");
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("KVR resource path '" + path
                        + "' must not contain relative segments - traversal outside the KVR namespace is not allowed");
            }
        }
        if (!VALID_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("KVR resource path '" + path + "' must be lowercase and use only "
                    + "[a-z0-9_.-/] (got '" + path.toLowerCase(Locale.ROOT) + "' after folding)");
        }
        return path;
    }
}
