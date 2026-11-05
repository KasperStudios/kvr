package com.kasperstudios.kvr.script;

/**
 * The JS runtime behind KVR OS, deliberately tiny so the backend can be swapped: QuickJS-on-WASM
 * (Chicory, Creator decision 5a) or native QuickJS via JNI as the fallback. One engine instance =
 * one running .vrapp; everything above (windows, kvr.* bindings, VFS) talks only to this.
 */
public interface JsEngine extends AutoCloseable {

    /** Run a script (module body); errors come back as {@link JsException} with file:line. */
    void eval(String source, String fileName) throws JsException;

    /**
     * Call a global function by name with JSON-serialisable arguments; returns the result as a
     * JSON string ({@code "null"} for undefined). Used for event dispatch: tick, click, kvr.on(...).
     */
    String call(String function, String... jsonArgs) throws JsException;

    /** Register a host function {@code name(...)} visible to scripts (kvr.* bindings). */
    void expose(String name, HostFunction fn);

    /** Ask the running script to stop as soon as possible (watchdog kill). Thread-safe. */
    void interrupt();

    /** Bytes of script heap in use, for the RAM limit and the task manager. */
    long memoryUsed();

    @Override
    void close();

    @FunctionalInterface
    interface HostFunction {
        /** @param jsonArgs arguments as JSON; @return JSON result */
        String invoke(String[] jsonArgs) throws JsException;
    }

    final class JsException extends Exception {
        public JsException(String message) {
            super(message);
        }
    }
}
