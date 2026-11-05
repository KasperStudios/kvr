package com.kasperstudios.kvr.power;

/** SPEC 2.7: power states of the glasses. */
public enum PowerState {
    /** Fully on: standby + every module draws. */
    ON,
    /** Display off: sensors and network alive, consumption −70%. */
    HUD_OFF,
    /** Circuit open: 0 FE/t; turning on again needs a cold boot that depends on the CPU tier. */
    OFF;

    public static PowerState byName(String name) {
        for (PowerState s : values()) if (s.name().equalsIgnoreCase(name)) return s;
        return OFF;
    }
}
