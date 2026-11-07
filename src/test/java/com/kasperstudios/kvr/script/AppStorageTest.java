package com.kasperstudios.kvr.script;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppStorageTest {

    @BeforeEach
    void setUp() {
        AppStorage.clear("test_app");
    }

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(AppStorage.get("test_app", "missing"));
    }

    @Test
    void setAndGetRoundTrip() {
        AppStorage.set("test_app", "theme", "cyber");
        assertEquals("cyber", AppStorage.get("test_app", "theme"));
    }

    @Test
    void removeDeletesKey() {
        AppStorage.set("test_app", "counter", "42");
        AppStorage.remove("test_app", "counter");
        assertNull(AppStorage.get("test_app", "counter"));
    }

    @Test
    void keysListsAllKeys() {
        AppStorage.set("test_app", "a", "1");
        AppStorage.set("test_app", "b", "2");
        assertTrue(AppStorage.keys("test_app").contains("a"));
        assertTrue(AppStorage.keys("test_app").contains("b"));
    }
}
