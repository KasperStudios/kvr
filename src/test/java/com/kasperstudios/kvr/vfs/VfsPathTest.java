package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VfsPathTest {

    @Test
    void normalisesAndStripsDuplicateSlashes() throws Exception {
        assertEquals("/", VfsPath.normalize("/"));
        assertEquals("/home/notes.txt", VfsPath.normalize("/home//notes.txt"));
        assertEquals("/tmp/scratch", VfsPath.normalize("/tmp/scratch/"));
    }

    @Test
    void rejectsDotsAndEscapes() {
        assertThrows(VfsException.class, () -> VfsPath.normalize("/home/../etc/passwd"));
        assertThrows(VfsException.class, () -> VfsPath.normalize("/home/./a"));
        assertThrows(VfsException.class, () -> VfsPath.normalize("relative/path"));
        assertThrows(VfsException.class, () -> VfsPath.normalize(""));
        assertThrows(VfsException.class, () -> VfsPath.normalize("/bad:char"));
    }

    @Test
    void sandboxPermissions() {
        // Shared home/tmp
        assertTrue(VfsPath.canAccess("/home/todo.txt", "notes", true));
        assertTrue(VfsPath.canAccess("/home/todo.txt", "messenger", false));
        assertTrue(VfsPath.canAccess("/tmp/scratch", "notes", true));

        // Private /data/<app>
        assertTrue(VfsPath.canAccess("/data/notes/db.json", "notes", true));
        assertFalse(VfsPath.canAccess("/data/notes/db.json", "kav", false));

        // System is locked
        assertFalse(VfsPath.canAccess("/system/root.key", "notes", false));
        assertFalse(VfsPath.canAccess("/apps/kav.vrapp", "notes", true));
        assertTrue(VfsPath.canAccess("/apps/kav.vrapp", "notes", false));
    }
}
