package com.kasperstudios.kvr.script;

/**
 * Script tick rate from the CPU tier (SPEC 2.5) on a fixed-step clock. Frames call
 * {@link #ticksDue(long)}; the clock never catches up more than {@link #MAX_CATCH_UP} ticks at once,
 * so a lag spike cannot snowball into a long burst of script work.
 */
public final class ScriptClock {

    private static final int[] CPU_HZ = {10, 20, 50, 100, 200};
    public static final int MAX_CATCH_UP = 3;

    private final long intervalNanos;
    private long next = -1;

    public ScriptClock(int cpuTier) {
        this.intervalNanos = 1_000_000_000L / hz(cpuTier);
    }

    public static int hz(int cpuTier) {
        if (cpuTier < 1 || cpuTier > CPU_HZ.length) throw new IllegalArgumentException("cpu tier 1..5, got " + cpuTier);
        return CPU_HZ[cpuTier - 1];
    }

    public long intervalNanos() {
        return intervalNanos;
    }

    /** How many script ticks to run now; drops the backlog beyond {@link #MAX_CATCH_UP}. */
    public int ticksDue(long nowNanos) {
        if (next < 0) {
            next = nowNanos + intervalNanos;
            return 1;
        }
        if (nowNanos < next) return 0;
        long due = (nowNanos - next) / intervalNanos + 1;
        int run = (int) Math.min(due, MAX_CATCH_UP);
        next = due > MAX_CATCH_UP ? nowNanos + intervalNanos : next + due * intervalNanos;
        return run;
    }
}
