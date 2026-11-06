package com.kasperstudios.kvr.power;

/**
 * Pure power physics, no Minecraft state: unit-tested in {@code PowerSimulatorTest}.
 *
 * <p>Design decision for the Creator to confirm: once an overload has started, an empty battery
 * does NOT stop it (the cell is shorting internally). Only the deliberate Power Off opens the
 * circuit — otherwise a T1 cell would drain in 5 s and make the explosion impossible to reach.
 */
public final class PowerSimulator {

    public enum Event { NONE, BOOTED, SHUTDOWN_EMPTY, OVERLOAD_STARTED, SAVED, DETONATE }

    private PowerSimulator() {}

    /** Advance to {@code gameTime}; the elapsed time since the last step is consumed at once. */
    public static Event step(PowerBuild build, PowerData d, long gameTime) {
        long dt = d.lastStep == PowerData.NONE ? 0 : Math.max(0, gameTime - d.lastStep);
        d.lastStep = gameTime;

        if (d.overloading() && gameTime >= d.overloadDeadline) return Event.DETONATE;
        if (d.state == PowerState.OFF) {
            d.lastStep = gameTime;
            return Event.NONE;
        }

        Event event = Event.NONE;
        if (d.bootUntil > 0 && gameTime >= d.bootUntil) {
            d.bootUntil = 0;
            event = Event.BOOTED;
        }

        d.charge = Math.max(0, Math.min(d.capacity(build), d.charge - build.draw(d.state) * dt));

        if (d.overloading()) {
            d.wear = Math.max(d.wear, d.overloadProgress(gameTime)); // capacity dies with the cell
            return event;
        }
        if (build.overloads(d.state)) {
            d.overloadDeadline = gameTime + PowerTable.OVERLOAD_TICKS;
            return Event.OVERLOAD_STARTED;
        }
        if (d.charge <= 0) {
            d.state = PowerState.OFF;
            d.bootUntil = 0;
            return Event.SHUTDOWN_EMPTY;
        }
        return event;
    }

    /** Player action (window button, gesture, command). Power Off during an overload is the save. */
    public static Event setState(PowerBuild build, PowerData d, PowerState target, long gameTime) {
        if (target == d.state) return Event.NONE;
        if (target == PowerState.OFF) {
            d.state = PowerState.OFF;
            d.bootUntil = 0;
            if (d.overloading()) {
                d.overloadDeadline = PowerData.NONE;
                return Event.SAVED;
            }
            return Event.NONE;
        }
        if (d.state == PowerState.OFF) {
            if (d.charge <= 0) return Event.NONE; // nothing to boot with
            d.lastStep = gameTime;
            d.bootUntil = gameTime + build.coldBootTicks();
        }
        d.lastStep = gameTime;
        d.state = target;
        return Event.NONE;
    }
}
