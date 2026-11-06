package com.kasperstudios.kvr.power;

import net.minecraft.nbt.CompoundTag;

/**
 * Power state stored on the glasses stack. Times are absolute game ticks, so an overload keeps
 * counting even while the glasses sit in a chest (no item ticks there) — they go off the moment
 * they are ticked again. That is the "mine somebody's house" path from SPEC 2.8.
 */
public final class PowerData {

    public static final long NONE = -1L;

    /** Stored energy, FE. */
    double charge;
    /** Permanent capacity loss 0..1 from overloads (SPEC 2.8: degrades to 0 capacity). */
    double wear;
    PowerState state = PowerState.OFF;
    /** Cold boot finishes at this game time; 0 when not booting. */
    long bootUntil;
    /** Detonation game time; {@link #NONE} when not overloading. */
    long overloadDeadline = NONE;
    /** Game time of the last simulation step; {@link #NONE} before the first. */
    long lastStep = NONE;

    public double charge() { return charge; }
    public double wear() { return wear; }
    public long bootUntil() { return bootUntil; }
    public PowerState state() { return state; }
    public boolean overloading() { return overloadDeadline != NONE; }

    public boolean booting(long gameTime) {
        return state != PowerState.OFF && bootUntil > 0 && gameTime < bootUntil;
    }

    /** On (or HUD-off) and finished booting: the OS and its windows are alive. */
    public boolean active(long gameTime) {
        return state != PowerState.OFF && !booting(gameTime);
    }

    /** Display lit: fully ON and booted. HUD_OFF keeps the OS running but shows nothing. */
    public boolean displayOn(long gameTime) {
        return state == PowerState.ON && !booting(gameTime);
    }

    public double capacity(PowerBuild build) {
        return build.capacity() * (1.0 - wear);
    }

    public int percent(PowerBuild build) {
        double cap = capacity(build);
        return cap <= 0 ? 0 : (int) Math.round(100.0 * Math.min(charge, cap) / cap);
    }

    /** 0 at overload start → 1 at detonation. */
    public double overloadProgress(long gameTime) {
        if (!overloading()) return 0;
        long start = overloadDeadline - PowerTable.OVERLOAD_TICKS;
        return Math.max(0, Math.min(1, (gameTime - start) / (double) PowerTable.OVERLOAD_TICKS));
    }

    public int secondsLeft(long gameTime) {
        return overloading() ? (int) Math.max(0, Math.ceil((overloadDeadline - gameTime) / 20.0)) : 0;
    }

    public double temperature(long gameTime) {
        return PowerTable.AMBIENT_CELSIUS + (PowerTable.DETONATION_CELSIUS - PowerTable.AMBIENT_CELSIUS) * overloadProgress(gameTime);
    }

    /** Admin / dock helper: set charge as a share of the current (worn) capacity. */
    public void setChargePercent(PowerBuild build, double percent) {
        charge = capacity(build) * Math.max(0, Math.min(100, percent)) / 100.0;
    }

    /** Adds energy up to capacity; returns what was accepted. */
    public double receive(PowerBuild build, double amount) {
        double room = Math.max(0, capacity(build) - charge);
        double accepted = Math.max(0, Math.min(room, amount));
        charge += accepted;
        return accepted;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putDouble("charge", charge);
        t.putDouble("wear", wear);
        t.putString("state", state.name());
        t.putLong("boot_until", bootUntil);
        t.putLong("overload_deadline", overloadDeadline);
        t.putLong("last_step", lastStep);
        return t;
    }

    public static PowerData load(CompoundTag t) {
        PowerData d = new PowerData();
        d.charge = PowerNbt.getDouble(t, "charge", 0);
        d.wear = PowerNbt.getDouble(t, "wear", 0);
        d.state = PowerState.byName(PowerNbt.getString(t, "state", PowerState.OFF.name()));
        d.bootUntil = PowerNbt.getLong(t, "boot_until", 0);
        d.overloadDeadline = PowerNbt.getLong(t, "overload_deadline", NONE);
        d.lastStep = PowerNbt.getLong(t, "last_step", NONE);
        return d;
    }
}
