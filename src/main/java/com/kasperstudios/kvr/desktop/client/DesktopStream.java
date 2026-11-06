package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.desktop.DesktopProtocol;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

/**
 * One app window inside kvr-desktopd (one xdg_toplevel). Pixels arrive in a shared-memory file with
 * three slots: the daemon fills a free slot and sends FRAME, the render thread uploads it straight from
 * the mapped buffer to the GPU and ACKs the slot. Only the newest frame is kept: an older unread frame is
 * ACKed at once (dropped) and its damage is folded into "full repaint", so the texture never misses a change.
 */
public final class DesktopStream {

    /** Receives the newest frame on the render thread. {@code pixels} is positioned at the slot start. */
    public interface Uploader {
        void upload(ByteBuffer pixels, DesktopProtocol.Frame frame, boolean full);
    }

    /**
     * The app's cursor (D2). {@code version} grows with every change, so the window re-uploads an image
     * cursor only when it really changed. Sizes and hotspot in frame (app) pixels.
     */
    public record CursorState(int shape, String name, int hotX, int hotY, int width, int height, byte[] pixels, int version) {}

    @Nullable private volatile CursorState cursor;
    private int cursorVersion;

    private final DesktopBridge bridge;
    private final int id;
    private final int req;
    private volatile String title;
    private volatile String appId;
    private volatile boolean closed;
    private volatile String closeReason = "";

    private final Object mapLock = new Object();
    @Nullable private MappedByteBuffer map;
    private volatile int generation = -1;
    private int slotSize;
    private final AtomicReference<DesktopProtocol.Frame> ready = new AtomicReference<>();
    /** A frame was dropped or the buffer changed: the next upload must repaint everything. */
    private volatile boolean needFull = true;

    DesktopStream(DesktopBridge bridge, int id, int req, String title, String appId) {
        this.bridge = bridge;
        this.id = id;
        this.req = req;
        this.title = title;
        this.appId = appId;
    }

    /** Reader thread: BUFFER. Every slot of the new file is free; frames of the old file are void. */
    void remap(DesktopProtocol.Buffer b) throws IOException {
        try (FileChannel ch = FileChannel.open(Path.of(b.path()), StandardOpenOption.READ)) {
            long size = DesktopProtocol.SHM_HEADER + (long) b.slots() * b.slotSize();
            MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
            m.order(ByteOrder.LITTLE_ENDIAN);
            int[] layout = DesktopProtocol.readShmHeader(m);
            synchronized (mapLock) {
                map = m; // the old mapping is released by the GC (Java cannot unmap explicitly before 22)
                generation = b.generation();
                slotSize = layout[1];
                ready.set(null);
                needFull = true;
            }
        }
    }

    /** Reader thread: FRAME. Keeps only the newest one. */
    void offer(DesktopProtocol.Frame f) {
        if (f.generation() != generation) {
            bridge.send(new DesktopProtocol.Ack(id, f.generation(), f.slot()));
            return;
        }
        DesktopProtocol.Frame old = ready.getAndSet(f);
        if (old != null) {
            needFull = true;
            bridge.send(new DesktopProtocol.Ack(id, old.generation(), old.slot()));
        }
    }

    /** Render thread: uploads the newest frame, if any, then gives its slot back. */
    public boolean upload(Uploader target) {
        synchronized (mapLock) {
            DesktopProtocol.Frame f = ready.getAndSet(null);
            if (f == null) return false;
            try {
                MappedByteBuffer m = map;
                long need = (long) f.stride() * f.height();
                if (m == null || f.generation() != generation || need > slotSize || f.stride() < f.width() * 4) return false;
                ByteBuffer px = m.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                px.position(DesktopProtocol.SHM_HEADER + f.slot() * slotSize);
                px.limit(px.position() + (int) need);
                boolean full = needFull || f.damage().isEmpty();
                needFull = false;
                target.upload(px.slice(), f, full);
                return true;
            } finally {
                bridge.send(new DesktopProtocol.Ack(id, f.generation(), f.slot()));
            }
        }
    }

    void retitle(String t) { this.title = t; }

    /** Reader thread: CURSOR. */
    synchronized void setCursor(DesktopProtocol.Cursor c) {
        cursor = new CursorState(c.shape(), c.name(), c.hotX(), c.hotY(), c.width(), c.height(), c.pixels(), ++cursorVersion);
    }

    /** Null until the app set a cursor over this window. */
    @Nullable
    public CursorState cursor() { return cursor; }

    void markClosed(String reason) {
        closed = true;
        closeReason = reason;
    }

    public int id() { return id; }
    public int req() { return req; }
    public String title() { return title; }
    public String appId() { return appId; }
    public boolean closed() { return closed; }
    public String closeReason() { return closeReason; }
    public DesktopBridge bridge() { return bridge; }
}
