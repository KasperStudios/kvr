package com.kasperstudios.kvr.rednet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Glasses ⇄ server packets of the Rednet bridge. The JSON text of a message is at most
 * {@link RednetPayload#MAX_JSON} bytes; the server re-checks every limit.
 */
public final class RednetProtocol {

    /** C2S: an app sends / broadcasts. */
    public static final ResourceLocation SEND = id("rednet_send");
    /** S2C: a rednet message for these glasses. */
    public static final ResourceLocation RECEIVE = id("rednet_recv");
    /** S2C: modem state (CC present, modem in the slot, id, kind) — on change only. */
    public static final ResourceLocation STATE = id("rednet_state");

    /** rednet.lua constants (CC: Tweaked {@code rom/apis/rednet.lua}). */
    public static final int CHANNEL_BROADCAST = 65535;
    public static final int CHANNEL_REPEAT = 65533;
    public static final int MAX_ID_CHANNELS = 65500;
    public static final int BROADCAST = -1;
    static final int MAX_PROTOCOL = 64;
    /** writeUtf limits are in UTF-8 bytes ×3 worst case — keep the JSON char limit and give the codec room. */
    static final int MAX_JSON_WIRE = RednetPayload.MAX_JSON * 3;

    private RednetProtocol() {}

    /** C2S. {@code to} = computer id or {@link #BROADCAST}; {@code json} = the message as JSON text. */
    public record Send(int to, String protocol, String json) {
        public void write(FriendlyByteBuf buf) {
            buf.writeInt(to);
            buf.writeUtf(protocol, MAX_PROTOCOL);
            buf.writeUtf(json, MAX_JSON_WIRE);
        }

        public static Send read(FriendlyByteBuf buf) {
            return new Send(buf.readInt(), buf.readUtf(MAX_PROTOCOL), buf.readUtf(MAX_JSON_WIRE));
        }
    }

    /** S2C. {@code sender} = rednet id of the sending computer (or glasses). */
    public record Receive(int sender, String protocol, String json, boolean broadcast, int distance) {
        public void write(FriendlyByteBuf buf) {
            buf.writeInt(sender);
            buf.writeUtf(protocol, MAX_PROTOCOL);
            buf.writeUtf(json, MAX_JSON_WIRE);
            buf.writeBoolean(broadcast);
            buf.writeVarInt(distance);
        }

        public static Receive read(FriendlyByteBuf buf) {
            return new Receive(buf.readInt(), buf.readUtf(MAX_PROTOCOL), buf.readUtf(MAX_JSON_WIRE), buf.readBoolean(), buf.readVarInt());
        }
    }

    /** S2C. {@code id} = -1 when the glasses never had a modem. */
    public record State(boolean available, boolean modem, boolean ender, int id) {
        public static final State NONE = new State(false, false, false, -1);

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(available);
            buf.writeBoolean(modem);
            buf.writeBoolean(ender);
            buf.writeInt(id);
        }

        public static State read(FriendlyByteBuf buf) {
            return new State(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readInt());
        }

        /** Online on the network right now. */
        public boolean online() {
            return available && modem;
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
