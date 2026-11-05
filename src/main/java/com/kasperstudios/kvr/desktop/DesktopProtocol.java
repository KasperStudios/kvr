package com.kasperstudios.kvr.desktop;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire format between the mod and {@code kvr-desktopd} (protocol v1 of the D1 bridge). Pure Java,
 * no Minecraft classes: unit-tested against the same golden bytes as {@code native/kvr-desktopd/src/protocol.rs}.
 *
 * <pre>
 * message = u16 type, u16 flags (0), u32 payload length, payload          (all little-endian)
 * str     = u16 byte length + UTF-8 (≤ 4096 bytes)
 * pixels never go through the socket: they live in the stream's shared-memory file
 * shm file = 64-byte header (u32 magic 'KVRS', u32 version, u32 slots, u32 slotSize, rest 0) + slots × slotSize
 * </pre>
 *
 * Mod → daemon: HELLO, LAUNCH, CONFIGURE, ACK, POINTER, BUTTON, AXIS, KEY, FOCUS, CLOSE.
 * Daemon → mod: HELLO_OK, STREAM, BUFFER, FRAME, TITLE, CLOSED, ERROR.
 */
public final class DesktopProtocol {

    public static final int VERSION = 1;
    public static final int HEADER = 8;
    public static final int MAX_PAYLOAD = 64 * 1024;
    public static final int MAX_STRING = 4096;
    public static final int MAX_RECTS = 16;
    public static final int SHM_MAGIC = 0x5352564B;
    public static final int SHM_HEADER = 64;
    /** Bytes B, G, R, A per pixel (wl_shm ARGB8888 / XRGB8888 in memory; the daemon forces A = 255 for X). */
    public static final int FORMAT_BGRA = 0;
    public static final int FORMAT_RGBA = 1;
    /** Linux evdev button codes, used on every OS (the Windows backend maps them). */
    public static final int BTN_LEFT = 0x110;
    public static final int BTN_RIGHT = 0x111;
    public static final int BTN_MIDDLE = 0x112;

    public static final int T_HELLO = 1;
    public static final int T_LAUNCH = 2;
    public static final int T_CONFIGURE = 3;
    public static final int T_ACK = 4;
    public static final int T_POINTER = 5;
    public static final int T_BUTTON = 6;
    public static final int T_AXIS = 7;
    public static final int T_KEY = 8;
    public static final int T_FOCUS = 9;
    public static final int T_CLOSE = 10;
    public static final int T_HELLO_OK = 101;
    public static final int T_STREAM = 102;
    public static final int T_BUFFER = 103;
    public static final int T_FRAME = 104;
    public static final int T_TITLE = 105;
    public static final int T_CLOSED = 106;
    public static final int T_ERROR = 107;

    private DesktopProtocol() {}

    public record Rect(int x, int y, int w, int h) {}

    public sealed interface Msg permits Hello, Launch, Configure, Ack, Pointer, Button, Axis, Key, Focus, Close,
        HelloOk, Stream, Buffer, Frame, Title, Closed, Error {}

    public record Hello(int version, String token) implements Msg {}
    public record Launch(int req, List<String> argv) implements Msg {}
    /** Size of the app surface in pixels; fps 0 = paused (minimised, HUD off). */
    public record Configure(int stream, int width, int height, int fps, boolean visible) implements Msg {}
    public record Ack(int stream, int generation, int slot) implements Msg {}
    public record Pointer(int stream, float x, float y) implements Msg {}
    public record Button(int stream, int button, boolean pressed) implements Msg {}
    /** Wheel notches; +dy = content moves up (Minecraft convention), the daemon flips for Wayland. */
    public record Axis(int stream, float dx, float dy) implements Msg {}
    /** GLFW scancode (Linux: evdev + 8 = xkb keycode) and GLFW key for the Windows backend. */
    public record Key(int stream, int scancode, int glfwKey, boolean pressed) implements Msg {}
    public record Focus(int stream, boolean focused) implements Msg {}
    public record Close(int stream) implements Msg {}
    public record HelloOk(int version, int caps) implements Msg {}
    /** New toplevel; {@code req} = the LAUNCH it belongs to, 0 = dialog / extra window of a running app. */
    public record Stream(int stream, int req, String title, String appId) implements Msg {}
    /** (Re)created shared-memory file for a stream; every slot of it is free. */
    public record Buffer(int stream, int generation, String path, int slots, int slotSize) implements Msg {}
    public record Frame(int stream, int generation, int slot, long seq, int width, int height, int stride, int format, List<Rect> damage) implements Msg {}
    public record Title(int stream, String title) implements Msg {}
    public record Closed(int stream, String reason) implements Msg {}
    public record Error(int req, String message) implements Msg {}

    // --- encode --------------------------------------------------------------------------------

    public static byte[] encode(Msg m) {
        ByteBuffer p = ByteBuffer.allocate(MAX_PAYLOAD).order(ByteOrder.LITTLE_ENDIAN);
        int type;
        // instanceof chain, not a pattern switch: the 1.20.1 node compiles with Java 17
        if (m instanceof Hello h) {
            p.putShort((short) h.version());
            str(p, h.token());
            type = T_HELLO;
        } else if (m instanceof Launch l) {
            p.putInt(l.req());
            p.putShort((short) l.argv().size());
            for (String a : l.argv()) str(p, a);
            type = T_LAUNCH;
        } else if (m instanceof Configure c) {
            p.putInt(c.stream()).putShort((short) c.width()).putShort((short) c.height());
            p.put((byte) c.fps()).put((byte) (c.visible() ? 1 : 0));
            type = T_CONFIGURE;
        } else if (m instanceof Ack a) {
            p.putInt(a.stream()).putShort((short) a.generation()).put((byte) a.slot());
            type = T_ACK;
        } else if (m instanceof Pointer pt) {
            p.putInt(pt.stream()).putFloat(pt.x()).putFloat(pt.y());
            type = T_POINTER;
        } else if (m instanceof Button b) {
            p.putInt(b.stream()).putInt(b.button()).put((byte) (b.pressed() ? 1 : 0));
            type = T_BUTTON;
        } else if (m instanceof Axis a) {
            p.putInt(a.stream()).putFloat(a.dx()).putFloat(a.dy());
            type = T_AXIS;
        } else if (m instanceof Key k) {
            p.putInt(k.stream()).putInt(k.scancode()).putInt(k.glfwKey()).put((byte) (k.pressed() ? 1 : 0));
            type = T_KEY;
        } else if (m instanceof Focus f) {
            p.putInt(f.stream()).put((byte) (f.focused() ? 1 : 0));
            type = T_FOCUS;
        } else if (m instanceof Close c) {
            p.putInt(c.stream());
            type = T_CLOSE;
        } else if (m instanceof HelloOk h) {
            p.putShort((short) h.version()).putInt(h.caps());
            type = T_HELLO_OK;
        } else if (m instanceof Stream s) {
            p.putInt(s.stream()).putInt(s.req());
            str(p, s.title());
            str(p, s.appId());
            type = T_STREAM;
        } else if (m instanceof Buffer b) {
            p.putInt(b.stream()).putShort((short) b.generation());
            str(p, b.path());
            p.put((byte) b.slots()).putInt(b.slotSize());
            type = T_BUFFER;
        } else if (m instanceof Frame f) {
            p.putInt(f.stream()).putShort((short) f.generation()).put((byte) f.slot()).putInt((int) f.seq());
            p.putShort((short) f.width()).putShort((short) f.height()).putInt(f.stride()).put((byte) f.format());
            int n = Math.min(MAX_RECTS, f.damage().size());
            p.put((byte) n);
            for (int i = 0; i < n; i++) {
                Rect r = f.damage().get(i);
                p.putShort((short) r.x()).putShort((short) r.y()).putShort((short) r.w()).putShort((short) r.h());
            }
            type = T_FRAME;
        } else if (m instanceof Title t) {
            p.putInt(t.stream());
            str(p, t.title());
            type = T_TITLE;
        } else if (m instanceof Closed c) {
            p.putInt(c.stream());
            str(p, c.reason());
            type = T_CLOSED;
        } else if (m instanceof Error e) {
            p.putInt(e.req());
            str(p, e.message());
            type = T_ERROR;
        } else {
            throw new IllegalArgumentException("unknown message " + m);
        }
        int len = p.position();
        ByteBuffer out = ByteBuffer.allocate(HEADER + len).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) type).putShort((short) 0).putInt(len);
        out.put(p.array(), 0, len);
        return out.array();
    }

    private static void str(ByteBuffer p, String s) {
        byte[] b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        int n = Math.min(MAX_STRING, b.length);
        p.putShort((short) n);
        p.put(b, 0, n);
    }

    // --- decode --------------------------------------------------------------------------------

    /** @param payload little-endian, positioned at the payload start; throws on truncated/unknown data */
    public static Msg decode(int type, ByteBuffer payload) {
        ByteBuffer p = payload.order(ByteOrder.LITTLE_ENDIAN);
        return switch (type) {
            case T_HELLO -> new Hello(u16(p), str(p));
            case T_LAUNCH -> {
                int req = p.getInt();
                int n = u16(p);
                if (n > 64) throw new IllegalArgumentException("argv too long");
                List<String> argv = new ArrayList<>(n);
                for (int i = 0; i < n; i++) argv.add(str(p));
                yield new Launch(req, List.copyOf(argv));
            }
            case T_CONFIGURE -> new Configure(p.getInt(), u16(p), u16(p), u8(p), u8(p) != 0);
            case T_ACK -> new Ack(p.getInt(), u16(p), u8(p));
            case T_POINTER -> new Pointer(p.getInt(), p.getFloat(), p.getFloat());
            case T_BUTTON -> new Button(p.getInt(), p.getInt(), u8(p) != 0);
            case T_AXIS -> new Axis(p.getInt(), p.getFloat(), p.getFloat());
            case T_KEY -> new Key(p.getInt(), p.getInt(), p.getInt(), u8(p) != 0);
            case T_FOCUS -> new Focus(p.getInt(), u8(p) != 0);
            case T_CLOSE -> new Close(p.getInt());
            case T_HELLO_OK -> new HelloOk(u16(p), p.getInt());
            case T_STREAM -> new Stream(p.getInt(), p.getInt(), str(p), str(p));
            case T_BUFFER -> new Buffer(p.getInt(), u16(p), str(p), u8(p), p.getInt());
            case T_FRAME -> {
                int stream = p.getInt();
                int gen = u16(p);
                int slot = u8(p);
                long seq = p.getInt() & 0xFFFFFFFFL;
                int w = u16(p);
                int h = u16(p);
                int stride = p.getInt();
                int format = u8(p);
                int n = Math.min(MAX_RECTS, u8(p));
                List<Rect> damage = new ArrayList<>(n);
                for (int i = 0; i < n; i++) damage.add(new Rect(u16(p), u16(p), u16(p), u16(p)));
                yield new Frame(stream, gen, slot, seq, w, h, stride, format, List.copyOf(damage));
            }
            case T_TITLE -> new Title(p.getInt(), str(p));
            case T_CLOSED -> new Closed(p.getInt(), str(p));
            case T_ERROR -> new Error(p.getInt(), str(p));
            default -> throw new IllegalArgumentException("unknown message type " + type);
        };
    }

    private static int u8(ByteBuffer p) {
        return p.get() & 0xFF;
    }

    private static int u16(ByteBuffer p) {
        return p.getShort() & 0xFFFF;
    }

    private static String str(ByteBuffer p) {
        int n = u16(p);
        if (n > MAX_STRING || n > p.remaining()) throw new IllegalArgumentException("bad string length " + n);
        byte[] b = new byte[n];
        p.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Validates the 64-byte header of a stream's shared-memory file; returns {slots, slotSize}. */
    public static int[] readShmHeader(ByteBuffer file) {
        ByteBuffer h = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (h.getInt(0) != SHM_MAGIC) throw new IllegalArgumentException("not a kvr-desktopd buffer");
        if (h.getInt(4) != VERSION) throw new IllegalArgumentException("buffer version " + h.getInt(4));
        int slots = h.getInt(8);
        int slotSize = h.getInt(12);
        if (slots < 1 || slots > 8 || slotSize <= 0 || (long) SHM_HEADER + (long) slots * slotSize > file.capacity()) {
            throw new IllegalArgumentException("bad buffer layout " + slots + "x" + slotSize);
        }
        return new int[] {slots, slotSize};
    }
}
