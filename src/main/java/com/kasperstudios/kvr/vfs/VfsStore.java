package com.kasperstudios.kvr.vfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * On-disk storage for one glasses UUID: {@code <root>/home/...}, {@code <root>/tmp/...}, etc.
 * Enforces per-file size caps (128 KB write / 256 KB read, to keep the network sane), quota check
 * before atomic writes, directory creation and recursive deletion.
 */
public final class VfsStore {

    public static final int MAX_WRITE_BYTES = 30_000;
    public static final int MAX_READ_BYTES = 256 * 1024;

    private final Path root;

    public record Entry(String name, boolean dir, long size) {}
    public record Usage(long bytes, int files) {}

    public VfsStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public List<Entry> list(String normalizedPath) throws VfsException {
        Path target = resolve(normalizedPath);
        if (!Files.exists(target)) return Collections.emptyList();
        if (!Files.isDirectory(target)) throw new VfsException(VfsException.Code.NOT_FOUND, normalizedPath + " is not a directory");
        try (Stream<Path> s = Files.list(target)) {
            List<Entry> out = new ArrayList<>();
            for (Path p : s.toList()) {
                String fn = p.getFileName().toString();
                if (fn.endsWith(".tmp")) continue;
                boolean dir = Files.isDirectory(p);
                long sz = dir ? 0 : Files.size(p);
                out.add(new Entry(fn, dir, sz));
            }
            out.sort(Comparator.comparing(Entry::dir).reversed().thenComparing(Entry::name));
            return out;
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public String read(String normalizedPath) throws VfsException {
        Path p = resolve(normalizedPath);
        if (!Files.exists(p)) throw new VfsException(VfsException.Code.NOT_FOUND, normalizedPath);
        if (Files.isDirectory(p)) throw new VfsException(VfsException.Code.IS_DIRECTORY, normalizedPath);
        try {
            long sz = Files.size(p);
            if (sz > MAX_READ_BYTES) throw new VfsException(VfsException.Code.TOO_LARGE, sz + " > " + MAX_READ_BYTES);
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public void write(String normalizedPath, String text, VfsQuota quota) throws VfsException {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_BYTES) throw new VfsException(VfsException.Code.TOO_LARGE, bytes.length + " > " + MAX_WRITE_BYTES);
        Path p = resolve(normalizedPath);
        try {
            boolean exists = Files.exists(p);
            if (exists && Files.isDirectory(p)) throw new VfsException(VfsException.Code.IS_DIRECTORY, normalizedPath);
            Usage cur = usage();
            long oldSize = exists ? Files.size(p) : 0;
            long nextBytes = cur.bytes() - oldSize + bytes.length;
            int nextFiles = cur.files() + (exists ? 0 : 1);
            if (nextBytes > quota.bytes()) throw new VfsException(VfsException.Code.QUOTA_BYTES, nextBytes + " > " + quota.bytes());
            if (nextFiles > quota.files()) throw new VfsException(VfsException.Code.QUOTA_FILES, nextFiles + " > " + quota.files());
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName().toString() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public void delete(String normalizedPath) throws VfsException {
        Path p = resolve(normalizedPath);
        if (!Files.exists(p)) return;
        try {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    for (Path sub : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(sub);
                }
            } else {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public Usage usage() {
        if (!Files.exists(root)) return new Usage(0, 0);
        long[] totals = new long[2]; // bytes, files
        try (Stream<Path> s = Files.walk(root)) {
            s.forEach(p -> {
                if (!Files.isDirectory(p) && !p.getFileName().toString().endsWith(".tmp")) {
                    try {
                        totals[0] += Files.size(p);
                        totals[1]++;
                    } catch (IOException ignored) {}
                }
            });
        } catch (IOException ignored) {}
        return new Usage(totals[0], (int) totals[1]);
    }

    private Path resolve(String normalizedPath) throws VfsException {
        Path p = root.resolve(normalizedPath.substring(1)).normalize();
        if (!p.startsWith(root)) throw new VfsException(VfsException.Code.DENIED, "escape attempt: " + normalizedPath);
        return p;
    }
}
