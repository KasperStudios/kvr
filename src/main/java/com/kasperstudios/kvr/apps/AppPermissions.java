package com.kasperstudios.kvr.apps;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * App permissions (CONCEPT §2.4). A third-party {@code .vrapp} declares them in {@code config.json}
 * ({@code "permissions": ["network"]}); the App Market shows them before installing, and they are
 * granted for the app id when the package is launched. Apps bundled in the mod jar are trusted (they
 * are part of the OS) and are never registered here.
 *
 * <ul>
 *   <li>{@link #NETWORK} — anything that leaves the glasses: KVR Messenger ({@code kvr.msg}), later Rednet;</li>
 *   <li>{@link #FS_READ_ALL} — read every app's {@code /data/<app>/} (file managers, backup tools); checked by the server;</li>
 *   <li>{@link #HARDWARE_OVERRIDE} — reserved for the Superuser chain; no API uses it yet, it is only shown.</li>
 * </ul>
 * Unknown permission names make the package invalid — a typo must not silently become "no access".
 */
public final class AppPermissions {

    public static final String NETWORK = "network";
    public static final String FS_READ_ALL = "fs_read_all";
    public static final String HARDWARE_OVERRIDE = "hardware_override";
    public static final List<String> KNOWN = List.of(NETWORK, FS_READ_ALL, HARDWARE_OVERRIDE);

    /** Client side: app id → permissions of the running third-party package. Absent = OS app (trusted). */
    private static final Map<String, Set<String>> GRANTED = new ConcurrentHashMap<>();

    private AppPermissions() {}

    public static boolean known(String permission) {
        return KNOWN.contains(permission);
    }

    /** Called when a package (installed or dev) is launched. */
    public static void grant(String appId, Collection<String> permissions) {
        GRANTED.put(appId, Set.copyOf(permissions));
    }

    /** May the running app {@code appId} use {@code permission}? OS apps (never granted) may use everything. */
    public static boolean allowed(String appId, String permission) {
        Set<String> p = GRANTED.get(appId);
        return p == null || p.contains(permission);
    }

    /** Lang key of the human-readable description shown in the install dialog. */
    public static String langKey(String permission) {
        return "kvr.apps.perm." + permission;
    }
}
