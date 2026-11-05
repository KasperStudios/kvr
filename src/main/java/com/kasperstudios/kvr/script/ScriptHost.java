package com.kasperstudios.kvr.script;

import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.script.ui.UiNode;
import java.util.Map;
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
    }

    private static final Map<ScriptHost, Boolean> RUNNING = new ConcurrentHashMap<>();
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
        JsEngine e = ScriptEngines.create();
        if (e == null) {
            state = State.NO_ENGINE;
            return;
        }
        engine = e;
        Thread t = new Thread(this::run, "KVR-Script-" + app.id());
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
        if (inbox.size() < 64) inbox.add(eventJson);
    }

    public State state() { return state; }
    public String error() { return error; }
    @Nullable public UiNode ui() { return ui.get(); }
    public VrApp app() { return app; }

    // --- script thread -------------------------------------------------------------------------

    private void run() {
        JsEngine e = engine;
        try {
            e.expose("__kvr_host", this::host);
            e.eval(prelude, "kvr:prelude.js");
            e.eval(ModuleShim.apply(app.main()), app.id() + "/main.js");
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
            if (!stopped) crash(killed ? "Watchdog: script exceeded " + (watchdog.killAfterNanos() / 1_000_000.0) + " ms in one tick" : ex.getMessage());
        } catch (RuntimeException ex) {
            if (!stopped) crash(ex.toString());
        } finally {
            RUNNING.remove(this);
            e.close();
        }
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

    /** Host bridge, called on the script thread: args[0] = op (JSON string), args[1] = payload (JSON). */
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
            case "sound.play" -> listener.sound(JsonParser.parseString(payload).getAsString());
            default -> KVR.logger().debug("[{}] unknown host op {}", app.id(), op);
        }
        return "null";
    }

    // --- watchdog monitor ----------------------------------------------------------------------

    private static void checkAll() {
        long now = System.nanoTime();
        for (ScriptHost h : RUNNING.keySet()) {
            long start = h.tickStart;
            if (start != 0 && now - start > h.watchdog.killAfterNanos() && !h.killed) {
                h.killed = true;
                JsEngine e = h.engine;
                if (e != null) e.interrupt();
            }
        }
    }
}
