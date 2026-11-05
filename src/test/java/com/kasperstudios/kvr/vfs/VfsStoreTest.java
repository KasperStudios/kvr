package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VfsStoreTest {

    @Test
    void crudLifecycle(@TempDir Path tmp) throws Exception {
        VfsStore s = new VfsStore(tmp);
        VfsQuota q = VfsQuota.forStorageTier(1); // 256 KB, 2 files

        s.write("/home/notes.txt", "hello", q);
        assertEquals("hello", s.read("/home/notes.txt"));
        assertEquals(1, s.list("/home").size());
        assertEquals("notes.txt", s.list("/home").get(0).name());
        assertFalse(s.list("/home").get(0).dir());

        s.delete("/home/notes.txt");
        assertThrows(VfsException.class, () -> s.read("/home/notes.txt"));
        assertEquals(0, s.list("/home").size());
    }

    @Test
    void tier1FileCountQuotaEnforced(@TempDir Path tmp) throws Exception {
        VfsStore s = new VfsStore(tmp);
        VfsQuota q = VfsQuota.forStorageTier(1); // 2 files limit

        s.write("/home/a.txt", "1", q);
        s.write("/home/b.txt", "2", q);
        VfsException e = assertThrows(VfsException.class, () -> s.write("/home/c.txt", "3", q));
        assertEquals(VfsException.Code.QUOTA_FILES, e.code());

        // Overwriting an existing file is fine — doesn't bump file count
        s.write("/home/a.txt", "new-1", q);
        assertEquals("new-1", s.read("/home/a.txt"));
    }

    @Test
    void tier1ByteQuotaEnforced(@TempDir Path tmp) throws Exception {
        VfsStore s = new VfsStore(tmp);
        VfsQuota q = new VfsQuota(10, 10); // 10 bytes

        s.write("/home/a.txt", "12345", q); // 5 bytes
        VfsException e = assertThrows(VfsException.class, () -> s.write("/home/b.txt", "123456", q)); // 5 + 6 = 11 > 10
        assertEquals(VfsException.Code.QUOTA_BYTES, e.code());
    }

    @Test
    void recursiveDeleteCleansSubdirs(@TempDir Path tmp) throws Exception {
        VfsStore s = new VfsStore(tmp);
        VfsQuota q = VfsQuota.forStorageTier(3);
        s.write("/data/notes/sub/a.txt", "a", q);
        s.write("/data/notes/b.txt", "b", q);
        s.delete("/data/notes");
        assertFalse(Files.exists(tmp.resolve("data/notes")));
    }
}
