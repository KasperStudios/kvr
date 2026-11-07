package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.input.HandPose;
import com.kasperstudios.kvr.spatial.input.SpatialCursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Positions a player's right arm along the spatial Alt pointer (ADR-001).
 *
 * <p>Two sources, one pose:
 * <ul>
 *   <li>the local player reads {@link SpatialCursor} directly (zero latency);</li>
 *   <li>everybody else reads {@link HandPose}, filled by {@code HandPoseRelay} — so the arm motion is
 *       visible to the whole server in multiplayer, not just to the person moving it.</li>
 * </ul>
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    public PlayerModelMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void kvr$aimArmAtCursor(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!KVRSpatial.ready()) return;
        Minecraft mc = Minecraft.getInstance();

        float yawOffset;
        float pitchOffset;
        if (entity == mc.player) {
            SpatialCursor cursor = KVRSpatial.cursor();
            if (!cursor.active()) return;
            yawOffset = cursor.yawOffset();
            pitchOffset = cursor.pitchOffset();
        } else {
            // remote players: only what the server relayed (active = Alt mode is on)
            HandPose.Pose pose = HandPose.get(entity.getId());
            if (!pose.active()) return;
            yawOffset = pose.yaw();
            pitchOffset = pose.pitch();
        }

        this.rightArm.xRot = (float) Math.toRadians(headPitch + pitchOffset - 90.0f);
        this.rightArm.yRot = (float) Math.toRadians(netHeadYaw + yawOffset);
        this.rightArm.zRot = 0.0f;
    }
}
