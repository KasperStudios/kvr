package com.kasperstudios.kvr.spatial.input;

import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import net.minecraft.client.Minecraft;

/**
 * Client half of {@link HandPoseRelay}: reports the local Alt pointer at
 * {@link HandPoseRelay#SEND_TICKS} and stores everybody else's pose in {@link HandPose} so
 * {@code PlayerModelMixin} can pose their arms.
 *
 * <p>Reporting stops (and one final {@code active = false} is sent) when Alt mode ends, so the hand
 * drops immediately instead of hanging in the air until the TTL runs out.
 */
public final class HandPoseClient {

    private static int ticks;
    private static boolean wasActive;

    private HandPoseClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(HandPoseRelay.VIEW, buf -> {
            int entity = buf.readVarInt();
            boolean active = buf.readBoolean();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            Minecraft.getInstance().execute(() -> HandPose.put(entity, new HandPose.Pose(active, yaw, pitch)));
        });
        KVRClient.get().onClientTickStart(HandPoseClient::tick);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            HandPose.clear();
            wasActive = false;
            return;
        }
        if (++ticks % HandPoseRelay.SEND_TICKS != 0) return;

        boolean active = KVRSpatial.ready() && KVRSpatial.cursor().active()
            && mc.screen == null;
        float yaw = active ? KVRSpatial.cursor().yawOffset() : 0f;
        float pitch = active ? KVRSpatial.cursor().pitchOffset() : 0f;
        if (!active && !wasActive) return; // idle and was idle: nothing to say
        wasActive = active;
        KVRNetwork.sendToServer(HandPoseRelay.REPORT, buf -> {
            buf.writeBoolean(active);
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        });
    }
}
