package com.kasperstudios.kvr.script.chicory;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.script.JsEngine;
import com.kasperstudios.kvr.script.ScriptEngines;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link JsEngine} on QuickJS-ng compiled to WebAssembly ({@code native/kvrjs}), interpreted by
 * Chicory: pure JVM bytecode, no native libraries, one jar for every OS and both loaders.
 *
 * <p>The wasm module is parsed once and shared; every app gets its own {@link Instance} (its own
 * linear memory = its own sandbox, JS heap capped at {@link #MEMORY_LIMIT}). All Chicory API use is
 * confined to this class, so a Chicory upgrade touches one file.
 *
 * <p>Threading: one engine belongs to one script thread. Only {@link #interrupt()} and
 * {@link #memoryUsed()} are called from other threads.
 *
 * <p>Bootstrap ({@link #install()}): registers the factory at once, then a background thread parses
 * the module and runs a self-test (one real instance: init, eval, call with JSON, Promise queue).
 * If anything fails, the factory is removed again and every app window shows the exact reason
 * ({@code kvr.app.engine_failed}) instead of crashing one by one. {@link #status()} for diagnostics.
 *
 * <pre>{@code ChicoryJsEngine.install();   // client init, before KVRSpatial.initClient(...)}</pre>
 */
public final class ChicoryJsEngine implements JsEngine {

    public enum Status { NOT_INSTALLED, LOADING, READY, FAILED }

    /** Outside assets/ on purpose: a resource pack must not be able to swap the engine. */
    public static final String WASM_RESOURCE = "/kvr/kvrjs.wasm";
    static final int MEMORY_LIMIT = 24 << 20;
    /** Below the 1 MiB wasm stack from build.sh, so deep recursion is a JS RangeError, not a trap. */
    private static final int STACK_LIMIT = 896 << 10;
    private static final byte[] NONE = new byte[0];
    private static final Object LOCK = new Object();
    private static volatile WasmModule module;
    private static final java.util.concurrent.atomic.AtomicBoolean INSTALL_CALLED = new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile Status status = Status.NOT_INSTALLED;
    private static volatile String statusDetail = "";

    private final Map<String, JsEngine.HostFunction> hostFunctions = new HashMap<>();
    private final WasiPreview1 wasi;
    private final Instance instance;
    private final Memory memory;
    private final ExportFunction alloc;
    private final ExportFunction free;
    private final ExportFunction evalFn;
    private final ExportFunction callFn;
    private final ExportFunction exposeFn;
    private final ExportFunction outPtr;
    private final ExportFunction outLen;
    private final ExportFunction memUsed;

    private byte[] hostResult = NONE;
    private volatile boolean interrupted;
    @org.jetbrains.annotations.Nullable private volatile Thread running;
    private volatile long memoryUsed;
    private boolean broken;
    private boolean closed;
    private int calls;

    public ChicoryJsEngine() {
        WasmModule m = module();
        // no preopened directories, no stdio: WASI only supplies clocks and libc plumbing
        this.wasi = WasiPreview1.builder().withOptions(WasiOptions.builder().build()).build();
        com.dylibso.chicory.runtime.ImportValues imports = com.dylibso.chicory.runtime.ImportValues.builder()
            .addFunction(wasi.toHostFunctions())
            .addFunction(
                new com.dylibso.chicory.runtime.HostFunction("kvr", "host_call",
                    FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32, ValType.I32), List.of(ValType.I32)),
                    (inst, args) -> new long[] {hostCall(inst, (int) args[0], (int) args[1], (int) args[2], (int) args[3])}),
                new com.dylibso.chicory.runtime.HostFunction("kvr", "host_result",
                    FunctionType.of(List.of(ValType.I32), List.of()),
                    (inst, args) -> {
                        inst.memory().write((int) args[0], hostResult);
                        hostResult = NONE;
                        return null;
                    }),
                new com.dylibso.chicory.runtime.HostFunction("kvr", "interrupted",
                    FunctionType.of(List.of(), List.of(ValType.I32)),
                    (inst, args) -> new long[] {interrupted ? 1 : 0}))
            .build();
        this.instance = Instance.builder(m).withImportValues(imports).build();
        this.memory = instance.memory();
        try {
            instance.export("_initialize").apply(); // wasi reactor: libc constructors
        } catch (RuntimeException notReactor) {
            // built without -mexec-model=reactor: nothing to initialise
        }
        this.alloc = instance.export("kvr_alloc");
        this.free = instance.export("kvr_free");
        this.evalFn = instance.export("kvr_eval");
        this.callFn = instance.export("kvr_call");
        this.exposeFn = instance.export("kvr_expose");
        this.outPtr = instance.export("kvr_out_ptr");
        this.outLen = instance.export("kvr_out_len");
        this.memUsed = instance.export("kvr_memory_used");
        long rc = instance.export("kvr_init").apply(MEMORY_LIMIT, STACK_LIMIT)[0];
        if (rc != 0) throw new IllegalStateException("QuickJS init failed: " + rc);
        this.memoryUsed = memUsed.apply()[0];
    }

    // --- bootstrap -----------------------------------------------------------------------------

    public static boolean available() {
        return ChicoryJsEngine.class.getResource(WASM_RESOURCE) != null;
    }

    /**
     * Client init: installs the engine; parses the wasm and self-tests it in the background so the
     * first app opens fast. Safe to call more than once (second call is a no-op).
     */
    public static void install() {
        if (!INSTALL_CALLED.compareAndSet(false, true)) return;
        if (!available()) {
            fail("missing " + WASM_RESOURCE + " (build native/kvrjs/build.sh)", null);
            return;
        }
        status = Status.LOADING;
        ScriptEngines.install(ChicoryJsEngine::new);
        // same 16 MiB stack as script threads: the self-test runs a real instance
        Thread t = new Thread(null, ChicoryJsEngine::preload, "KVR-JS-Preload", 16L << 20);
        t.setDaemon(true);
        t.start();
    }

    static void preload() {
        try {
            long t0 = System.nanoTime();
            module();
            long t1 = System.nanoTime();
            selfTest();
            long t2 = System.nanoTime();
            statusDetail = String.format("parse %d ms, first instance + self-test %d ms", (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
            status = Status.READY;
            KVR.logger().info("KVR OS: QuickJS wasm ready ({})", statusDetail);
        } catch (JsException | RuntimeException | LinkageError e) {
            fail(e.toString(), e);
        }
    }

    /** One instance end to end: ABI + kvr_init, eval, call with a JSON argument, Promise job queue. */
    static void selfTest() throws JsException {
        try (ChicoryJsEngine e = new ChicoryJsEngine()) {
            e.eval("globalThis.__kvr_v = 0; Promise.resolve().then(() => { __kvr_v = 1; });"
                + "globalThis.__kvr_selftest = (o) => [o.a * 6, __kvr_v, 'ы'];", "kvr:selftest.js");
            String r = e.call("__kvr_selftest", "{\"a\":7}");
            if (!"[42,1,\"ы\"]".equals(r)) throw new IllegalStateException("self-test returned " + r + " (ABI mismatch between kvrjs.c and ChicoryJsEngine?)");
        }
    }

    private static void fail(String reason, @org.jetbrains.annotations.Nullable Throwable cause) {
        status = Status.FAILED;
        statusDetail = reason;
        ScriptEngines.uninstall(reason);
        if (cause == null) KVR.logger().warn("KVR OS: JS engine unavailable: {}", reason);
        else KVR.logger().error("KVR OS: JS engine failed to start, apps disabled: {}", reason, cause);
    }

    public static Status status() {
        return status;
    }

    /** Timings when READY, the error when FAILED. */
    public static String statusDetail() {
        return statusDetail;
    }

    static WasmModule module() {
        WasmModule m = module;
        if (m != null) return m;
        synchronized (LOCK) {
            if (module == null) {
                try (InputStream in = ChicoryJsEngine.class.getResourceAsStream(WASM_RESOURCE)) {
                    if (in == null) throw new IllegalStateException("missing " + WASM_RESOURCE);
                    module = Parser.parse(in);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return module;
        }
    }

    // --- JsEngine ------------------------------------------------------------------------------

    @Override
    public void eval(String source, String fileName) throws JsException {
        byte[] src = utf8(source);
        byte[] name = utf8(fileName);
        invoke(() -> {
            int ps = put(src);
            int pn = put(name);
            try {
                return (int) evalFn.apply(ps, src.length, pn, name.length)[0];
            } finally {
                free.apply(ps);
                free.apply(pn);
            }
        });
    }

    @Override
    public String call(String function, String... jsonArgs) throws JsException {
        byte[] fn = utf8(function);
        byte[] args = utf8("[" + String.join(",", jsonArgs) + "]");
        return invoke(() -> {
            int pf = put(fn);
            int pa = put(args);
            try {
                return (int) callFn.apply(pf, fn.length, pa, args.length)[0];
            } finally {
                free.apply(pf);
                free.apply(pa);
            }
        });
    }

    @Override
    public void expose(String name, JsEngine.HostFunction fn) {
        hostFunctions.put(name, fn);
        byte[] n = utf8(name);
        try {
            invoke(() -> {
                int pn = put(n);
                try {
                    return (int) exposeFn.apply(pn, n.length)[0];
                } finally {
                    free.apply(pn);
                }
            });
        } catch (JsException e) {
            throw new IllegalStateException("expose " + name + ": " + e.getMessage(), e);
        }
    }

    /** Watchdog kill: QuickJS polls {@code kvr.interrupted} and throws an uncatchable error. */
    @Override
    public void interrupt() {
        interrupted = true;
        Thread t = running;
        if (t != null) t.interrupt(); // fallback for long C loops that never poll (Chicory checks the flag)
    }

    @Override
    public long memoryUsed() {
        return memoryUsed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            wasi.close();
        } catch (Exception ignored) {
            // nothing open: no preopens, no stdio
        }
    }

    // --- plumbing ------------------------------------------------------------------------------

    private interface WasmOp {
        int run() throws JsException;
    }

    private String invoke(WasmOp op) throws JsException {
        if (closed) throw new JsException("engine closed");
        if (broken) throw new JsException("engine stopped after a fatal error");
        if (interrupted) throw new JsException("interrupted");
        // a late Thread.interrupt() from an earlier watchdog race must not trap this call:
        // real kills always set the volatile flag above first
        Thread.interrupted();
        running = Thread.currentThread();
        try {
            int status = op.run();
            String result = out();
            if (status != 0) throw new JsException(result.isEmpty() ? "error" : result);
            if ((++calls & 63) == 0) memoryUsed = memUsed.apply()[0];
            return result;
        } catch (RuntimeException | StackOverflowError e) {
            broken = true; // trap, Chicory interruption or JVM stack overflow mid-instruction: instance state is undefined
            throw new JsException(interrupted ? "interrupted" : "wasm trap: " + e);
        } finally {
            running = null;
            Thread.interrupted(); // drop a late interrupt() so the script thread's park() behaves
        }
    }

    /** Copies bytes + NUL into script memory; the caller frees. */
    private int put(byte[] bytes) throws JsException {
        int p = (int) alloc.apply(bytes.length + 1)[0];
        if (p == 0) throw new JsException("InternalError: out of script memory");
        memory.write(p, bytes);
        memory.writeByte(p + bytes.length, (byte) 0);
        return p;
    }

    private String out() {
        int p = (int) outPtr.apply()[0];
        int n = (int) outLen.apply()[0];
        return p == 0 || n <= 0 ? "" : new String(memory.readBytes(p, n), StandardCharsets.UTF_8);
    }

    /** kvr.host_call import: args arrive as a JSON array, each element is handed over as JSON. */
    private int hostCall(Instance inst, int namePtr, int nameLen, int argsPtr, int argsLen) {
        Memory mem = inst.memory();
        String name = new String(mem.readBytes(namePtr, nameLen), StandardCharsets.UTF_8);
        try {
            JsEngine.HostFunction fn = hostFunctions.get(name);
            if (fn == null) throw new JsException("no host function " + name);
            JsonArray arr = JsonParser.parseString(new String(mem.readBytes(argsPtr, argsLen), StandardCharsets.UTF_8)).getAsJsonArray();
            String[] args = new String[arr.size()];
            for (int i = 0; i < args.length; i++) args[i] = arr.get(i).toString();
            String result = fn.invoke(args);
            hostResult = utf8(result == null ? "null" : result);
            return hostResult.length;
        } catch (JsException | RuntimeException e) {
            // becomes a catchable InternalError in JS; the engine keeps running
            hostResult = utf8(name + ": " + e.getMessage());
            return -(hostResult.length + 1);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
