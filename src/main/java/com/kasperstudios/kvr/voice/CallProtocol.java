package com.kasperstudios.kvr.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Calls (SPEC 6.3): call control C2S, personalised call state S2C. */
public final class CallProtocol {

    public static final ResourceLocation OP = id("call_op");
    public static final ResourceLocation STATE = id("call_state");
    /** Participants including the caller. */
    public static final int MAX_MEMBERS = 8;

    private CallProtocol() {}

    public enum Op { INVITE, ACCEPT, DECLINE, HANGUP, ADD }

    /** From the receiving player's point of view. */
    public enum Status { IDLE, RINGING_IN, RINGING_OUT, ACTIVE, ENDED }

    /** C2S. {@code names} for INVITE / ADD, {@code callId} for the rest. */
    public record Request(Op op, int callId, List<String> names) {
        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(op);
            buf.writeVarInt(callId);
            int n = Math.min(names.size(), MAX_MEMBERS);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeUtf(names.get(i), 16);
        }

        public static Request read(FriendlyByteBuf buf) {
            Op op = buf.readEnum(Op.class);
            int callId = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), MAX_MEMBERS);
            List<String> names = new ArrayList<>(n);
            for (int i = 0; i < n; i++) names.add(buf.readUtf(16));
            return new Request(op, callId, names);
        }
    }

    public record Member(UUID uuid, String name, boolean joined, boolean speaking) {}

    /**
     * S2C. {@code reason} = why it ended (HUNG_UP, DECLINED, NO_ANSWER, MISSED, BUSY, OFFLINE, NO_GLASSES,
     * NO_VOICE, NO_VOICE_CLIENT, LEFT_GROUP, FULL, RATE_LIMIT) or empty.
     */
    public record State(int callId, Status status, String caller, List<Member> members, String reason) {
        public static final State IDLE = new State(0, Status.IDLE, "", List.of(), "");

        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(callId);
            buf.writeEnum(status);
            buf.writeUtf(caller, 16);
            buf.writeVarInt(members.size());
            for (Member m : members) {
                buf.writeUUID(m.uuid());
                buf.writeUtf(m.name(), 16);
                buf.writeBoolean(m.joined());
                buf.writeBoolean(m.speaking());
            }
            buf.writeUtf(reason, 32);
        }

        public static State read(FriendlyByteBuf buf) {
            int callId = buf.readVarInt();
            Status status = buf.readEnum(Status.class);
            String caller = buf.readUtf(16);
            int n = Math.min(buf.readVarInt(), MAX_MEMBERS * 2);
            List<Member> members = new ArrayList<>(n);
            for (int i = 0; i < n; i++) members.add(new Member(buf.readUUID(), buf.readUtf(16), buf.readBoolean(), buf.readBoolean()));
            return new State(callId, status, caller, List.copyOf(members), buf.readUtf(32));
        }

        public boolean inCall() {
            return status == Status.RINGING_IN || status == Status.RINGING_OUT || status == Status.ACTIVE;
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
