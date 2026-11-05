package com.kasperstudios.kvr.vfs;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * VFS packets for the channel-based {@code KVRNetwork}: the client (script runtime) asks, the server
 * owns the files. Each request carries an id; the reply echoes it.
 */
public final class VfsProtocol {

    public static final ResourceLocation REQUEST = id("vfs");
    public static final ResourceLocation REPLY = id("vfs_reply");
    private static final int MAX_TEXT = VfsStore.MAX_WRITE_BYTES;
    private static final int MAX_REPLY = VfsStore.MAX_READ_BYTES;

    public enum Op { LIST, READ, WRITE, DELETE, USAGE }

    private VfsProtocol() {}

    public record Request(int req, Op op, String appId, String path, String text) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeEnum(op);
            buf.writeUtf(appId, 64);
            buf.writeUtf(path, VfsPath.MAX_LENGTH);
            buf.writeUtf(text == null ? "" : text, MAX_TEXT);
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readEnum(Op.class), buf.readUtf(64), buf.readUtf(VfsPath.MAX_LENGTH), buf.readUtf(MAX_TEXT));
        }
    }

    /** {@code data}: text for READ, JSON for LIST/USAGE, empty otherwise; {@code error}: a {@link VfsException.Code} name. */
    public record Reply(int req, boolean ok, String error, String data) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeBoolean(ok);
            buf.writeUtf(error == null ? "" : error, 64);
            buf.writeUtf(data == null ? "" : data, MAX_REPLY);
        }

        public static Reply read(FriendlyByteBuf buf) {
            return new Reply(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readUtf(MAX_REPLY));
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
