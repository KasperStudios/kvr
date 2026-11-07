package com.kasperstudios.kvr.vfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * One glasses' file system on the server disk ({@code <world>/kvr/vfs/<glasses-uuid>/}). Text files
 * for now (UTF-8). Enforces the byte and file-count quota on every write. Plain NIO, no Minecraft
 * classes — unit-tested with a temp directory.
 */
public final class VfsStore {

    /** One C2S custom payload is capped at 32767 bytes by vanilla; keep a margin for the header. */
    public static final int MAX_WRITE_BYTES = 30_000;
    public static final int MAX_READ_BYTES = 256 * 1024;

    public record Entry(String name, boolean dir, long size) {}

    public record Usage(long bytes, int files) {}

    private final Path root;

    public VfsStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Folder of these glasses on the server disk (cache key for the App Manager). */
    public Path root() {
        return root;
    }

    public List<Entry> list(String path) throws VfsException {
        Path dir = resolve(path);
        if (path.equals("/")) ensureSkeleton();
        if (!Files.isDirectory(dir)) throw new VfsException(VfsException.Code.NOT_FOUND, path);
        List<Entry> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) out.add(new Entry(p.getFileName().toString(), Files.isDirectory(p), Files.isDirectory(p) ? 0 : Files.size(p)));
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
        out.sort((a, b) -> a.dir() != b.dir() ? (a.dir() ? -1 : 1) : a.name().compareTo(b.name()));
        return out;
    }

    public String read(String path) throws VfsException {
        Path f = resolve(path);
        if (Files.isDirectory(f)) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        if (!Files.isRegularFile(f)) throw new VfsException(VfsException.Code.NOT_FOUND, path);
        try {
            if (Files.size(f) > MAX_READ_BYTES) throw new VfsException(VfsException.Code.TOO_LARGE, path);
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public void write(String path, String text, VfsQuota quota) throws VfsException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_WRITE_BYTES) throw new VfsException(VfsException.Code.TOO_LARGE, bytes.length + " bytes");
        if (path.equals("/")) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        Path f = resolve(path);
        if (Files.isDirectory(f)) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        try {
            boolean exists = Files.isRegularFile(f);
            long old = exists ? Files.size(f) : 0;
            Usage u = usage();
            if (!exists && u.files() + 1 > quota.files()) throw new VfsException(VfsException.Code.QUOTA_FILES, u.files() + "/" + quota.files());
            if (u.bytes() - old + bytes.length > quota.bytes()) throw new VfsException(VfsException.Code.QUOTA_BYTES, (u.bytes() - old + bytes.length) + "/" + quota.bytes());
            Files.createDirectories(f.getParent());
            Files.write(f, bytes);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /**
     * Binary write for the App Manager ({@code /apps/<id>.vrapp}, 0025). Same quota rules as {@link #write},
     * no per-packet cap: the installer assembled the bytes from chunks and validated the package already.
     * Atomic (temp file + move), so a crash mid-write never leaves half a package.
     */
    public void writeBytes(String path, byte[] bytes, VfsQuota quota) throws VfsException {
        if (path.equals("/")) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        Path f = resolve(path);
        if (Files.isDirectory(f)) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        try {
            boolean exists = Files.isRegularFile(f);
            long old = exists ? Files.size(f) : 0;
            Usage u = usage();
            if (!exists && u.files() + 1 > quota.files()) throw new VfsException(VfsException.Code.QUOTA_FILES, u.files() + "/" + quota.files());
            if (u.bytes() - old + bytes.length > quota.bytes()) throw new VfsException(VfsException.Code.QUOTA_BYTES, (u.bytes() - old + bytes.length) + "/" + quota.bytes());
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".part");
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /** Binary read for the App Manager; {@code maxBytes} guards memory. */
    public byte[] readBytes(String path, int maxBytes) throws VfsException {
        Path f = resolve(path);
        if (Files.isDirectory(f)) throw new VfsException(VfsException.Code.IS_DIRECTORY, path);
        if (!Files.isRegularFile(f)) throw new VfsException(VfsException.Code.NOT_FOUND, path);
        try {
            if (Files.size(f) > maxBytes) throw new VfsException(VfsException.Code.TOO_LARGE, path);
            return Files.readAllBytes(f);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /** Last-modified millis + size of a file (cache key for parsed packages); {@code [-1, -1]} if missing. */
    public long[] stamp(String path) throws VfsException {
        Path f = resolve(path);
        try {
            if (!Files.isRegularFile(f)) return new long[] {-1, -1};
            return new long[] {Files.getLastModifiedTime(f).toMillis(), Files.size(f)};
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /** Deletes a file or an empty directory. */
    public void delete(String path) throws VfsException {
        if (path.equals("/")) throw new VfsException(VfsException.Code.DENIED, path);
        Path f = resolve(path);
        if (!Files.exists(f)) throw new VfsException(VfsException.Code.NOT_FOUND, path);
        try {
            if (Files.isDirectory(f)) {
                try (Stream<Path> s = Files.list(f)) {
                    if (s.findAny().isPresent()) throw new VfsException(VfsException.Code.NOT_EMPTY, path);
                }
            }
            Files.delete(f);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    public Usage usage() throws VfsException {
        if (!Files.isDirectory(root)) return new Usage(0, 0);
        try (Stream<Path> s = Files.walk(root)) {
            long[] acc = {0, 0};
            s.filter(Files::isRegularFile).forEach(p -> {
                acc[1]++;
                try {
                    acc[0] += Files.size(p);
                } catch (IOException ignored) {
                    // file vanished between walk and size: count it as 0
                }
            });
            return new Usage(acc[0], (int) acc[1]);
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /** /home /tmp /data /apps exist from the first listing. */
    private void ensureSkeleton() throws VfsException {
        try {
            for (String d : new String[] {"home", "tmp", "data", "apps"}) Files.createDirectories(root.resolve(d));
        } catch (IOException e) {
            throw new VfsException(VfsException.Code.IO, e.getMessage());
        }
    }

    /** {@code path} must already be normalized by {@link VfsPath#normalize}; this is the last line of defence. */
    private Path resolve(String path) throws VfsException {
        Path p = path.equals("/") ? root : root.resolve(path.substring(1)).normalize();
        if (!p.startsWith(root)) throw new VfsException(VfsException.Code.INVALID_PATH, path);
        return p;
    }
}
