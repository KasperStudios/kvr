package com.kasperstudios.kvr.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KvrConfigTest {

    @AfterEach
    void restoreDirectory() {
        KvrConfig.setDirectory(Path.of("config"));
    }

    @Test
    void parsesFlatTomlSubset() {
        Map<String, String> m = KvrConfig.parse("""
            # comment
            [section]
            enable_host_desktop_windows = true   # trailing
            port = 99999
            name = "kvr"
            url = "https://example.org/kvr#sha"  # the # inside quotes stays
            broken line
            """);
        assertTrue(KvrConfig.bool(m, "enable_host_desktop_windows", false));
        assertEquals(65535, KvrConfig.clampInt(m, "port", 47800, 1024, 65535));
        assertEquals("kvr", m.get("name"));
        assertEquals("https://example.org/kvr#sha", m.get("url"));
        assertFalse(m.containsKey("broken line"));
        assertEquals(7, KvrConfig.clampInt(m, "missing", 7, 0, 8));
    }

    @Test
    void withValueReplacesInPlaceOrAppendsAndKeepsComments() {
        String text = "# keep me\nhost_desktop_consent = \"\"\nshow_public_windows = true\n";
        String replaced = KvrConfig.withValue(text, "host_desktop_consent", "\"ab12\"");
        assertEquals("# keep me\nhost_desktop_consent = \"ab12\"\nshow_public_windows = true\n", replaced);
        String appended = KvrConfig.withValue("a = 1", "b", "false");
        assertEquals("a = 1\nb = false\n", appended);
        assertThrows(IllegalArgumentException.class, () -> KvrConfig.literal("evil\" = true"));
    }

    @Test
    void updateClientPersistsConsentAndReloads(@TempDir Path dir) {
        KvrConfig.setDirectory(dir);
        assertEquals("", KvrConfig.client().hostDesktopConsent());
        assertFalse(KvrConfig.client().hostDesktopWindows());
        KvrConfig.updateClient("host_desktop_consent", "ab12cd");
        KvrConfig.updateClient("enable_host_desktop_windows", true);
        assertEquals("ab12cd", KvrConfig.client().hostDesktopConsent());
        assertTrue(KvrConfig.client().hostDesktopWindows());
        assertTrue(KvrConfig.client().hostDesktopApps().contains("foot"));
    }
}
