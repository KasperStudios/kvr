package com.kasperstudios.kvr.script;

import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Where JS engines come from. 5a installs the backend chosen by the spike:
 * <pre>{@code ScriptEngines.install(ChicoryJsEngine::new);}</pre>
 * Until then {@link #create()} returns null and app windows show "engine not installed".
 */
public final class ScriptEngines {

    private static Supplier<JsEngine> factory;

    private ScriptEngines() {}

    public static void install(Supplier<JsEngine> engineFactory) {
        factory = engineFactory;
    }

    @Nullable
    public static JsEngine create() {
        return factory == null ? null : factory.get();
    }
}
