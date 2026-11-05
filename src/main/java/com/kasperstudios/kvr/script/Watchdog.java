package com.kasperstudios.kvr.script;

/**
 * Watchdog (SPEC 5.1, Creator decision): one script tick may use at most 25% of its CPU tier's tick
 * interval (T1: 25 ms of 100, T5: 1.25 ms of 5).
 * <ul>
 *   <li>over budget 3 ticks in a row → {@link Verdict#NOT_RESPONDING} (toast: close the app?);</li>
 *   <li>a single tick over 4× the budget → {@link Verdict#KILL} — the host calls
 *       {@code JsEngine.interrupt()} right away (infinite loops never get a second tick).</li>
 * </ul>
 * Pure logic, fed with measured durations; unit-tested.
 */
public final class Watchdog {

    public enum Verdict { OK, SLOW, NOT_RESPONDING, KILL }

    public static final double BUDGET_SHARE = 0.25;
    public static final int STRIKES = 3;
    public static final int KILL_FACTOR = 4;

    private final long budgetNanos;
    private int strikes;

    public Watchdog(ScriptClock clock) {
        this.budgetNanos = (long) (clock.intervalNanos() * BUDGET_SHARE);
    }

    public long budgetNanos() {
        return budgetNanos;
    }

    /** Deadline for the in-flight tick after which the host must interrupt the engine. */
    public long killAfterNanos() {
        return budgetNanos * KILL_FACTOR;
    }

    public Verdict record(long tickNanos) {
        if (tickNanos > killAfterNanos()) {
            strikes = 0;
            return Verdict.KILL;
        }
        if (tickNanos <= budgetNanos) {
            strikes = 0;
            return Verdict.OK;
        }
        strikes++;
        if (strikes >= STRIKES) {
            strikes = 0;
            return Verdict.NOT_RESPONDING;
        }
        return Verdict.SLOW;
    }
}
