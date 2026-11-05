package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VfsPathTest {

    @Test
    void normalizesSlashes() throws Exception {
        assertEquals("/home/notes/coords.txt", VfsPath.normalize("/home//notes/coords.txt/"));
        assertEquals("/", VfsPath.normalize("/"));
    }

    @Test
    void rejectsTraversalAndJunk() {
        for (String bad : new String[] {"home/a", "/home/../system/x", "/home/./a", "/home/a b", "/home/\u0444\u0430\u0439\u043b", "", "/" + "a/".repeat(10)}) {
            assertThrows(VfsException.class, () -> VfsPath.normalize(bad), bad);
        }
    }

    @Test
    void sandboxRules() {
        assertTrue(VfsPath.canAccess("/home/coords.txt", "notes", true));
        assertTrue(VfsPath.canAccess("/tmp/x", "notes", true));
        assertTrue(VfsPath.canAccess("/data/notes/state.json", "notes", true));
        assertFalse(VfsPath.canAccess("/data/messenger/chat.json", "notes", false));
        assertFalse(VfsPath.canAccess("/system/boot.cfg", "notes", false));
        assertTrue(VfsPath.canAccess("/apps/kav/main.js", "notes", false));
        assertFalse(VfsPath.canAccess("/apps/kav/main.js", "notes", true));
        assertTrue(VfsPath.canAccess("/", "notes", false));
        assertFalse(VfsPath.canAccess("/", "notes", true));
    }
}
