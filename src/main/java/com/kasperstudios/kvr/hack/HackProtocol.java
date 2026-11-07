package com.kasperstudios.kvr.hack;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Protocol payloads for HackGlasses Part 2 (MITM Session Interception & Click Injection).
 */
public final class HackProtocol {

    public static final ResourceLocation C2S_HACK_INPUT = id("hack_input");
    public static final ResourceLocation S2C_INJECT_INPUT = id("inject_input");
    /** Server -> hacker: who in range wears KVR glasses (detection, SPEC 4 "Rentgen"). */
    public static final ResourceLocation S2C_HACK_TARGETS = id("hack_targets");

    public enum Action {
        CLICK,
        RELEASE,
        CHAR
    }

    /** One detected victim: enough to draw a marker in the world. */
    public record Target(UUID id, String name, double x, double y, double z) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, 32);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
        }

        public static Target read(FriendlyByteBuf buf) {
            return new Target(buf.readUUID(), buf.readUtf(32), buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }

    /**
     * Hacker client -> Server -> Victim client input injection payload.
     */
    public record InputInjection(
        UUID victim,
        int windowIndex,
        String windowTitle,
        Action action,
        double px,
        double py,
        int buttonOrChar
    ) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(victim);
            buf.writeVarInt(windowIndex);
            buf.writeUtf(windowTitle, 64);
            buf.writeVarInt(action.ordinal());
            buf.writeDouble(px);
            buf.writeDouble(py);
            buf.writeVarInt(buttonOrChar);
        }

        public static InputInjection read(FriendlyByteBuf buf) {
            UUID victim = buf.readUUID();
            int windowIndex = buf.readVarInt();
            String windowTitle = buf.readUtf(64);
            int actionOrd = buf.readVarInt();
            Action action = Action.values()[Math.max(0, Math.min(Action.values().length - 1, actionOrd))];
            double px = buf.readDouble();
            double py = buf.readDouble();
            int buttonOrChar = buf.readVarInt();
            return new InputInjection(victim, windowIndex, windowTitle, action, px, py, buttonOrChar);
        }
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }

    private HackProtocol() {}
}
