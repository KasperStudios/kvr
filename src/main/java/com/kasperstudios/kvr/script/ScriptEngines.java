package com.kasperstudios.kvr.script;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Where JS engines come from. The client init installs the Chicory backend:
 * <pre>{@code ChicoryJsEngine.install();   // = ScriptEngines.install(ChicoryJsEngine::new) + preload + self-test}</pre>
 * Until then (or if the self-test failed) {@link #create()} returns null and app windows show
 * "engine not installed" — with {@link #problem()} as the reason when there is one.
 */
public final class ScriptEngines {

    private static volatile Supplier<JsEngine> factory;
    private static volatile String problem = "";

    private ScriptEngines() {}

    public static void install(Supplier<JsEngine> engineFactory) {
        factory = engineFactory;
        problem = "";
    }

    /** The backend turned out to be unusable (missing or broken wasm): apps show {@code reason}. */
    public static void uninstall(String reason) {
        factory = null;
        problem = reason == null ? "" : reason;
    }

    public static boolean installed() {
        return factory != null;
    }

    /** Why there is no engine; empty if it was simply never installed. */
    public static String problem() {
        return problem;
    }

    /** Can be slow (wasm instance ~15-20 ms, first call parses the module): call on the script thread. */
    @Nullable
    public static JsEngine create() {
        Supplier<JsEngine> f = factory;
        return f == null ? null : f.get();
    }
}
