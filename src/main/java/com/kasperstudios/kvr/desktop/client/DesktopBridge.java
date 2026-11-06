package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.desktop.DesktopProtocol;
import java.io.EOFException;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Control link to kvr-desktopd over a Unix domain socket (Java 16+: Linux, macOS, Windows 10 1803+).
 * Small binary messages only ({@link DesktopProtocol}); pixels go through {@link DesktopStream}'s
 * shared memory. One reader thread decodes; everything user-visible is handed to the client thread.
 *
 * <p>Connect retries for up to 5 s while the freshly started daemon creates its socket. HELLO carries
 * the per-launch token; until HELLO_OK arrives, LAUNCH requests are queued.
 */
public final class DesktopBridge implements AutoCloseable {

    public enum State { CONNECTING, READY, CLOSED }

    /** Client-thread callbacks for the windows layer ({@link HostDesktopClient}). */
    public interface Listener {
        /** New toplevel: the launched app's window ({@code req} > 0) or a dialog / extra window ({@code req} = 0). */
        void streamOpened(DesktopStream stream);
        void streamClosed(DesktopStream stream);
        void launchFailed(int req, String message);
        void bridgeClosed(String reason);
    }

    private final Path socket;
    private final String token;
    private final Listener listener;
    private final Map<Integer, DesktopStream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger reqSeq = new AtomicInteger();
    private final java.util.Queue<DesktopProtocol.Msg> queued = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Object writeLock = new Object();
    @Nullable private volatile SocketChannel channel;
    private volatile State state = State.CONNECTING;
    private volatile String reason = "";
    private volatile int caps;

    public DesktopBridge(Path socket, String token, Listener listener) {
        this.socket = socket;
        this.token = token;
        this.listener = listener;
    }

    public void start() {
        Thread t = new Thread(this::run, "KVR-Desktop-Bridge");
        t.setDaemon(true);
        t.start();
    }

    // --- requests (any thread) -----------------------------------------------------------------

    /** Starts an app inside the daemon's hidden compositor. @return request id echoed in STREAM / ERROR */
    public int launch(List<String> argv) {
        int req = reqSeq.incrementAndGet();
        DesktopProtocol.Launch m = new DesktopProtocol.Launch(req, List.copyOf(argv));
        if (state == State.READY) send(m);
        else queued.add(m);
        return req;
    }

    public void send(DesktopProtocol.Msg m) {
        SocketChannel ch = channel;
        if (ch == null || state == State.CLOSED) return;
        ByteBuffer b = ByteBuffer.wrap(DesktopProtocol.encode(m));
        synchronized (writeLock) {
            try {
                while (b.hasRemaining()) ch.write(b);
            } catch (IOException e) {
                fail("write: " + e.getMessage());
            }
        }
    }

    // --- reader thread -------------------------------------------------------------------------

    private void run() {
        try {
            SocketChannel ch = connect();
            channel = ch;
            send(new DesktopProtocol.Hello(DesktopProtocol.VERSION, token));
            ByteBuffer header = ByteBuffer.allocate(DesktopProtocol.HEADER).order(ByteOrder.LITTLE_ENDIAN);
            ByteBuffer payload = ByteBuffer.allocate(DesktopProtocol.MAX_PAYLOAD).order(ByteOrder.LITTLE_ENDIAN);
            while (state != State.CLOSED) {
                header.clear();
                readFully(ch, header);
                header.flip();
                int type = header.getShort() & 0xFFFF;
                header.getShort();
                int len = header.getInt();
                if (len < 0 || len > DesktopProtocol.MAX_PAYLOAD) throw new IOException("bad message length " + len);
                payload.clear().limit(len);
                readFully(ch, payload);
                payload.flip();
                handle(DesktopProtocol.decode(type, payload));
            }
        } catch (IOException | RuntimeException e) {
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private SocketChannel connect() throws IOException {
        long deadline = System.currentTimeMillis() + 5000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline && state != State.CLOSED) {
            try {
                SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                try {
                    ch.connect(UnixDomainSocketAddress.of(socket));
                    return ch;
                } catch (IOException e) {
                    ch.close();
                    throw e;
                }
            } catch (IOException e) {
                last = e;
                if (!DesktopDaemon.alive()) throw new IOException("kvr-desktopd " + DesktopDaemon.exitInfo());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
        }
        throw last != null ? last : new IOException("closed");
    }

    private static void readFully(SocketChannel ch, ByteBuffer b) throws IOException {
        while (b.hasRemaining()) {
            if (ch.read(b) < 0) throw new EOFException("kvr-desktopd closed the connection");
        }
    }

    private void handle(DesktopProtocol.Msg m) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        if (m instanceof DesktopProtocol.HelloOk ok) {
            if (ok.version() != DesktopProtocol.VERSION) {
                throw new IOException("kvr-desktopd speaks protocol " + ok.version() + ", the mod " + DesktopProtocol.VERSION + " — update kvr-desktopd");
            }
            caps = ok.caps();
            state = State.READY;
            DesktopDaemon.markRunning();
            DesktopProtocol.Msg q;
            while ((q = queued.poll()) != null) send(q);
        } else if (m instanceof DesktopProtocol.Stream s) {
            DesktopStream stream = new DesktopStream(this, s.stream(), s.req(), s.title(), s.appId());
            streams.put(s.stream(), stream);
            mc.execute(() -> listener.streamOpened(stream));
        } else if (m instanceof DesktopProtocol.Buffer b) {
            DesktopStream stream = streams.get(b.stream());
            if (stream != null) stream.remap(b);
        } else if (m instanceof DesktopProtocol.Frame f) {
            DesktopStream stream = streams.get(f.stream());
            if (stream != null) stream.offer(f);
            else send(new DesktopProtocol.Ack(f.stream(), f.generation(), f.slot()));
        } else if (m instanceof DesktopProtocol.Title t) {
            DesktopStream stream = streams.get(t.stream());
            if (stream != null) stream.retitle(t.title());
        } else if (m instanceof DesktopProtocol.Closed c) {
            DesktopStream stream = streams.remove(c.stream());
            if (stream != null) {
                stream.markClosed(c.reason());
                mc.execute(() -> listener.streamClosed(stream));
            }
        } else if (m instanceof DesktopProtocol.Error e) {
            mc.execute(() -> listener.launchFailed(e.req(), e.message()));
        } else if (m instanceof DesktopProtocol.Cursor c) {
            DesktopStream stream = streams.get(c.stream());
            if (stream != null) stream.setCursor(c);
        } else if (m instanceof DesktopProtocol.Clipboard c) {
            mc.execute(() -> DesktopClipboard.fromApp(c.text()));
        }
    }

    private void fail(String why) {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        reason = why == null ? "" : why;
        KVR.logger().warn("KVR desktop bridge closed: {}", reason);
        closeChannel();
        for (DesktopStream s : streams.values()) s.markClosed(reason);
        streams.clear();
        DesktopDaemon.markFailed(reason);
        Minecraft.getInstance().execute(() -> listener.bridgeClosed(reason));
    }

    private void closeChannel() {
        SocketChannel ch = channel;
        channel = null;
        if (ch == null) return;
        try {
            ch.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    @Override
    public void close() {
        state = State.CLOSED;
        closeChannel();
    }

    public State state() { return state; }
    public String reason() { return reason; }
    /** HELLO_OK capability bits ({@link DesktopProtocol#CAP_CLIPBOARD} …); 0 until READY. */
    public int caps() { return caps; }
    public int streamCount() { return streams.size(); }
}
