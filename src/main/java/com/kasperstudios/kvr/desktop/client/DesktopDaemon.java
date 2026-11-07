package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.config.KvrConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code kvr-desktopd} process: where the binary is, whether the user agreed to run <i>this exact</i>
 * binary (consent is bound to its SHA-256 — a swapped file asks again), the optional verified
 * download, and the background launch through {@link ProcessBuilder}.
 *
 * <ul>
 *   <li>Socket + shared-memory files live in a private directory: Linux {@code $XDG_RUNTIME_DIR/kvr} (0700),
 *       Windows {@code %LOCALAPPDATA%\kvr}. The session token is random per launch and passed only through
 *       the child's environment ({@code KVR_DESKTOPD_TOKEN}), never on the command line.</li>
 *   <li>The daemon dies with the game: JVM shutdown hook here, {@code PR_SET_PDEATHSIG} on the Rust side.</li>
 *   <li>Its log goes to {@code logs/kvr-desktopd.log}.</li>
 * </ul>
 */
public final class DesktopDaemon {

    public enum State { STOPPED, STARTING, RUNNING, FAILED }

    /** What the consent screen shows. {@code sha256} is empty when the file does not exist (yet). */
    public record Info(Path binary, boolean exists, String sha256, String downloadUrl, String downloadSha256, boolean consented) {
        public boolean canDownload() {
            return !exists && !downloadUrl.isEmpty() && downloadSha256.length() == 64;
        }

        /** The hash the user is asked to approve: the file on disk, or the pinned hash of the download. */
        public String approvedHash() {
            return exists ? sha256 : downloadSha256;
        }
    }

    private static final Object LOCK = new Object();
    @Nullable private static Process process;
    private static volatile State state = State.STOPPED;
    private static volatile String error = "";
    private static String token = "";
    private static boolean hookInstalled;

    private DesktopDaemon() {}

    // --- files ---------------------------------------------------------------------------------

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Path binary() {
        String configured = KvrConfig.client().hostDesktopDaemon();
        if (!configured.isEmpty()) return Path.of(configured);
        return Path.of("config", "kvr", "bin", windows() ? "kvr-desktopd.exe" : "kvr-desktopd");
    }

    /** Private per-user directory for the socket and the stream buffers. */
    public static Path runtimeDir() throws IOException {
        Path dir = runtimeDir(System.getenv(), windows(), System.getProperty("java.io.tmpdir"), System.getProperty("user.name", "user"));
        if (!Files.exists(dir)) Files.createDirectories(dir);
        if (!windows()) Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        return dir;
    }

    /** Pure, unit-tested. */
    static Path runtimeDir(Map<String, String> env, boolean windows, String tmp, String user) {
        if (windows) {
            String local = env.get("LOCALAPPDATA");
            return local != null && !local.isEmpty() ? Path.of(local, "kvr") : Path.of(tmp, "kvr-" + user);
        }
        String xdg = env.get("XDG_RUNTIME_DIR");
        return xdg != null && !xdg.isEmpty() ? Path.of(xdg, "kvr") : Path.of(tmp, "kvr-" + user);
    }

    public static Path socketPath() throws IOException {
        return runtimeDir().resolve("desktopd.sock");
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Info inspect() {
        KvrConfig.Client c = KvrConfig.client();
        Path bin = binary();
        boolean exists = Files.isRegularFile(bin);
        String sha = "";
        if (exists) {
            try {
                sha = sha256(bin);
            } catch (IOException e) {
                exists = false;
            }
        }
        String pinned = c.hostDesktopDownloadSha256().toLowerCase(Locale.ROOT);
        String approved = c.hostDesktopConsent().toLowerCase(Locale.ROOT);
        boolean consented = !approved.isEmpty() && approved.equals(exists ? sha : pinned);
        return new Info(bin, exists, sha, c.hostDesktopDownloadUrl(), pinned, consented);
    }

    /** Consent dialog: "Разрешить". Binds the consent to the exact binary (or pinned download). */
    public static void grantConsent(Info info) {
        KvrConfig.updateClient("host_desktop_consent", info.approvedHash());
    }

    public static void revokeConsent() {
        KvrConfig.updateClient("host_desktop_consent", "");
        stop();
    }

    // --- download ------------------------------------------------------------------------------

    /** Fetches the binary to {@link #binary()} only if its SHA-256 equals the pinned one. Async. */
    public static CompletableFuture<Path> download(Info info) {
        if (!info.canDownload()) return CompletableFuture.failedFuture(new IOException("no download URL with a pinned SHA-256"));
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
        Path target = info.binary();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(target.toAbsolutePath().getParent());
                Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "kvr-desktopd", ".part");
                try {
                    HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(info.downloadUrl())).timeout(Duration.ofMinutes(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofFile(tmp));
                    if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
                    String got = sha256(tmp);
                    if (!got.equalsIgnoreCase(info.downloadSha256())) throw new IOException("SHA-256 mismatch: got " + got);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    if (!windows()) Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwx------"));
                    return target;
                } finally {
                    Files.deleteIfExists(tmp);
                }
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    // --- process -------------------------------------------------------------------------------

    /**
     * Starts the daemon if it is not running. Refuses without consent for the exact binary.
     * @return the socket to connect to
     */
    public static Path start() throws IOException {
        synchronized (LOCK) {
            if (process != null && process.isAlive()) return socketPath();
            Info info = inspect();
            if (!info.exists()) throw new IOException("kvr-desktopd not found: " + info.binary().toAbsolutePath());
            if (!info.consented()) throw new IOException("no consent for this kvr-desktopd binary (sha256 " + info.sha256() + ")");
            Path socket = socketPath();
            Files.deleteIfExists(socket);
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            token = HexFormat.of().formatHex(raw);

            List<String> cmd = new ArrayList<>();
            cmd.add(info.binary().toAbsolutePath().toString());
            cmd.add("--socket");
            cmd.add(socket.toAbsolutePath().toString());
            cmd.add("--shm-dir");
            cmd.add(runtimeDir().toAbsolutePath().toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("KVR_DESKTOPD_TOKEN", token);
            pb.environment().putIfAbsent("RUST_LOG", "info");
            applyXkb(pb.environment(), KvrConfig.desktopXkb(), gameLanguage());
            Path log = Path.of("logs", "kvr-desktopd.log");
            Files.createDirectories(log.toAbsolutePath().getParent());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            state = State.STARTING;
            try {
                process = pb.start();
            } catch (IOException e) {
                state = State.FAILED;
                error = e.getMessage();
                throw e;
            }
            if (!hookInstalled) {
                hookInstalled = true;
                Runtime.getRuntime().addShutdownHook(new Thread(DesktopDaemon::stop, "KVR-Desktopd-Stop"));
            }
            KVR.logger().info("KVR: started kvr-desktopd (pid {}), socket {}", process.pid(), socket);
            return socket;
        }
    }

    public static void markRunning() {
        state = State.RUNNING;
    }

    public static void markFailed(String why) {
        state = State.FAILED;
        error = why == null ? "" : why;
    }

    public static boolean alive() {
        Process p = process;
        return p != null && p.isAlive();
    }

    /** Exit code of a dead daemon, for the error text. */
    public static String exitInfo() {
        Process p = process;
        if (p == null) return "not started";
        return p.isAlive() ? "running" : "exited with code " + p.exitValue() + " (see logs/kvr-desktopd.log)";
    }

    public static void stop() {
        Process p;
        synchronized (LOCK) {
            p = process;
            process = null;
        }
        state = State.STOPPED;
        if (p == null || !p.isAlive()) return;
        p.destroy(); // SIGTERM: the daemon closes its apps and removes its files
        try {
            if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    public static String token() { return token; }
    public static State state() { return state; }
    public static String error() { return error; }

    /**
     * 0031 — layouts inside PC apps. The hidden compositor builds its xkb keymap with empty names, and libxkbcommon
     * then takes {@code XKB_DEFAULT_LAYOUT / _VARIANT / _OPTIONS} from the environment. Setting them for the daemon
     * process gives its keymap our layouts + switch combo with no daemon flag (an older kvr-desktopd keeps working).
     * Before: no variables → libxkbcommon's built-in "us" only → Russian was impossible in foot/Firefox.
     * <ul>
     *   <li>layout: config {@code desktop_xkb_layout}; else the session's {@code XKB_DEFAULT_LAYOUT}; else from the game
     *       language ({@code ru_*} → "us,ru", {@code uk_*} → "us,ua", {@code be_*} → "us,by"); else "us";</li>
     *   <li>variant / options: config if set (options default {@code grp:alt_shift_toggle}).</li>
     * </ul>
     * Pure apart from the map, unit-testable.
     */
    static void applyXkb(Map<String, String> env, KvrConfig.Xkb xkb, String gameLanguage) {
        String layout = xkb.layout();
        String session = env.getOrDefault("XKB_DEFAULT_LAYOUT", "");
        if (layout.isEmpty()) {
            String lang = gameLanguage.toLowerCase(Locale.ROOT);
            layout = !session.isBlank() ? session
                : lang.startsWith("ru") ? "us,ru"
                : lang.startsWith("uk") ? "us,ua"
                : lang.startsWith("be") ? "us,by"
                : "us";
        }
        env.put("XKB_DEFAULT_LAYOUT", layout);
        if (!xkb.variant().isEmpty()) env.put("XKB_DEFAULT_VARIANT", xkb.variant());
        else if (!layout.equals(session)) env.remove("XKB_DEFAULT_VARIANT"); // a session variant list would not match our layout list
        if (!xkb.options().isEmpty()) env.put("XKB_DEFAULT_OPTIONS", xkb.options());
    }

    private static String gameLanguage() {
        try {
            return net.minecraft.client.Minecraft.getInstance().options.languageCode;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** {@code host_desktop_apps}: "foot;firefox --new-instance" → [[foot], [firefox, --new-instance]]. Pure. */
    public static List<List<String>> apps(String config) {
        List<List<String>> out = new ArrayList<>();
        for (String entry : config.split(";")) {
            List<String> argv = new ArrayList<>();
            for (String part : entry.strip().split("\\s+")) if (!part.isEmpty()) argv.add(part);
            if (!argv.isEmpty() && out.size() < 12) out.add(List.copyOf(argv));
        }
        return out;
    }
}
