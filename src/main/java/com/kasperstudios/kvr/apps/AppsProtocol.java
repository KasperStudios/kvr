package com.kasperstudios.kvr.apps;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * App Manager packets on the channel-based {@code KVRNetwork}. Packages live on the server in the
 * glasses' VFS ({@code /apps/<id>.vrapp}, counted against the Storage tier); the client uploads and
 * downloads them in chunks, because vanilla caps one C2S custom payload at 32 KiB.
 *
 * <pre>
 * LIST                               → json {policy, usage, apps[]}
 * INSTALL_BEGIN  id, sha256, total   → ok          (one upload per player at a time)
 * INSTALL_CHUNK  offset, data        → ok          (next chunk only after the ack: natural flow control)
 * INSTALL_COMMIT                     → json app    (hash, package validation, quota — all on the server)
 * INSTALL_ABORT
 * UNINSTALL      id                  → ok
 * FETCH          id, sha256, offset  → data, offset, total   (client cache miss)
 * </pre>
 */
public final class AppsProtocol {

    public static final ResourceLocation REQUEST = id("apps");
    public static final ResourceLocation REPLY = id("apps_reply");
    /** Upload chunk: under the 32767-byte C2S limit with room for the header. */
    public static final int CHUNK_UP = 30_000;
    /** Download chunk: S2C custom payloads may be up to 1 MiB. */
    public static final int CHUNK_DOWN = 256 * 1024;
    private static final int MAX_JSON = 256 * 1024;

    public enum Op { LIST, INSTALL_BEGIN, INSTALL_CHUNK, INSTALL_COMMIT, INSTALL_ABORT, UNINSTALL, FETCH }

    private AppsProtocol() {}

    public record Request(int req, Op op, String appId, String sha256, int total, int offset, byte[] data) {
        public static Request of(int req, Op op) {
            return new Request(req, op, "", "", 0, 0, new byte[0]);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeEnum(op);
            buf.writeUtf(appId, 64);
            buf.writeUtf(sha256, 64);
            buf.writeVarInt(total);
            buf.writeVarInt(offset);
            buf.writeByteArray(data);
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readEnum(Op.class), buf.readUtf(64), buf.readUtf(64),
                buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_UP));
        }
    }

    /** {@code error}: a {@link VrAppException.Code} name (or a VFS code); {@code json}: op-specific. */
    public record Reply(int req, Op op, boolean ok, String error, String message, String json, int offset, int total, byte[] data) {
        public static Reply ok(Request r, String json) {
            return new Reply(r.req(), r.op(), true, "", "", json, 0, 0, new byte[0]);
        }

        public static Reply fail(Request r, String code, String message) {
            return new Reply(r.req(), r.op(), false, code, message == null ? "" : cut(message, 300), "", 0, 0, new byte[0]);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(req);
            buf.writeEnum(op);
            buf.writeBoolean(ok);
            buf.writeUtf(error, 64);
            buf.writeUtf(message, 300);
            buf.writeUtf(json, MAX_JSON);
            buf.writeVarInt(offset);
            buf.writeVarInt(total);
            buf.writeByteArray(data);
        }

        public static Reply read(FriendlyByteBuf buf) {
            return new Reply(buf.readVarInt(), buf.readEnum(Op.class), buf.readBoolean(), buf.readUtf(64), buf.readUtf(300),
                buf.readUtf(MAX_JSON), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_DOWN));
        }
    }

    static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
