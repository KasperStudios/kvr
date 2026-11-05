package com.kasperstudios.kvr.messenger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MessengerProtocolTest {

    @Test
    void sendAckReceiveRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        MessengerProtocol.Send send = new MessengerProtocol.Send(3, "Kasper", "Алмазы тут: X 120 Y -58 Z 44", "/home/coords.txt");
        send.write(buf);
        assertEquals(send, MessengerProtocol.Send.read(buf));

        MessengerProtocol.Ack ack = new MessengerProtocol.Ack(3, false, "RECIPIENT_GLASSES_OFF");
        ack.write(buf);
        assertEquals(ack, MessengerProtocol.Ack.read(buf));

        MessengerProtocol.Receive rec = new MessengerProtocol.Receive("Steve", "hi", "/home/inbox/Steve-coords.txt");
        rec.write(buf);
        assertEquals(rec, MessengerProtocol.Receive.read(buf));
    }

    @Test
    void textLongerThanLimitIsRejectedByTheCodec() {
        // MessengerClient clamps before writing; this documents why it must
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        String tooLong = "x".repeat(MessengerProtocol.MAX_TEXT + 1);
        assertThrows(RuntimeException.class, () -> new MessengerProtocol.Send(1, "a", tooLong, "").write(buf));
    }
}
