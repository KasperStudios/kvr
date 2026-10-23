package com.kasperstudios.kvr.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: {@link KVRNames} is the only place that decides what a legal KVR resource path is.
 * Every registered item, block, packet and screen id flows through it, so the rules
 * (lowercase, no namespace separator, no blanks) are enforced once and tested here.
 */
class KVRNamesTest {

    @Test
    void namespaceIsTheModId() {
        assertEquals("kvr", KVRNames.NAMESPACE);
    }

    @Test
    void acceptsTypicalRegistryPaths() {
        assertEquals("cpu_tier1", KVRNames.validated("cpu_tier1"));
        assertEquals("up_ore", KVRNames.validated("up_ore"));
        assertEquals("notes.vrapp", KVRNames.validated("notes.vrapp"));
        assertEquals("dock/plate", KVRNames.validated("dock/plate"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated(""));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("   "));
    }

    @Test
    void rejectsExplicitNamespace() {
        // Callers must never smuggle a foreign namespace ("minecraft:stone") into KVR registries.
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("minecraft:stone"));
    }

    @Test
    void rejectsUppercaseAndSpaces() {
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("CPU"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("cpu tier1"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("../../config/kvr.json"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("apps/../../secrets"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("./notes"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("/notes"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("notes/"));
        assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("apps//notes"));
    }

    @Test
    void reportsTheOffendingPathInTheMessage() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> KVRNames.validated("CPU"));
        assertTrue(ex.getMessage().contains("CPU"), "message should quote the bad path: " + ex.getMessage());
    }
}
