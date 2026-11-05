package com.kasperstudios.kvr.messenger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * KVR Messenger packets (CONCEPT §1.4 "Zero Discord Needed"): P2P chat relayed by the server,
 * optional file attachment from the sender's {@code /home} delivered into the recipient's
 * {@code /home/inbox/}.
 */
public final class MessengerProtocol {

    public static final ResourceLocation SEND = id("msg_send");
    public static final ResourceLocation ACK = id("msg_ack");
    public static final ResourceLocation RECEIVE = id("msg_recv");
    public static final int MAX_TEXT = 512;

    private MessengerProtocol() {}

    /** C2S. {@code attach} = VFS path under /home, or empty. */
    public record Send(int req, String to, String text, String attach) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeUtf(to, 16);
            buf.writeUtf(text, MAX_TEXT);
            buf.writeUtf(attach, 128);
        }

        public static Send read(FriendlyByteBuf buf) {
            return new Send(buf.readVarInt(), buf.readUtf(16), buf.readUtf(MAX_TEXT), buf.readUtf(128));
        }
    }

    /** S2C to the sender. */
    public record Ack(int req, boolean ok, String error) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeBoolean(ok);
            buf.writeUtf(error, 64);
        }

        public static Ack read(FriendlyByteBuf buf) {
            return new Ack(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64));
        }
    }

    /** S2C to the recipient. {@code file} = path in the recipient's VFS, or empty. */
    public record Receive(String from, String text, String file) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(from, 16);
            buf.writeUtf(text, MAX_TEXT);
            buf.writeUtf(file, 128);
        }

        public static Receive read(FriendlyByteBuf buf) {
            return new Receive(buf.readUtf(16), buf.readUtf(MAX_TEXT), buf.readUtf(128));
        }
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
