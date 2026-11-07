package com.kasperstudios.kvr.desktop;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 0031 additions to the mod ⇄ kvr-desktopd protocol (framing of DESKTOP_PLAN §5: {@code u16 type, u16 flags,
 * u32 len, payload}, little-endian). Self-contained so it can sit next to the existing {@code DesktopProtocol}
 * without touching its message set; the daemon side is {@code native/kvr-desktopd/src/input_0031.rs}.
 *
 * <table>
 *   <tr><th>type</th><th>dir</th><th>payload</th><th>meaning</th></tr>
 *   <tr><td>0x31 KEY</td><td>mod→d</td><td>u32 stream, u16 evdev, u8 pressed</td><td>physical key; daemon's xkb does modifiers/layout/repeat</td></tr>
 *   <tr><td>0x32 KEYS_UP</td><td>mod→d</td><td>u32 stream</td><td>release every key the daemon thinks is down (focus left)</td></tr>
 *   <tr><td>0x33 AXIS</td><td>mod→d</td><td>u32 stream, u8 horizontal, i32 value120, f32 pixels</td><td>wheel, Wayland sign</td></tr>
 *   <tr><td>0x34 CLIP_SET</td><td>mod→d</td><td>u32 n, n bytes UTF-8</td><td>host clipboard → selection of the hidden compositor</td></tr>
 *   <tr><td>0x35 CLIP_CHANGED</td><td>d→mod</td><td>u32 n, n bytes UTF-8</td><td>an app copied text</td></tr>
 *   <tr><td>0x36 KEYMAP</td><td>d→mod</td><td>u8 group, u32 n, n bytes UTF-8 layout name</td><td>active layout (for the window's indicator)</td></tr>
 * </table>
 * VERIFY against your {@code DesktopProtocol}: these ids must not collide with existing types (change the constants
 * here and in {@code input_0031.rs} together if they do).
 */
public final class DesktopInputProtocol {

    public static final int KEY = 0x31;
    public static final int KEYS_UP = 0x32;
    public static final int AXIS = 0x33;
    public static final int CLIP_SET = 0x34;
    public static final int CLIP_CHANGED = 0x35;
    public static final int KEYMAP = 0x36;

    /** Same limit as {@code HostClipboardSync.MAX_CHARS}, in bytes (UTF-8 can be up to 3 bytes per char). */
    public static final int MAX_CLIP_BYTES = 3 << 20;
    private static final int HEADER = 8;

    private DesktopInputProtocol() {}

    public static byte[] key(int stream, int evdev, boolean pressed) {
        ByteBuffer b = frame(KEY, 4 + 2 + 1);
        b.putInt(stream).putShort((short) evdev).put((byte) (pressed ? 1 : 0));
        return b.array();
    }

    public static byte[] keysUp(int stream) {
        ByteBuffer b = frame(KEYS_UP, 4);
        b.putInt(stream);
        return b.array();
    }

    public static byte[] axis(int stream, boolean horizontal, int value120, double pixels) {
        ByteBuffer b = frame(AXIS, 4 + 1 + 4 + 4);
        b.putInt(stream).put((byte) (horizontal ? 1 : 0)).putInt(value120).putFloat((float) pixels);
        return b.array();
    }

    public static byte[] clipSet(String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > MAX_CLIP_BYTES) throw new IllegalArgumentException("clipboard text too long");
        ByteBuffer b = frame(CLIP_SET, 4 + utf8.length);
        b.putInt(utf8.length).put(utf8);
        return b.array();
    }

    /** Payload of {@link #CLIP_CHANGED} (without the 8-byte header). Null if malformed or too big. */
    public static String clipChanged(ByteBuffer payload) {
        ByteBuffer p = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (p.remaining() < 4) return null;
        int n = p.getInt();
        if (n < 0 || n > MAX_CLIP_BYTES || n > p.remaining()) return null;
        byte[] utf8 = new byte[n];
        p.get(utf8);
        return new String(utf8, StandardCharsets.UTF_8);
    }

    /** Payload of {@link #KEYMAP}: active group index and its layout name ("us", "ru" …). Null if malformed. */
    public static Keymap keymap(ByteBuffer payload) {
        ByteBuffer p = payload.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (p.remaining() < 5) return null;
        int group = p.get() & 0xFF;
        int n = p.getInt();
        if (n < 0 || n > 64 || n > p.remaining()) return null;
        byte[] utf8 = new byte[n];
        p.get(utf8);
        return new Keymap(group, new String(utf8, StandardCharsets.UTF_8));
    }

    public record Keymap(int group, String layout) {}

    private static ByteBuffer frame(int type, int len) {
        ByteBuffer b = ByteBuffer.allocate(HEADER + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) type).putShort((short) 0).putInt(len);
        return b;
    }
}
