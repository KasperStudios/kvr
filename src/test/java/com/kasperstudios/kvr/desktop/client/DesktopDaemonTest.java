package com.kasperstudios.kvr.desktop.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DesktopDaemonTest {

    @Test
    void runtimeDirIsPrivatePerUser() {
        assertEquals(Path.of("/run/user/1000", "kvr"), DesktopDaemon.runtimeDir(Map.of("XDG_RUNTIME_DIR", "/run/user/1000"), false, "/tmp", "kasper"));
        assertEquals(Path.of("/tmp", "kvr-kasper"), DesktopDaemon.runtimeDir(Map.of(), false, "/tmp", "kasper"));
        assertEquals(Path.of("C:\\Users\\k\\AppData\\Local", "kvr"),
            DesktopDaemon.runtimeDir(Map.of("LOCALAPPDATA", "C:\\Users\\k\\AppData\\Local"), true, "C:\\Temp", "k"));
    }

    @Test
    void launcherEntriesSplitWithoutAShell() {
        assertEquals(List.of(List.of("foot"), List.of("firefox", "--new-instance"), List.of("thunar")),
            DesktopDaemon.apps(" foot ; firefox   --new-instance;;thunar "));
        assertTrue(DesktopDaemon.apps("").isEmpty());
        assertEquals(List.of(List.of("sh", "-c", "rm", "-rf")), DesktopDaemon.apps("sh -c rm -rf"), "no shell parsing: each word is one argv entry");
    }

    @Test
    void downloadNeedsAPinnedHash() {
        DesktopDaemon.Info noHash = new DesktopDaemon.Info(Path.of("x"), false, "", "https://example.org/kvr-desktopd", "", false);
        assertFalse(noHash.canDownload());
        DesktopDaemon.Info pinned = new DesktopDaemon.Info(Path.of("x"), false, "", "https://example.org/kvr-desktopd", "a".repeat(64), false);
        assertTrue(pinned.canDownload());
        assertEquals("a".repeat(64), pinned.approvedHash());
    }
}
