package com.kasperstudios.kvr.hack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class WindowSummaryTest {

    private static WindowSummary.Entry entry(String title, String text) {
        return new WindowSummary.Entry(title, 0, 1.5, 64.25, -3.75, 90f, -10f, 320, 200, false, text);
    }

    @Test
    void roundTrip() {
        WindowSummary s = new WindowSummary(63, List.of(entry("coords.txt", "X: -1284 Y: -54 Z: 312")));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        s.write(buf);
        WindowSummary r = WindowSummary.read(buf);
        assertEquals(s, r);
    }

    @Test
    void publicViewDropsMinimisedAndFarWindowsAndHidesBattery() {
        WindowSummary s = new WindowSummary(63, List.of(
            new WindowSummary.Entry("near", 0, 1, 65, 1, 0f, 0f, 320, 200, false, "a"),
            new WindowSummary.Entry("minimised", 0, 1, 65, 1, 0f, 0f, 320, 200, true, "b"),
            new WindowSummary.Entry("planted far away", 0, 500, 65, 0, 0f, 0f, 320, 200, false, "c")));
        WindowSummary v = s.publicView(0, 65.6, 0, 48);
        assertEquals(0, v.battery());
        assertEquals(1, v.windows().size());
        assertEquals("near", v.windows().get(0).title());
    }

    @Test
    void limitsAreEnforced() {
        List<WindowSummary.Entry> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(entry("t".repeat(200), "x".repeat(1000)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new WindowSummary(250, many).write(buf);
        WindowSummary r = WindowSummary.read(buf);
        assertEquals(100, r.battery());
        assertEquals(WindowSummary.MAX_WINDOWS, r.windows().size());
        assertTrue(r.windows().get(0).title().length() <= WindowSummary.MAX_TITLE);
        assertTrue(r.windows().get(0).text().length() <= WindowSummary.MAX_TEXT);
    }
}
