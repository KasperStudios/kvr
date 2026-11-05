package com.kasperstudios.kvr.script.chicory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.script.BuiltinApps;
import com.kasperstudios.kvr.script.JsEngine;
import com.kasperstudios.kvr.script.ModuleShim;
import com.kasperstudios.kvr.script.ScriptEngines;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Needs {@code src/main/resources/kvr/kvrjs.wasm} (native/kvrjs/build.sh); skipped without it.
 * Engine work runs on a 16 MiB-stack thread, exactly like {@code ScriptHost}'s script threads.
 * {@link #benchmark()} prints the spike table from BLOCK5_PLAN.md — paste its output into the report.
 */
class ChicoryJsEngineTest {

    @BeforeAll
    static void needsWasm() {
        assumeTrue(ChicoryJsEngine.available(), "kvrjs.wasm not built yet (native/kvrjs/build.sh)");
    }

    @Test
    void selfTestUsedByInstallPasses() throws Exception {
        onScriptThread(() -> {
            long t0 = System.nanoTime();
            ChicoryJsEngine.selfTest();
            System.out.printf("[kvr-bench] install() self-test (parse cached + instance + eval/call) %.1f ms%n", (System.nanoTime() - t0) / 1e6);
        });
    }

    @Test
    void uninstallLeavesAReasonForAppWindows() {
        ScriptEngines.uninstall("missing kvr/kvrjs.wasm");
        assertFalse(ScriptEngines.installed());
        assertEquals("missing kvr/kvrjs.wasm", ScriptEngines.problem());
        ScriptEngines.install(ChicoryJsEngine::new);
        assertTrue(ScriptEngines.installed());
        assertEquals("", ScriptEngines.problem());
        ScriptEngines.uninstall("");
    }

    @Test
    void evalAndCallRoundTripJson() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                e.eval("globalThis.add = (a, b) => a + b; globalThis.obj = (o) => ({ x: [1, 'ы'], echo: o.k }); globalThis.nothing = () => {};", "t.js");
                assertEquals("5", e.call("add", "2", "3"));
                assertEquals("{\"x\":[1,\"ы\"],\"echo\":\"v\"}", e.call("obj", "{\"k\":\"v\"}"));
                assertEquals("null", e.call("nothing"));
            }
        });
    }

    @Test
    void hostFunctionsReceiveJsonAndReturnParsedValues() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                e.expose("__h", args -> "{\"op\":" + args[0] + ",\"a\":" + JsonParser.parseString(args[1]).getAsJsonObject().get("a") + "}");
                e.expose("__bad", args -> {
                    throw new JsEngine.JsException("nope");
                });
                e.eval("""
                    globalThis.r = __h('ping', { a: 7 });
                    let caught = '';
                    try { __bad(); } catch (err) { caught = String(err.message); }
                    globalThis.get = () => [r.op, r.a, caught];
                    """, "t.js");
                String out = e.call("get");
                assertTrue(out.startsWith("[\"ping\",7,"), out);
                assertTrue(out.contains("nope"), out);
            }
        });
    }

    @Test
    void promiseJobsRunAfterEveryEvalAndCall() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                e.eval("globalThis.v = 0; Promise.resolve().then(() => { v = 1; }); globalThis.get = () => v;"
                    + "globalThis.bump = () => { Promise.resolve().then(() => { v += 10; }); };", "t.js");
                assertEquals("1", e.call("get"));
                e.call("bump");
                assertEquals("11", e.call("get"));
            }
        });
    }

    @Test
    void errorsCarryFileAndLine() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                JsEngine.JsException ex = assertThrows(JsEngine.JsException.class,
                    () -> e.eval("\n\nfunction f() { throw new Error('boom'); }\nf();", "app/main.js"));
                assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
                assertTrue(ex.getMessage().contains("main.js"), ex.getMessage());
            }
        });
    }

    @Test
    void interruptStopsAnInfiniteLoop() throws Exception {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        try {
            onScriptThread(() -> {
                try (JsEngine e = new ChicoryJsEngine()) {
                    long t0 = System.nanoTime();
                    timer.schedule(e::interrupt, 50, TimeUnit.MILLISECONDS);
                    assertThrows(JsEngine.JsException.class, () -> e.eval("while (true) {}", "loop.js"));
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    System.out.println("[kvr-bench] while(true) stopped " + (ms - 50) + " ms after interrupt()");
                    assertTrue(ms < 1050, "took " + ms + " ms");
                }
            });
        } finally {
            timer.shutdownNow();
        }
    }

    /** A late Thread.interrupt() (watchdog race) must not break the next call of a healthy engine. */
    @Test
    void strayThreadInterruptDoesNotBreakTheEngine() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                e.eval("globalThis.sum = (n) => { let s = 0; for (let i = 0; i < n; i++) s += i; return s; };", "t.js");
                Thread.currentThread().interrupt();
                assertEquals("4950", e.call("sum", "100"));
                assertEquals("499500", e.call("sum", "1000"));
            }
        });
    }

    @Test
    void memoryLimitIsAJsErrorNotAJvmCrash() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                assertThrows(JsEngine.JsException.class,
                    () -> e.eval("const a = []; for (;;) a.push('x'.repeat(100000) + a.length);", "oom.js"));
            }
        });
    }

    /** Ideal: QuickJS's own limit fires (RangeError). Acceptable: a wasm trap -> JsException. Never a JVM crash. */
    @Test
    void deepRecursionNeverCrashesTheJvm() throws Exception {
        onScriptThread(() -> {
            try (JsEngine e = new ChicoryJsEngine()) {
                try {
                    e.eval("let r = ''; try { (function f() { f(); })(); } catch (err) { r = err.name; } globalThis.get = () => r;", "rec.js");
                    assertEquals("\"RangeError\"", e.call("get"));
                    System.out.println("[kvr-bench] deep recursion -> RangeError (stack limit works)");
                } catch (JsEngine.JsException ex) {
                    System.out.println("[kvr-bench] deep recursion -> engine error, no RangeError: " + ex.getMessage());
                }
            }
        });
    }

    /**
     * Every bundled app boots on the real prelude, commits a UI on its first tick and survives a
     * click on its first button (Hello must count it).
     */
    @Test
    void bundledAppsBootAndCommitUi() throws Exception {
        String prelude = resource("/assets/kvr/kvr_os/prelude.js");
        assertEquals(List.of("hello", "kav", "notes", "calc", "messenger"), BuiltinApps.SYSTEM_APPS);
        for (String app : BuiltinApps.SYSTEM_APPS) {
            onScriptThread(() -> {
                List<String> commits = new ArrayList<>();
                try (JsEngine e = new ChicoryJsEngine()) {
                    e.expose("__kvr_host", args -> fakeHost(args, commits));
                    e.eval(prelude, "kvr:prelude.js");
                    e.eval(ModuleShim.apply(resource("/assets/kvr/apps/" + app + "/main.js")), app + "/main.js");
                    e.call("__kvr_tick", "100");
                    assertFalse(commits.isEmpty(), app + ": no ui.commit on first tick");
                    String ui = commits.get(commits.size() - 1);
                    assertTrue(ui.contains("\"type\":\"window\""), app);

                    Integer button = firstButton(JsonParser.parseString(ui));
                    assertNotNull(button, app + ": no button in the first UI");
                    e.call("__kvr_event", "{\"type\":\"click\",\"id\":" + button + "}");
                    e.call("__kvr_tick", "200");
                    if (app.equals("hello")) assertTrue(commits.get(commits.size() - 1).contains("Clicks: 1"), commits.get(commits.size() - 1));
                    if (app.equals("calc")) {
                        e.eval("globalThis.__t = () => [evaluate('3*64+12'), evaluate('2^(1+2)'), evaluate('-(4-6)*1,5')];", "t.js");
                        assertEquals("[204,8,3]", e.call("__t"));
                    }
                    System.out.printf("[kvr-bench] app %-9s boots, commits UI, handles a click; heap %.2f MB%n", app, e.memoryUsed() / 1048576.0);
                }
            });
        }
    }

    /** Prints the BLOCK5_PLAN thresholds table. Not a pass/fail gate; send the numbers. */
    @Test
    void benchmark() throws Exception {
        String prelude = resource("/assets/kvr/kvr_os/prelude.js");
        onScriptThread(() -> {
            ChicoryJsEngine.module(); // parse cost reported separately below
            long[] cold = new long[5];
            for (int i = 0; i < cold.length; i++) {
                long t0 = System.nanoTime();
                try (JsEngine e = new ChicoryJsEngine()) {
                    e.eval("", "empty.js");
                }
                cold[i] = System.nanoTime() - t0;
            }
            try (JsEngine e = new ChicoryJsEngine()) {
                int[] hostCalls = {0};
                e.expose("__h", args -> {
                    hostCalls[0]++;
                    return "null";
                });
                e.eval("let n = 0; globalThis.tick = (s) => { n++; return { n, s: s + '!' }; };"
                    + "globalThis.kav = () => { for (let i = 0; i < 200; i++) __h('op', { i }); return 200; };", "bench.js");
                double tick = median(() -> e.call("tick", "\"abc\""), 3000);
                double kav = median(() -> e.call("kav"), 300);
                List<String> commits = new ArrayList<>();
                try (JsEngine app = new ChicoryJsEngine()) {
                    app.expose("__kvr_host", args -> fakeHost(args, commits));
                    app.eval(prelude, "kvr:prelude.js");
                    app.eval(ModuleShim.apply(resource("/assets/kvr/apps/hello/main.js")), "hello/main.js");
                    double helloTick = median(() -> app.call("__kvr_tick", "1000"), 1000);
                    System.out.printf("[kvr-bench] hello app tick (timers + ui.commit)  median %.3f ms%n", helloTick);
                    System.out.printf("[kvr-bench] memory per app instance (JS heap)    %.2f MB%n", app.memoryUsed() / 1048576.0);
                }
                long[] sorted = cold.clone();
                Arrays.sort(sorted);
                System.out.printf("[kvr-bench] cold start (instance + eval empty)  median %.1f ms (limit 300)%n", sorted[2] / 1e6);
                System.out.printf("[kvr-bench] tick() with small JSON              median %.3f ms (limit 0.3)%n", tick);
                System.out.printf("[kvr-bench] 200 host calls in one tick          median %.3f ms (limit 1.25)%n", kav);
                System.out.printf("[kvr-bench] host calls counted                  %d%n", hostCalls[0]);
            }
        });
        System.out.printf("[kvr-bench] java %s, %s %s%n", System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private interface Op {
        void run() throws Exception;
    }

    /** Same thread shape as ScriptHost: own thread, 16 MiB stack. Rethrows failures and assertion errors. */
    private static void onScriptThread(Op op) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(null, () -> {
            try {
                op.run();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "KVR-Script-test", 16L << 20);
        t.start();
        t.join(TimeUnit.MINUTES.toMillis(5));
        assertFalse(t.isAlive(), "script thread did not finish in 5 min");
        Throwable ex = failure.get();
        if (ex instanceof Exception x) throw x;
        if (ex instanceof Error x) throw x;
    }

    private static double median(Op op, int runs) throws Exception {
        for (int i = 0; i < runs / 3; i++) op.run(); // warm-up
        long[] t = new long[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            op.run();
            t[i] = System.nanoTime() - t0;
        }
        Arrays.sort(t);
        return t[runs / 2] / 1e6;
    }

    /** Depth-first: id of the first {@code type: "button"} node in a committed UI tree. */
    private static Integer firstButton(JsonElement node) {
        if (!node.isJsonObject()) return null;
        JsonObject o = node.getAsJsonObject();
        if (o.has("type") && "button".equals(o.get("type").getAsString()) && o.has("id")) return o.get("id").getAsInt();
        if (o.has("children") && o.get("children").isJsonArray()) {
            for (JsonElement c : o.getAsJsonArray("children")) {
                Integer id = firstButton(c);
                if (id != null) return id;
            }
        }
        return null;
    }

    /** Minimal ScriptHost.host stand-in: records commits, answers the getters the apps call. */
    private static String fakeHost(String[] args, List<String> commits) {
        String op = JsonParser.parseString(args[0]).getAsString();
        return switch (op) {
            case "ui.commit" -> {
                commits.add(args[1]);
                yield "null";
            }
            case "client.lang" -> "\"ru_ru\"";
            case "player.name" -> "\"Steve\"";
            case "player.coords" -> "{\"x\":1,\"y\":64,\"z\":-3,\"dimension\":\"minecraft:overworld\"}";
            case "players.online", "msg.pending" -> "[]";
            default -> "null";
        };
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ChicoryJsEngineTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
