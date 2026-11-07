package com.kasperstudios.kvr.apps.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.AppsProtocol;
import com.kasperstudios.kvr.apps.VrAppException;
import com.kasperstudios.kvr.apps.VrAppManifest;
import com.kasperstudios.kvr.apps.VrAppPackage;
import com.kasperstudios.kvr.platform.KVRNetwork;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the App Manager: the list of packages installed in the worn glasses, chunked install /
 * download and the status the App Market shows. Everything runs on the client thread (replies are handed
 * over with {@code Minecraft.execute}); requests without a reply in {@link #TIMEOUT_MS} fail with
 * {@code TIMEOUT} so the UI never hangs on a server without KVR 0025.
 */
public final class AppsClient {

    public record Installed(VrAppManifest manifest, String sha256, long size) {}

    public record Policy(boolean allowInstall, List<String> allowlist, int maxPackageBytes) {
        static final Policy UNKNOWN = new Policy(false, List.of(), 0);
    }

    public record Usage(long bytes, int files, long quotaBytes, int quotaFiles) {
        static final Usage UNKNOWN = new Usage(0, 0, 0, 0);
    }

    /** Why an operation failed: {@code code} = VrAppException/VfsException name, TIMEOUT or LOCAL. */
    public record Failure(String code, String message) {
        public String langKey() {
            return "kvr.apps.err." + code.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final long TIMEOUT_MS = 15_000;

    private record Pending(Consumer<AppsProtocol.Reply> callback, long sentAt, AppsProtocol.Op op) {}

    private static int seq;
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static List<Installed> installed = List.of();
    private static Policy policy = Policy.UNKNOWN;
    private static Usage usage = Usage.UNKNOWN;
    private static boolean loaded;
    @Nullable private static Failure lastError;
    /** Current long operation for the status line ("Installing weather… 40 %"); empty = idle. */
    private static String task = "";
    private static float progress = -1f;

    private AppsClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(AppsProtocol.REPLY, buf -> {
            AppsProtocol.Reply r = AppsProtocol.Reply.read(buf);
            Minecraft.getInstance().execute(() -> {
                Pending p = PENDING.remove(r.req());
                if (p != null) p.callback().accept(r);
            });
        });
    }

    /** Client tick: expire requests the server never answered. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Pending> e = it.next();
            if (now - e.getValue().sentAt() < TIMEOUT_MS) continue;
            it.remove();
            Pending p = e.getValue();
            p.callback().accept(new AppsProtocol.Reply(e.getKey(), p.op(), false, "TIMEOUT", "no reply from the server (KVR 0025 missing there?)", "", 0, 0, new byte[0]));
        }
    }

    /** Disconnect / glasses removed. */
    public static void reset() {
        PENDING.clear();
        installed = List.of();
        policy = Policy.UNKNOWN;
        usage = Usage.UNKNOWN;
        loaded = false;
        lastError = null;
        task = "";
        progress = -1f;
    }

    private static void send(AppsProtocol.Request base, Consumer<AppsProtocol.Reply> callback) {
        int id = ++seq;
        AppsProtocol.Request req = new AppsProtocol.Request(id, base.op(), base.appId(), base.sha256(), base.total(), base.offset(), base.data());
        PENDING.put(id, new Pending(callback, System.currentTimeMillis(), req.op()));
        KVRNetwork.sendToServer(AppsProtocol.REQUEST, req::write);
    }

    private static AppsProtocol.Request req(AppsProtocol.Op op, String appId, String sha, int total, int offset, byte[] data) {
        return new AppsProtocol.Request(0, op, appId, sha, total, offset, data);
    }

    private static Failure failure(AppsProtocol.Reply r) {
        return new Failure(r.error().isEmpty() ? "IO" : r.error(), r.message());
    }

    // --- list ------------------------------------------------------------------------------------

    public static void refresh() {
        refresh(f -> {});
    }

    public static void refresh(Consumer<Failure> done) {
        send(req(AppsProtocol.Op.LIST, "", "", 0, 0, new byte[0]), r -> {
            if (!r.ok()) {
                lastError = failure(r);
                done.accept(lastError);
                return;
            }
            try {
                apply(JsonParser.parseString(r.json()).getAsJsonObject());
                lastError = null;
                loaded = true;
                done.accept(null);
            } catch (RuntimeException e) {
                lastError = new Failure("IO", e.toString());
                done.accept(lastError);
            }
        });
    }

    private static void apply(JsonObject o) {
        JsonObject p = o.getAsJsonObject("policy");
        List<String> allow = new ArrayList<>();
        for (JsonElement e : p.getAsJsonArray("allowlist")) allow.add(e.getAsString());
        policy = new Policy(p.get("allowInstall").getAsBoolean(), List.copyOf(allow), p.get("maxPackageBytes").getAsInt());
        JsonObject u = o.getAsJsonObject("usage");
        usage = new Usage(u.get("bytes").getAsLong(), u.get("files").getAsInt(), u.get("quotaBytes").getAsLong(), u.get("quotaFiles").getAsInt());
        List<Installed> list = new ArrayList<>();
        JsonArray apps = o.getAsJsonArray("apps");
        for (JsonElement e : apps) {
            JsonObject a = e.getAsJsonObject();
            try {
                list.add(new Installed(VrAppManifest.parse(a.getAsJsonObject("manifest").toString()), a.get("sha256").getAsString(), a.get("size").getAsLong()));
            } catch (VrAppException ex) {
                KVR.logger().warn("KVR apps: server listed an unreadable manifest: {}", ex.getMessage());
            }
        }
        list.sort((x, y) -> x.manifest().name().compareToIgnoreCase(y.manifest().name()));
        installed = List.copyOf(list);
    }

    // --- install ---------------------------------------------------------------------------------

    /**
     * Validates locally (fast, clear errors), then uploads in chunks and commits. On success the bytes go to
     * the cache (no download on first launch) and the list is refreshed.
     */
    public static void install(byte[] bytes, Consumer<Failure> done) {
        if (!task.isEmpty()) {
            done.accept(new Failure("BUSY", task));
            return;
        }
        VrAppPackage pkg;
        try {
            pkg = VrAppPackage.read(bytes);
        } catch (VrAppException e) {
            done.accept(new Failure(e.code().name(), e.getMessage()));
            return;
        }
        String id = pkg.manifest().id();
        String sha = pkg.sha256();
        task = pkg.manifest().name();
        progress = 0f;
        Consumer<Failure> finish = f -> {
            task = "";
            progress = -1f;
            if (f == null) {
                AppCache.put(bytes);
                refresh();
            }
            done.accept(f);
        };
        send(req(AppsProtocol.Op.INSTALL_BEGIN, id, sha, bytes.length, 0, new byte[0]), r -> {
            if (!r.ok()) {
                finish.accept(failure(r));
                return;
            }
            uploadFrom(id, bytes, 0, finish);
        });
    }

    private static void uploadFrom(String id, byte[] bytes, int offset, Consumer<Failure> finish) {
        if (offset >= bytes.length) {
            send(req(AppsProtocol.Op.INSTALL_COMMIT, id, "", 0, 0, new byte[0]), r -> finish.accept(r.ok() ? null : failure(r)));
            return;
        }
        int len = Math.min(AppsProtocol.CHUNK_UP, bytes.length - offset);
        byte[] part = Arrays.copyOfRange(bytes, offset, offset + len);
        send(req(AppsProtocol.Op.INSTALL_CHUNK, id, "", 0, offset, part), r -> {
            if (!r.ok()) {
                send(req(AppsProtocol.Op.INSTALL_ABORT, id, "", 0, 0, new byte[0]), x -> {});
                finish.accept(failure(r));
                return;
            }
            progress = (offset + len) / (float) bytes.length;
            uploadFrom(id, bytes, offset + len, finish);
        });
    }

    public static void uninstall(String appId, Consumer<Failure> done) {
        send(req(AppsProtocol.Op.UNINSTALL, appId, "", 0, 0, new byte[0]), r -> {
            if (r.ok()) refresh();
            done.accept(r.ok() ? null : failure(r));
        });
    }

    // --- fetch -----------------------------------------------------------------------------------

    /** The package bytes of an installed app: from the cache, or downloaded in chunks and cached. */
    public static void fetch(Installed app, Consumer<byte[]> ok, Consumer<Failure> err) {
        Optional<byte[]> cached = AppCache.get(app.sha256());
        if (cached.isPresent()) {
            ok.accept(cached.get());
            return;
        }
        fetchFrom(app, new ByteArrayOutputStream((int) Math.max(0, app.size())), ok, err);
    }

    private static void fetchFrom(Installed app, ByteArrayOutputStream acc, Consumer<byte[]> ok, Consumer<Failure> err) {
        send(req(AppsProtocol.Op.FETCH, app.manifest().id(), app.sha256(), 0, acc.size(), new byte[0]), r -> {
            if (!r.ok()) {
                err.accept(failure(r));
                return;
            }
            if (r.offset() != acc.size() || r.total() <= 0 || r.total() > VrAppPackage.MAX_PACKAGE_BYTES) {
                err.accept(new Failure("BAD_ZIP", "unexpected chunk"));
                return;
            }
            acc.write(r.data(), 0, r.data().length);
            if (acc.size() < r.total()) {
                if (r.data().length == 0) {
                    err.accept(new Failure("BAD_ZIP", "empty chunk"));
                    return;
                }
                fetchFrom(app, acc, ok, err);
                return;
            }
            byte[] bytes = acc.toByteArray();
            if (!VrAppPackage.sha256(bytes).equals(app.sha256())) {
                err.accept(new Failure("HASH_MISMATCH", app.manifest().id()));
                return;
            }
            AppCache.put(bytes);
            ok.accept(bytes);
        });
    }

    // --- state for the UI ------------------------------------------------------------------------

    public static List<Installed> installed() { return installed; }
    public static Optional<Installed> installed(String appId) {
        return installed.stream().filter(i -> i.manifest().id().equals(appId)).findFirst();
    }
    public static Policy policy() { return policy; }
    public static Usage usage() { return usage; }
    public static boolean loaded() { return loaded; }
    @Nullable public static Failure lastError() { return lastError; }
    public static String task() { return task; }
    public static float progress() { return progress; }
    public static boolean busy() { return !task.isEmpty(); }
}
