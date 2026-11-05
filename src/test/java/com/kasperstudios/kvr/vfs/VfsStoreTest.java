package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VfsStoreTest {

    @TempDir
    Path dir;

    @Test
    void writeReadListDelete() throws Exception {
        VfsStore s = new VfsStore(dir);
        VfsQuota q = VfsQuota.forStorageTier(3);
        s.write("/home/coords.txt", "X: -1284 Y: -54 Z: 312", q);
        assertEquals("X: -1284 Y: -54 Z: 312", s.read("/home/coords.txt"));
        List<VfsStore.Entry> root = s.list("/");
        assertTrue(root.stream().anyMatch(e -> e.name().equals("home") && e.dir()));
        assertTrue(root.stream().anyMatch(e -> e.name().equals("apps")));
        assertEquals(1, s.list("/home").size());
        s.delete("/home/coords.txt");
        assertThrows(VfsException.class, () -> s.read("/home/coords.txt"));
    }

    @Test
    void tierOneAllowsOnlyTwoFiles() throws Exception {
        VfsStore s = new VfsStore(dir);
        VfsQuota q = VfsQuota.forStorageTier(1);
        s.write("/home/a.txt", "a", q);
        s.write("/home/b.txt", "b", q);
        s.write("/home/a.txt", "rewrite is fine", q);
        VfsException e = assertThrows(VfsException.class, () -> s.write("/home/c.txt", "c", q));
        assertEquals(VfsException.Code.QUOTA_FILES, e.code());
    }

    @Test
    void byteQuotaCountsTheReplacedFile() throws Exception {
        VfsStore s = new VfsStore(dir);
        VfsQuota q = new VfsQuota(10, 100);
        s.write("/home/a.txt", "1234567890", q);
        s.write("/home/a.txt", "0987654321", q); // same size replaces, still fits
        VfsException e = assertThrows(VfsException.class, () -> s.write("/home/b.txt", "x", q));
        assertEquals(VfsException.Code.QUOTA_BYTES, e.code());
    }

    @Test
    void oversizedWriteAndNonEmptyDelete() throws Exception {
        VfsStore s = new VfsStore(dir);
        VfsQuota q = VfsQuota.forStorageTier(5);
        assertEquals(VfsException.Code.TOO_LARGE,
            assertThrows(VfsException.class, () -> s.write("/home/big.txt", "x".repeat(VfsStore.MAX_WRITE_BYTES + 1), q)).code());
        s.write("/home/notes/a.txt", "a", q);
        assertEquals(VfsException.Code.NOT_EMPTY, assertThrows(VfsException.class, () -> s.delete("/home/notes")).code());
    }

    @Test
    void quotasFollowSpec() {
        assertEquals(256 * 1024, VfsQuota.forStorageTier(1).bytes());
        assertEquals(2, VfsQuota.forStorageTier(1).files());
        assertEquals(5, VfsQuota.forStorageTier(2).files());
        assertEquals(512L * 1024 * 1024, VfsQuota.forStorageTier(5).bytes());
    }
}
