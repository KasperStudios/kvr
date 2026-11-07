package com.kasperstudios.kvr.apps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.config.AppsConfig;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.vfs.VfsException;
import com.kasperstudios.kvr.vfs.VfsServer;
import com.kasperstudios.kvr.vfs.VfsStore;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the App Manager (Creator decision 4: the client downloads, the server checks size and
 * the disk quota). Installed packages are files {@code /apps/<id>.vrapp} in the VFS of the worn glasses,
 * so they travel with the glasses, count against the Storage tier (T1: 256 KB and 2 files — a real
 * bottleneck, ADR-003 §3.3) and are read-only for apps ({@code VfsPath}: {@code /apps} is installer-only).
 *
 * <p>Nothing the client says is trusted: the server re-hashes the uploaded bytes, parses and bundles the
 * package with {@link VrAppPackage}, rejects reserved ids and enforces {@code kvr-apps-server.toml}.
 *
 * <p>Common init: {@code AppsServer.init()} after {@code VfsServer.init()}.
 */
public final class AppsServer {

    private static final int MAX_PER_SECOND = 60;
    /** An upload with no chunk for this long is dropped (ticks). */
    private static final int UPLOAD_TIMEOUT_TICKS = 20 * 60;

    private record Upload(String appId, String sha256, int total, ByteArrayOutputStream data, int lastTick) {}

    private record Cached(long modified, long size, VrAppManifest manifest, String sha256) {}

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, int[]> RATE = new HashMap<>();
    /** Parsed manifests by absolute VFS file key; re-parsed only when the file changes. */
    private static final Map<String, Cached> MANIFESTS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > 512;
        }
    };

    private AppsServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(AppsProtocol.REQUEST, (player, buf) -> {
            AppsProtocol.Request req = AppsProtocol.Request.read(buf);
            MinecraftServer server = com.kasperstudios.kvr.compat.VersionCompat.server(player);
            if (server != null) server.execute(() -> KVRNetwork.sendToPlayer(player, AppsProtocol.REPLY, handle(player, req)::write));
        });
    }

    static AppsProtocol.Reply handle(ServerPlayer player, AppsProtocol.Request req) {
        try {
            rateLimit(player);
            Optional<VfsServer.Context> ctx = VfsServer.context(player);
            if (ctx.isEmpty()) throw new VrAppException(VrAppException.Code.NO_GLASSES, "worn glasses with a running OS required");
            VfsStore store = ctx.get().store();
            return switch (req.op()) {
                case LIST -> AppsProtocol.Reply.ok(req, list(store, ctx.get()).toString());
                case INSTALL_BEGIN -> begin(player, req);
                case INSTALL_CHUNK -> chunk(player, req);
                case INSTALL_COMMIT -> commit(player, req, ctx.get());
                case INSTALL_ABORT -> {
                    UPLOADS.remove(player.getUUID());
                    yield AppsProtocol.Reply.ok(req, "");
                }
                case UNINSTALL -> uninstall(req, store);
                case FETCH -> fetch(req, store);
                case INSTALL_BUNDLED -> installBundled(player, req, ctx.get());
            };
        } catch (VrAppException e) {
            if (req.op() == AppsProtocol.Op.INSTALL_CHUNK || req.op() == AppsProtocol.Op.INSTALL_COMMIT) UPLOADS.remove(player.getUUID());
            return AppsProtocol.Reply.fail(req, e.code().name(), e.getMessage());
        } catch (VfsException e) {
            if (req.op() == AppsProtocol.Op.INSTALL_COMMIT) UPLOADS.remove(player.getUUID());
            return AppsProtocol.Reply.fail(req, e.code().name(), e.getMessage());
        } catch (RuntimeException e) {
            UPLOADS.remove(player.getUUID());
            KVR.logger().warn("KVR apps: {} from {} failed", req.op(), player.getName().getString(), e);
            return AppsProtocol.Reply.fail(req, VrAppException.Code.IO.name(), e.toString());
        }
    }

    // --- LIST ------------------------------------------------------------------------------------

    private static JsonObject list(VfsStore store, VfsServer.Context ctx) throws VfsException {
        AppsConfig.Server cfg = AppsConfig.server();
        JsonObject out = new JsonObject();
        JsonObject policy = new JsonObject();
        policy.addProperty("allowInstall", cfg.allowInstall());
        JsonArray allow = new JsonArray();
        cfg.allowlist().forEach(allow::add);
        policy.add("allowlist", allow);
        policy.addProperty("maxPackageBytes", maxBytes(cfg));
        out.add("policy", policy);

        VfsStore.Usage u = store.usage();
        JsonObject usage = new JsonObject();
        usage.addProperty("bytes", u.bytes());
        usage.addProperty("files", u.files());
        usage.addProperty("quotaBytes", ctx.quota().bytes());
        usage.addProperty("quotaFiles", ctx.quota().files() == Integer.MAX_VALUE ? -1 : ctx.quota().files());
        out.add("usage", usage);

        JsonArray apps = new JsonArray();
        for (Installed i : installed(store)) {
            JsonObject a = new JsonObject();
            a.add("manifest", i.manifest().toJson());
            a.addProperty("sha256", i.sha256());
            a.addProperty("size", i.size());
            apps.add(a);
        }
        out.add("apps", apps);
        return out;
    }

    public record Installed(VrAppManifest manifest, String sha256, long size) {}

    /** Every valid package in {@code /apps}; broken files are skipped (and logged once per change). */
    public static java.util.List<Installed> installed(VfsStore store) throws VfsException {
        java.util.List<Installed> out = new java.util.ArrayList<>();
        java.util.List<VfsStore.Entry> entries;
        try {
            entries = store.list("/apps");
        } catch (VfsException e) {
            if (e.code() == VfsException.Code.NOT_FOUND) return out;
            throw e;
        }
        for (VfsStore.Entry e : entries) {
            if (e.dir() || !e.name().endsWith(".vrapp")) continue;
            String path = "/apps/" + e.name();
            Optional<Cached> c = manifest(store, path);
            c.ifPresent(m -> out.add(new Installed(m.manifest(), m.sha256(), m.size())));
        }
        return out;
    }

    private static Optional<Cached> manifest(VfsStore store, String path) throws VfsException {
        long[] st = store.stamp(path);
        if (st[0] < 0) return Optional.empty();
        String key = store.root() + path;
        Cached c = MANIFESTS.get(key);
        if (c != null && c.modified() == st[0] && c.size() == st[1]) return Optional.of(c);
        byte[] bytes = store.readBytes(path, VrAppPackage.MAX_PACKAGE_BYTES);
        try {
            VrAppPackage pkg = VrAppPackage.read(bytes);
            Cached fresh = new Cached(st[0], st[1], pkg.manifest(), pkg.sha256());
            MANIFESTS.put(key, fresh);
            return Optional.of(fresh);
        } catch (VrAppException ex) {
            KVR.logger().warn("KVR apps: skipping broken package {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Does the installed package {@code appId} on these glasses declare {@code permission}? (VfsServer: fs_read_all) */
    public static boolean grants(VfsStore store, String appId, String permission) {
        try {
            Optional<Cached> c = manifest(store, "/apps/" + appId + ".vrapp");
            return c.isPresent() && c.get().manifest().permissions().contains(permission);
        } catch (VfsException e) {
            return false;
        }
    }

    // --- INSTALL ---------------------------------------------------------------------------------

    private static int maxBytes(AppsConfig.Server cfg) {
        return Math.min(VrAppPackage.MAX_PACKAGE_BYTES, cfg.maxPackageKb() * 1024);
    }

    private static void checkPolicy(String appId) throws VrAppException {
        AppsConfig.Server cfg = AppsConfig.server();
        if (!cfg.allowInstall()) throw new VrAppException(VrAppException.Code.NOT_ALLOWED, "this server does not allow installing apps");
        if (!cfg.allowlist().isEmpty() && !cfg.allowlist().contains(appId)) throw new VrAppException(VrAppException.Code.NOT_ALLOWED, appId + " is not on this server's allowlist");
        if (!VrAppManifest.ID.matcher(appId).matches()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "bad id " + appId);
        if (VrAppManifest.reserved(appId)) throw new VrAppException(VrAppException.Code.RESERVED_ID, appId);
    }

    private static AppsProtocol.Reply begin(ServerPlayer player, AppsProtocol.Request req) throws VrAppException {
        checkPolicy(req.appId());
        int max = maxBytes(AppsConfig.server());
        if (req.total() <= 0 || req.total() > max) throw new VrAppException(VrAppException.Code.TOO_LARGE, req.total() + " > " + max + " bytes");
        if (!req.sha256().matches("[0-9a-f]{64}")) throw new VrAppException(VrAppException.Code.HASH_MISMATCH, "bad sha256");
        Upload old = UPLOADS.get(player.getUUID());
        int now = com.kasperstudios.kvr.compat.VersionCompat.server(player).getTickCount();
        if (old != null && now - old.lastTick() < UPLOAD_TIMEOUT_TICKS) throw new VrAppException(VrAppException.Code.BUSY, "another install is in progress");
        UPLOADS.put(player.getUUID(), new Upload(req.appId(), req.sha256(), req.total(), new ByteArrayOutputStream(req.total()), now));
        return AppsProtocol.Reply.ok(req, "");
    }

    private static AppsProtocol.Reply chunk(ServerPlayer player, AppsProtocol.Request req) throws VrAppException {
        Upload u = UPLOADS.get(player.getUUID());
        if (u == null) throw new VrAppException(VrAppException.Code.NOT_FOUND, "no install in progress");
        if (req.offset() != u.data().size()) throw new VrAppException(VrAppException.Code.BAD_ZIP, "chunk at " + req.offset() + ", expected " + u.data().size());
        if (u.data().size() + req.data().length > u.total()) throw new VrAppException(VrAppException.Code.TOO_LARGE, "more bytes than announced");
        u.data().write(req.data(), 0, req.data().length);
        UPLOADS.put(player.getUUID(), new Upload(u.appId(), u.sha256(), u.total(), u.data(), com.kasperstudios.kvr.compat.VersionCompat.server(player).getTickCount()));
        return AppsProtocol.Reply.ok(req, "");
    }

    private static AppsProtocol.Reply commit(ServerPlayer player, AppsProtocol.Request req, VfsServer.Context ctx) throws VrAppException, VfsException {
        Upload u = UPLOADS.remove(player.getUUID());
        if (u == null) throw new VrAppException(VrAppException.Code.NOT_FOUND, "no install in progress");
        byte[] bytes = u.data().toByteArray();
        if (bytes.length != u.total()) throw new VrAppException(VrAppException.Code.BAD_ZIP, bytes.length + " of " + u.total() + " bytes received");
        String sha = VrAppPackage.sha256(bytes);
        if (!sha.equals(u.sha256())) throw new VrAppException(VrAppException.Code.HASH_MISMATCH, "upload damaged in transit");
        VrAppPackage pkg = VrAppPackage.read(bytes);
        if (!pkg.manifest().id().equals(u.appId())) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "package id " + pkg.manifest().id() + " != " + u.appId());
        checkPolicy(pkg.manifest().id());
        try {
            ctx.store().writeBytes("/apps/" + u.appId() + ".vrapp", bytes, ctx.quota());
        } catch (VfsException e) {
            if (e.code() == VfsException.Code.QUOTA_BYTES || e.code() == VfsException.Code.QUOTA_FILES) {
                throw new VrAppException(VrAppException.Code.QUOTA, e.getMessage());
            }
            throw e;
        }
        KVR.logger().info("KVR apps: {} installed {} {} ({} bytes, {})", player.getName().getString(), u.appId(), pkg.manifest().version(), bytes.length, sha.substring(0, 12));
        JsonObject a = new JsonObject();
        a.add("manifest", pkg.manifest().toJson());
        a.addProperty("sha256", sha);
        a.addProperty("size", bytes.length);
        return AppsProtocol.Reply.ok(req, a.toString());
    }

    private static AppsProtocol.Reply uninstall(AppsProtocol.Request req, VfsStore store) throws VrAppException, VfsException {
        if (!VrAppManifest.ID.matcher(req.appId()).matches()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "bad id");
        try {
            store.delete("/apps/" + req.appId() + ".vrapp");
        } catch (VfsException e) {
            if (e.code() == VfsException.Code.NOT_FOUND) throw new VrAppException(VrAppException.Code.NOT_FOUND, req.appId());
            throw e;
        }
        return AppsProtocol.Reply.ok(req, "");
    }

    // --- INSTALL_BUNDLED (0027) ---------------------------------------------------------------------

    /**
     * Built-in catalog app: the server packs it from its own jar, so the client sends only the id. Not subject to
     * {@code allow_app_install} / allowlist — it is part of the mod, not third-party code — but it takes disk space
     * like any package (Creator: everything except Market, Messenger and Settings is installed and occupies Storage).
     */
    private static AppsProtocol.Reply installBundled(ServerPlayer player, AppsProtocol.Request req, VfsServer.Context ctx) throws VrAppException, VfsException {
        if (!BundledApps.isBundled(req.appId())) throw new VrAppException(VrAppException.Code.NOT_FOUND, "not in the built-in catalog: " + req.appId());
        byte[] bytes = BundledApps.pack(req.appId());
        VrAppPackage pkg = VrAppPackage.read(bytes);
        try {
            ctx.store().writeBytes("/apps/" + req.appId() + ".vrapp", bytes, ctx.quota());
        } catch (VfsException e) {
            if (e.code() == VfsException.Code.QUOTA_BYTES || e.code() == VfsException.Code.QUOTA_FILES) {
                throw new VrAppException(VrAppException.Code.QUOTA, e.getMessage());
            }
            throw e;
        }
        KVR.logger().info("KVR apps: {} installed built-in {} {} ({} bytes)", player.getName().getString(), req.appId(), pkg.manifest().version(), bytes.length);
        JsonObject a = new JsonObject();
        a.add("manifest", pkg.manifest().toJson());
        a.addProperty("sha256", pkg.sha256());
        a.addProperty("size", bytes.length);
        return AppsProtocol.Reply.ok(req, a.toString());
    }

    // --- FETCH -----------------------------------------------------------------------------------

    private static AppsProtocol.Reply fetch(AppsProtocol.Request req, VfsStore store) throws VrAppException, VfsException {
        if (!VrAppManifest.ID.matcher(req.appId()).matches()) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "bad id");
        byte[] bytes;
        try {
            bytes = store.readBytes("/apps/" + req.appId() + ".vrapp", VrAppPackage.MAX_PACKAGE_BYTES);
        } catch (VfsException e) {
            if (e.code() == VfsException.Code.NOT_FOUND) throw new VrAppException(VrAppException.Code.NOT_FOUND, req.appId());
            throw e;
        }
        if (!req.sha256().isEmpty() && !VrAppPackage.sha256(bytes).equals(req.sha256())) {
            throw new VrAppException(VrAppException.Code.HASH_MISMATCH, "package changed, refresh the list");
        }
        int off = req.offset();
        if (off < 0 || off > bytes.length) throw new VrAppException(VrAppException.Code.BAD_ZIP, "offset " + off);
        int len = Math.min(AppsProtocol.CHUNK_DOWN, bytes.length - off);
        byte[] part = java.util.Arrays.copyOfRange(bytes, off, off + len);
        return new AppsProtocol.Reply(req.req(), req.op(), true, "", "", "", off, bytes.length, part);
    }

    private static void rateLimit(ServerPlayer player) throws VrAppException {
        int second = com.kasperstudios.kvr.compat.VersionCompat.server(player).getTickCount() / 20;
        int[] r = RATE.computeIfAbsent(player.getUUID(), k -> new int[] {second, 0});
        if (r[0] != second) {
            r[0] = second;
            r[1] = 0;
        }
        if (++r[1] > MAX_PER_SECOND) throw new VrAppException(VrAppException.Code.RATE_LIMIT, "too many requests");
    }
}
