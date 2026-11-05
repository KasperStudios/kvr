package com.kasperstudios.kvr.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.script.ui.UiNode;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.jetbrains.annotations.Nullable;

/**
 * Runs one app: its own daemon thread (never the render thread, DevNotes 7), the CPU-tier
 * {@link ScriptClock}, the {@link Watchdog}, an inbox of UI events, and the latest committed
 * {@link UiNode} tree. A shared monitor thread interrupts any tick that runs past the kill
 * deadline, so {@code while(true){}} cannot hang the game.
 *
 * <p>Side effects the app asks for (toast, shake, resize, sound) are handed to {@link Listener},
 * which the window runs on the client thread.
 */
public final class ScriptHost {

    public enum State { STARTING, RUNNING, NOT_RESPONDING, CRASHED, NO_ENGINE, STOPPED }

    public interface Listener {
        void toast(String title, String text);
        void notResponding();
        void shake(float intensity, int durationMs);
        void resize(int width, int height);
        void sound(String id);
        /** {@code kvr.pin(title, text)}: note window in front of the player (Body anchor, gear menu, ×). */
        void pin(String title, String text);
        /** kvr.window.lock / setPublic / anchor: key = lock|public|anchor (same switches as the gear menu). */
        void windowOption(String key, String value);
    }

    private static final Map<ScriptHost, Boolean> RUNNING = new ConcurrentHashMap<>();
    /** main.js + prelude must finish evaluating within this (top-level while(true) is killed too). */
    static final long BOOT_LIMIT_NANOS = 5_000_000_000L;
    /** First seconds after boot: the JVM is still warming the wasm interpreter, so ticks are slow. */
    static final long GRACE_NANOS = 3_000_000_000L;
    /** Kill deadline during the grace period (instead of 4x budget, which is 5 ms at T5). */
    static final long GRACE_KILL_NANOS = 250_000_000L;
    private static final ScheduledExecutorService MONITOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "KVR-Script-Watchdog");
        t.setDaemon(true);
        return t;
    });

    static {
        MONITOR.scheduleAtFixedRate(ScriptHost::checkAll, 1, 1, TimeUnit.MILLISECONDS);
    }

    private final VrApp app;
    private final String prelude;
    private final ScriptClock clock;
    private final Watchdog watchdog;
    private final Listener listener;
    private final Queue<String> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicReference<UiNode> ui = new AtomicReference<>();

    private volatile State state = State.STARTING;
    private volatile String error = "";
    private volatile long tickStart;
    private volatile long bootDeadline;
    private volatile long graceUntil;
    private volatile boolean killed;
    private volatile boolean stopped;
    @Nullable private volatile JsEngine engine;
    @Nullable private Thread thread;

    public ScriptHost(VrApp app, String prelude, int cpuTier, Listener listener) {
        this.app = app;
        this.prelude = prelude;
        this.clock = new ScriptClock(cpuTier);
        this.watchdog = new Watchdog(clock);
        this.listener = listener;
    }

    public void start() {
        if (!ScriptEngines.installed()) {
            state = State.NO_ENGINE;
            return;
        }
        // the engine itself is created on the script thread: a wasm instance costs ~15-20 ms;
        // 16 MiB stack: headroom for the wasm interpreter on deep JS call chains
        Thread t = new Thread(null, this::run, "KVR-Script-" + app.id(), 16L << 20);
        t.setDaemon(true);
        thread = t;
        RUNNING.put(this, Boolean.TRUE);
        t.start();
    }

    public void stop() {
        stopped = true;
        state = State.STOPPED;
        RUNNING.remove(this);
        Thread t = thread;
        if (t != null) LockSupport.unpark(t);
        JsEngine e = engine;
        if (e != null) e.interrupt();
    }

    /** UI event from the window (client thread): {@code {"type":"click","id":4}}. */
    public void post(String eventJson) {
        if (inbox.size() < 128) inbox.add(eventJson);
    }

    public State state() { return state; }

    /** The live host of an app (Messenger delivery etc.), if it is running. */
    public static Optional<ScriptHost> running(String appId) {
        for (ScriptHost h : RUNNING.keySet()) {
            if (h.app.id().equals(appId) && !h.stopped) return Optional.of(h);
        }
        return Optional.empty();
    }
    public String error() { return error; }
    @Nullable public UiNode ui() { return ui.get(); }
    public VrApp app() { return app; }

    // --- script thread -------------------------------------------------------------------------

    private void run() {
        JsEngine e;
        try {
            e = ScriptEngines.create();
        } catch (RuntimeException | LinkageError ex) {
            // LinkageError = Chicory jars not shipped inside the mod jar (include(...) missing)
            crash("JS engine failed to start: " + ex);
            RUNNING.remove(this);
            return;
        }
        if (e == null) {
            state = State.NO_ENGINE;
            RUNNING.remove(this);
            return;
        }
        engine = e;
        try {
            if (stopped) return; // window closed while the engine was starting
            e.expose("__kvr_host", this::host);
            bootDeadline = System.nanoTime() + BOOT_LIMIT_NANOS;
            e.eval(prelude, "kvr:prelude.js");
            e.eval(ModuleShim.apply(app.main()), app.id() + "/main.js");
            bootDeadline = 0;
            graceUntil = System.nanoTime() + GRACE_NANOS;
            state = State.RUNNING;
            long t0 = System.nanoTime();
            while (!stopped) {
                long now = System.nanoTime();
                int due = clock.ticksDue(now);
                if (due == 0) {
                    LockSupport.parkNanos(Math.min(clock.intervalNanos(), 2_000_000L));
                    continue;
                }
                String ev;
                while ((ev = inbox.poll()) != null) {
                    String event = ev;
                    timed(() -> e.call("__kvr_event", event));
                }
                for (int i = 0; i < due && !stopped; i++) {
                    String ms = Long.toString((now - t0) / 1_000_000L);
                    timed(() -> e.call("__kvr_tick", ms));
                }
            }
        } catch (JsEngine.JsException ex) {
            if (!stopped) crash(killed ? killMessage() : ex.getMessage());
        } catch (RuntimeException ex) {
            if (!stopped) crash(ex.toString());
        } finally {
            RUNNING.remove(this);
            e.close();
        }
    }

    private String killMessage() {
        if (state == State.STARTING) return "Watchdog: app did not start within " + BOOT_LIMIT_NANOS / 1_000_000_000L + " s";
        return "Watchdog: script exceeded " + (watchdog.killAfterNanos() / 1_000_000.0) + " ms in one tick";
    }

    private interface Call {
        void run() throws JsEngine.JsException;
    }

    private void timed(Call call) throws JsEngine.JsException {
        long start = System.nanoTime();
        tickStart = start;
        try {
            call.run();
        } finally {
            tickStart = 0;
        }
        if (System.nanoTime() < graceUntil) return; // warm-up: do not judge the interpreter yet
        switch (watchdog.record(System.nanoTime() - start)) {
            case NOT_RESPONDING -> {
                if (state != State.NOT_RESPONDING) listener.notResponding();
                state = State.NOT_RESPONDING;
            }
            case OK -> {
                if (state == State.NOT_RESPONDING) state = State.RUNNING;
            }
            default -> {}
        }
    }

    private void crash(String message) {
        error = message == null ? "error" : message;
        state = State.CRASHED;
        KVR.logger().warn("KVR app {} crashed: {}", app.id(), error);
    }

    /**
     * Host bridge, called on the script thread: args[0] = op (JSON string), args[1] = payload (JSON).
     * Returns a JSON value; most ops return {@code null}, getters return data.
     */
    private String host(String[] args) {
        String op = args.length > 0 ? JsonParser.parseString(args[0]).getAsString() : "";
        String payload = args.length > 1 ? args[1] : "null";
        switch (op) {
            case "ui.commit" -> ui.set(UiNode.parse(payload));
            case "toast" -> {
                var o = JsonParser.parseString(payload).getAsJsonObject();
                listener.toast(o.has("title") ? o.get("title").getAsString() : app.name(), o.has("text") ? o.get("text").getAsString() : "");
            }
            case "log" -> KVR.logger().info("[{}] {}", app.id(), JsonParser.parseString(payload).getAsString());
            case "window.shake" -> {
                var o = JsonParser.parseString(payload).getAsJsonObject();
                listener.shake(o.has("intensity") ? o.get("intensity").getAsFloat() : 4f, o.has("duration") ? o.get("duration").getAsInt() : 800);
            }
            case "window.resize" -> {
                var o = JsonParser.parseString(payload).getAsJsonObject();
                listener.resize(o.has("width") ? o.get("width").getAsInt() : 0, o.has("height") ? o.get("height").getAsInt() : 0);
            }
            case "window.pin" -> {
                var o = JsonParser.parseString(payload).getAsJsonObject();
                String title = str(o, "title", app.name());
                String text = str(o, "text", "");
                listener.pin(title.length() > 40 ? title.substring(0, 40) : title, text.length() > 2000 ? text.substring(0, 2000) : text);
            }
            case "window.option" -> {
                var o = JsonParser.parseString(payload).getAsJsonObject();
                listener.windowOption(str(o, "key", ""), str(o, "value", ""));
            }
            case "sound.play" -> listener.sound(JsonParser.parseString(payload).getAsString());
            case "fs" -> fs(JsonParser.parseString(payload).getAsJsonObject());
            case "player.coords" -> {
                ClientInfo.Snapshot s = ClientInfo.get();
                JsonObject o = new JsonObject();
                o.addProperty("x", s.x());
                o.addProperty("y", s.y());
                o.addProperty("z", s.z());
                o.addProperty("dimension", s.dimension());
                return o.toString();
            }
            case "player.name" -> {
                return new JsonPrimitive(ClientInfo.get().name()).toString();
            }
            case "client.lang" -> {
                return new JsonPrimitive(ClientInfo.get().language()).toString();
            }
            case "players.online" -> {
                JsonArray arr = new JsonArray();
                ClientInfo.get().online().forEach(arr::add);
                return arr.toString();
            }
            case "msg.send" -> msgSend(JsonParser.parseString(payload).getAsJsonObject());
            case "msg.pending" -> {
                return com.kasperstudios.kvr.messenger.client.MessengerClient.drainPending();
            }
            default -> KVR.logger().debug("[{}] unknown host op {}", app.id(), op);
        }
        return "null";
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    /** kvr.msg.send: relayed by the server; the result comes back as a {@code {type:'msg_ack'}} event. */
    private void msgSend(JsonObject o) {
        int req = o.get("req").getAsInt();
        com.kasperstudios.kvr.messenger.client.MessengerClient.send(str(o, "to", ""), str(o, "text", ""), str(o, "attach", ""), ack -> {
            JsonObject e = new JsonObject();
            e.addProperty("type", "msg_ack");
            e.addProperty("req", req);
            e.addProperty("ok", ack.ok());
            e.addProperty("error", ack.error());
            post(e.toString());
        });
    }

    /** kvr.fs: async round trip to the server VFS; the reply comes back as a {@code {type:'fs'}} event. */
    private void fs(JsonObject o) {
        int req = o.get("req").getAsInt();
        com.kasperstudios.kvr.vfs.VfsProtocol.Op op;
        try {
            op = com.kasperstudios.kvr.vfs.VfsProtocol.Op.valueOf(o.get("op").getAsString());
        } catch (IllegalArgumentException e) {
            return;
        }
        String path = o.has("path") ? o.get("path").getAsString() : "/";
        String text = o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString() : "";
        com.kasperstudios.kvr.vfs.client.VfsClient.request(op, app.id(), path, text, r -> {
            JsonObject e = new JsonObject();
            e.addProperty("type", "fs");
            e.addProperty("req", req);
            e.addProperty("ok", r.ok());
            e.addProperty("error", r.error());
            e.addProperty("data", r.data());
            post(e.toString());
        });
    }

    // --- watchdog monitor ----------------------------------------------------------------------

    private static void checkAll() {
        long now = System.nanoTime();
        for (ScriptHost h : RUNNING.keySet()) {
            if (h.killed) continue;
            long start = h.tickStart;
            long boot = h.bootDeadline;
            long limit = now < h.graceUntil ? Math.max(h.watchdog.killAfterNanos(), GRACE_KILL_NANOS) : h.watchdog.killAfterNanos();
            if ((start != 0 && now - start > limit) || (boot != 0 && now > boot)) {
                h.killed = true;
                JsEngine e = h.engine;
                if (e != null) e.interrupt();
            }
        }
    }
}
