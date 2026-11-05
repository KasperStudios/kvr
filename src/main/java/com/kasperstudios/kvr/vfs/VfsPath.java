package com.kasperstudios.kvr.vfs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * KVR OS paths (CONCEPT §2.1) and the app sandbox (§2.4):
 * <pre>
 * /home/          user files, shared by all apps (Notes writes coords.txt, Messenger sends it)
 * /tmp/           scratch, shared
 * /data/&lt;app&gt;/    private storage of one app
 * /apps/          installed .vrapp — read-only for apps (installer only)
 * /system/        denied (Root exploit chain later)
 * </pre>
 * Pure string logic, unit-tested; the server enforces it — client scripts are not trusted.
 */
public final class VfsPath {

    public static final int MAX_LENGTH = 128;
    public static final int MAX_DEPTH = 6;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    private VfsPath() {}

    /** "/home//notes/./a.txt" is rejected; returns a clean absolute path like "/home/notes/a.txt" ("/" for root). */
    public static String normalize(String raw) throws VfsException {
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '/' || raw.length() > MAX_LENGTH) {
            throw new VfsException(VfsException.Code.INVALID_PATH, String.valueOf(raw));
        }
        List<String> parts = new ArrayList<>();
        for (String s : raw.split("/")) {
            if (s.isEmpty()) continue;
            if (s.equals(".") || s.equals("..") || !SEGMENT.matcher(s).matches()) throw new VfsException(VfsException.Code.INVALID_PATH, raw);
            parts.add(s);
        }
        if (parts.size() > MAX_DEPTH) throw new VfsException(VfsException.Code.INVALID_PATH, "too deep: " + raw);
        return "/" + String.join("/", parts);
    }

    /** May app {@code appId} read (or write) the normalized {@code path}? */
    public static boolean canAccess(String path, String appId, boolean write) {
        if (path.equals("/") || path.equals("/data")) return !write; // listing the top only
        if (under(path, "/home") || under(path, "/tmp")) return true;
        if (under(path, "/data/" + appId)) return true;
        if (under(path, "/apps")) return !write;
        return false; // /system, other apps' /data, anything else
    }

    private static boolean under(String path, String dir) {
        return path.equals(dir) || path.startsWith(dir + "/");
    }
}
