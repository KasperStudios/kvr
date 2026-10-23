package com.kasperstudios.kvr.platform;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: the GUI facade the core talks to. The core asks for a screen by id and never names a
 * Screen class, so this registry is the only thing standing between "open the chassis screen" and
 * a loader-specific implementation.
 */
class SimpleKVRGuiTest {

    @Test
    void opensRegisteredScreens() {
        SimpleKVRGui gui = new SimpleKVRGui("test");
        AtomicInteger opened = new AtomicInteger();
        gui.register("chassis_assembly", opened::incrementAndGet);

        assertTrue(gui.open("chassis_assembly"));
        assertEquals(1, opened.get());
        assertEquals(java.util.Set.of("chassis_assembly"), gui.screens());
    }

    @Test
    void reportsUnknownScreensInsteadOfThrowing() {
        SimpleKVRGui gui = new SimpleKVRGui("test");

        assertFalse(gui.open("nope"));
        assertFalse(gui.open(null));
        assertTrue(gui.screens().isEmpty());
    }

    @Test
    void rejectsDuplicatesAndInvalidIds() {
        SimpleKVRGui gui = new SimpleKVRGui("test");
        gui.register("notes", () -> { });

        assertThrows(IllegalStateException.class, () -> gui.register("notes", () -> { }));
        assertThrows(IllegalArgumentException.class, () -> gui.register("Notes", () -> { }));
        assertThrows(IllegalArgumentException.class, () -> gui.register("notes", null));
    }

    @Test
    void dedicatedServerDefaultOpensNothing() {
        KVRGuiProvider.reset();

        assertFalse(KVRGuiProvider.get().open("notes"));
        assertTrue(KVRGuiProvider.get().screens().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> KVRGuiProvider.install(null));
    }
}
