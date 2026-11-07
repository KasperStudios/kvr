package com.kasperstudios.kvr.hack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class HackProtocolTest {

    @Test
    void inputInjectionRoundTrip() {
        UUID victim = UUID.randomUUID();
        HackProtocol.InputInjection original = new HackProtocol.InputInjection(
            victim,
            2,
            "Notes",
            HackProtocol.Action.CLICK,
            120.5,
            45.0,
            0
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.write(buf);

        HackProtocol.InputInjection decoded = HackProtocol.InputInjection.read(buf);
        assertEquals(victim, decoded.victim());
        assertEquals(2, decoded.windowIndex());
        assertEquals("Notes", decoded.windowTitle());
        assertEquals(HackProtocol.Action.CLICK, decoded.action());
        assertEquals(120.5, decoded.px(), 1e-4);
        assertEquals(45.0, decoded.py(), 1e-4);
        assertEquals(0, decoded.buttonOrChar());
    }
}
