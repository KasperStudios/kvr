package com.kasperstudios.kvr;

import com.kasperstudios.kvr.platform.KVRPlatform;
import com.kasperstudios.kvr.platform.KVRPlatformProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Seam: platform bootstrap. The common core must boot against any {@link KVRPlatform}
 * without touching a mod loader, and report the platform it runs on.
 */
class KVRBootstrapTest {

    @AfterEach
    void tearDown() {
        KVR.reset();
        KVRPlatformProvider.reset();
    }

    @Test
    void bootsOnceAndRecordsThePlatform() {
        KVRPlatformProvider.install(new FakePlatform("fabric"));

        KVR.init();

        assertTrue(KVR.isInitialized());
        assertEquals("fabric", KVR.platformName());
    }

    @Test
    void refusesToBootWithoutAPlatform() {
        KVRPlatformProvider.reset();

        assertThrows(IllegalStateException.class, KVR::init);
        assertFalse(KVR.isInitialized());
    }

    @Test
    void secondInitIsIgnored() {
        KVRPlatformProvider.install(new FakePlatform("fabric"));
        KVR.init();

        KVRPlatformProvider.install(new FakePlatform("neoforge"));
        KVR.init(); // must not throw, must not switch platform under a running OS

        assertEquals("fabric", KVR.platformName());
    }

    private record FakePlatform(String name) implements KVRPlatform {
        @Override
        public String modVersion() {
            return "test";
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public Path gameDir() {
            return Path.of(".");
        }

        @Override
        public Path configDir() {
            return Path.of(".", "config");
        }

        @Override
        public boolean isClient() {
            return true;
        }
    }
}
