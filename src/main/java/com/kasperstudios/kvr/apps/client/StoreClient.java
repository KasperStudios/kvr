package com.kasperstudios.kvr.apps.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.apps.StoreIndex;
import com.kasperstudios.kvr.apps.VrAppException;
import com.kasperstudios.kvr.apps.VrAppPackage;
import com.kasperstudios.kvr.config.AppsConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Online KVR Store on the client (Creator decision 4: the client downloads). One background thread does
 * the HTTP; results are handed back on the client thread. Downloads are capped by the size announced in
 * the index and accepted only if the SHA-256 matches the index entry — the bytes reviewed in the index PR.
 * Redirects are followed (GitHub release assets live on another host), but the final host must still be a
 * GitHub host and https ({@link StoreIndex#checkUrl}).
 */
public final class StoreClient {

    public enum State { IDLE, LOADING, READY, ERROR, OFF }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "KVR-Store");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL) // never https -> http
        .build();

    private static volatile State state = State.IDLE;
    private static volatile List<StoreIndex.Entry> entries = List.of();
    private static volatile String error = "";
    private static volatile String downloading = "";

    private StoreClient() {}

    public static void refresh() {
        String url = AppsConfig.client().storeIndexUrl();
        if (url.isEmpty()) {
            state = State.OFF;
            return;
        }
        if (state == State.LOADING) return;
        state = State.LOADING;
        boolean dev = AppsConfig.client().devApps();
        IO.execute(() -> {
            try {
                StoreIndex.checkUrl(url, dev);
                String json = new String(get(url, StoreIndex.MAX_INDEX_BYTES), StandardCharsets.UTF_8);
                StoreIndex.Parsed p = StoreIndex.parse(json, dev);
                for (String s : p.skipped()) KVR.logger().warn("KVR Store: skipped index entry: {}", s);
                Minecraft.getInstance().execute(() -> {
                    entries = p.entries();
                    error = "";
                    state = State.READY;
                });
            } catch (VrAppException | IOException | RuntimeException e) {
                String msg = e instanceof java.net.http.HttpTimeoutException ? "timeout" : String.valueOf(e.getMessage());
                KVR.logger().warn("KVR Store: index {} failed: {}", url, msg);
                Minecraft.getInstance().execute(() -> {
                    error = msg;
                    state = State.ERROR;
                });
            }
        });
    }

    /** Downloads and verifies one package; {@code ok}/{@code err} run on the client thread. */
    public static void download(StoreIndex.Entry e, Consumer<byte[]> ok, Consumer<String> err) {
        if (!downloading.isEmpty()) {
            err.accept("busy: " + downloading);
            return;
        }
        downloading = e.name();
        boolean dev = AppsConfig.client().devApps();
        IO.execute(() -> {
            try {
                StoreIndex.checkUrl(e.url(), dev);
                byte[] bytes = get(e.url(), Math.min(e.size(), VrAppPackage.MAX_PACKAGE_BYTES));
                if (bytes.length != e.size()) throw new VrAppException(VrAppException.Code.HASH_MISMATCH, "size " + bytes.length + " != " + e.size());
                String sha = VrAppPackage.sha256(bytes);
                if (!sha.equals(e.sha256())) throw new VrAppException(VrAppException.Code.HASH_MISMATCH, "sha256 " + sha.substring(0, 12) + "… != index");
                VrAppPackage pkg = VrAppPackage.read(bytes);
                if (!pkg.manifest().id().equals(e.id())) throw new VrAppException(VrAppException.Code.BAD_MANIFEST, "package id " + pkg.manifest().id() + " != index id " + e.id());
                Minecraft.getInstance().execute(() -> {
                    downloading = "";
                    ok.accept(bytes);
                });
            } catch (VrAppException | IOException | RuntimeException ex) {
                String msg = String.valueOf(ex.getMessage());
                KVR.logger().warn("KVR Store: download of {} failed: {}", e.id(), msg);
                Minecraft.getInstance().execute(() -> {
                    downloading = "";
                    err.accept(msg);
                });
            }
        });
    }

    /** GET with a hard byte cap (read while streaming, Content-Length is not trusted). */
    private static byte[] get(String url, int maxBytes) throws IOException, VrAppException {
        URI uri = URI.create(url);
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Path p = Path.of(uri);
            if (Files.size(p) > maxBytes) throw new VrAppException(VrAppException.Code.TOO_LARGE, p + " > " + maxBytes);
            return Files.readAllBytes(p);
        }
        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "KVR-Store/1 (Minecraft mod)")
            .GET()
            .build();
        HttpResponse<InputStream> res;
        try {
            res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            StoreIndex.checkUrl(res.uri().toString(), false); // where the redirects ended up
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) throw new VrAppException(VrAppException.Code.TOO_LARGE, "more than " + maxBytes + " bytes");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    public static State state() { return state; }
    public static List<StoreIndex.Entry> entries() { return entries; }
    public static String error() { return error; }
    public static String downloading() { return downloading; }
}
