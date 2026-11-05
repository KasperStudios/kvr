package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class VfsProtocolTest {

    @Test
    void requestAndReplyRoundTrip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        VfsProtocol.Request req = new VfsProtocol.Request(7, VfsProtocol.Op.WRITE, "notes", "/home/coords.txt", "X: 1 Y: 2 Z: 3");
        req.write(buf);
        assertEquals(req, VfsProtocol.Request.read(buf));

        VfsProtocol.Reply rep = new VfsProtocol.Reply(7, false, "QUOTA_FILES", "2/2");
        rep.write(buf);
        assertEquals(rep, VfsProtocol.Reply.read(buf));
    }
}
