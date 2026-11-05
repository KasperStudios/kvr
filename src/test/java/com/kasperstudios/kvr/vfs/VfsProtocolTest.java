package com.kasperstudios.kvr.vfs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class VfsProtocolTest {

    @Test
    void roundTripRequest() {
        VfsProtocol.Request req = new VfsProtocol.Request(42, VfsProtocol.Op.WRITE, "notes", "/home/todo.txt", "diamonds at -58");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        req.write(buf);
        VfsProtocol.Request read = VfsProtocol.Request.read(buf);
        assertEquals(req, read);
    }

    @Test
    void roundTripReply() {
        VfsProtocol.Reply rep = new VfsProtocol.Reply(42, true, "", "{\"ok\":true}");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        rep.write(buf);
        VfsProtocol.Reply read = VfsProtocol.Reply.read(buf);
        assertEquals(rep, read);
    }
}
