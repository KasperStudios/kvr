package com.kasperstudios.kvr.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Same golden bytes as {@code native/kvr-desktopd/src/protocol.rs} tests: both sides must agree byte for byte. */
class DesktopProtocolTest {

    static final String HELLO_HEX = "01000000" + "06000000" + "0100" + "0200" + "6162";
    static final String FRAME_HEX = "68000000" + "1d000000" + "07000000" + "0100" + "02" + "09000000"
        + "0400" + "0200" + "10000000" + "00" + "01" + "0000" + "0000" + "0400" + "0200";

    private static DesktopProtocol.Msg roundTrip(DesktopProtocol.Msg m) {
        byte[] bytes = DesktopProtocol.encode(m);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int type = b.getShort() & 0xFFFF;
        b.getShort();
        int len = b.getInt();
        assertEquals(bytes.length - DesktopProtocol.HEADER, len);
        return DesktopProtocol.decode(type, b.slice());
    }

    @Test
    void goldenBytesMatchTheRustDaemon() {
        assertArrayEquals(HexFormat.of().parseHex(HELLO_HEX), DesktopProtocol.encode(new DesktopProtocol.Hello(1, "ab")));
        DesktopProtocol.Frame f = new DesktopProtocol.Frame(7, 1, 2, 9, 4, 2, 16, DesktopProtocol.FORMAT_BGRA,
            List.of(new DesktopProtocol.Rect(0, 0, 4, 2)));
        assertArrayEquals(HexFormat.of().parseHex(FRAME_HEX), DesktopProtocol.encode(f));
    }

    @Test
    void everyMessageRoundTrips() {
        List<DesktopProtocol.Msg> all = List.of(
            new DesktopProtocol.Hello(1, "токен"),
            new DesktopProtocol.Launch(3, List.of("foot", "--title", "ы")),
            new DesktopProtocol.Configure(5, 960, 600, 30, true),
            new DesktopProtocol.Ack(5, 2, 1),
            new DesktopProtocol.Pointer(5, 12.5f, 300f),
            new DesktopProtocol.Button(5, DesktopProtocol.BTN_LEFT, true),
            new DesktopProtocol.Axis(5, 0f, -1f),
            new DesktopProtocol.Key(5, 38, 65, false),
            new DesktopProtocol.Focus(5, true),
            new DesktopProtocol.Close(5),
            new DesktopProtocol.HelloOk(1, 3),
            new DesktopProtocol.Stream(5, 3, "foot", "foot"),
            new DesktopProtocol.Buffer(5, 2, "/run/user/1000/kvr/stream-5-2.buf", 3, 2_304_000),
            new DesktopProtocol.Frame(5, 2, 0, 4_000_000_000L, 960, 600, 3840, 0, List.of(new DesktopProtocol.Rect(10, 20, 30, 40))),
            new DesktopProtocol.Title(5, "~/kvr"),
            new DesktopProtocol.Closed(5, "app exited"),
            new DesktopProtocol.Error(3, "not found: foo"));
        for (DesktopProtocol.Msg m : all) assertEquals(m, roundTrip(m));
    }

    @Test
    void damageIsCappedAndGarbageIsRejected() {
        List<DesktopProtocol.Rect> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(new DesktopProtocol.Rect(i, i, 1, 1));
        DesktopProtocol.Frame f = (DesktopProtocol.Frame) roundTrip(new DesktopProtocol.Frame(1, 0, 0, 1, 2, 2, 8, 0, many));
        assertEquals(DesktopProtocol.MAX_RECTS, f.damage().size());
        assertThrows(IllegalArgumentException.class, () -> DesktopProtocol.decode(999, ByteBuffer.allocate(4)));
        ByteBuffer lying = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 1).putShort((short) 5000);
        lying.flip();
        assertThrows(IllegalArgumentException.class, () -> DesktopProtocol.decode(DesktopProtocol.T_HELLO, lying));
    }

    @Test
    void shmHeaderIsValidated() {
        ByteBuffer file = ByteBuffer.allocate(DesktopProtocol.SHM_HEADER + 3 * 16).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt(0, DesktopProtocol.SHM_MAGIC).putInt(4, DesktopProtocol.VERSION).putInt(8, 3).putInt(12, 16);
        assertArrayEquals(new int[] {3, 16}, DesktopProtocol.readShmHeader(file));
        file.putInt(12, 1 << 20);
        assertThrows(IllegalArgumentException.class, () -> DesktopProtocol.readShmHeader(file));
    }
}
