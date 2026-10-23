package com.kasperstudios.kvr.hardware;

/** The five hardware modules a pair of KVR glasses can hold (ADR-003 3.3). */
public enum ComponentType {
    CPU("cpu"),
    GPU("gpu"),
    RAM("ram"),
    STORAGE("storage"),
    BATTERY("battery");

    private final String id;

    ComponentType(String id) {
        this.id = id;
    }

    /** Resource-path fragment used for items: {@code kvr:cpu_tier1}. */
    public String id() {
        return id;
    }
}
