package com.kasperstudios.kvr.mixin.client;

//? if <1.21.2 {
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.input.SpatialCursor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * Positions the player's right arm to follow the spatial Alt pointer / cursor in 1st & 3rd person.
 * Concept: "Альт режим это когда на альт нажал и переходишь в режим который правую руку за курсором водит"
 *
 * <p>The Creator's file (received with 0020). Needs {@code SpatialCursor.yawOffset()/pitchOffset()}.
 *
 * <p>0026, versions: up to 1.21.1 {@code PlayerModel<T extends LivingEntity>} animates from the entity.
 * From 1.21.2 models are not generic over entities any more ({@code PlayerModel extends
 * HumanoidModel<PlayerRenderState>}, {@code setupAnim(state)}), so the old class header does not even compile
 * there. On our 1.21.11 node the client mixins are skipped (no render backend), so that branch is an empty
 * placeholder on a stable target — the mixin config keeps the same entry on every node.
 * <b>Agent:</b> your version with the remote-arm sync (HandPoseClient) goes into the first branch as is.
 */
//? if <1.21.2 {
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {

    public PlayerModelMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void kvr$aimArmAtCursor(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!KVRSpatial.ready()) return;
        Minecraft mc = Minecraft.getInstance();

        float targetPitch;
        float targetYaw;

        if (entity == mc.player) {
            SpatialCursor cursor = KVRSpatial.cursor();
            if (!cursor.active()) return;
            targetPitch = (float) Math.toRadians(headPitch + cursor.pitchOffset() - 90.0f);
            targetYaw = (float) Math.toRadians(netHeadYaw + cursor.yawOffset());
        } else {
            // Remote player sync over network (ADR-001)
            var pose = com.kasperstudios.kvr.spatial.input.HandPose.get(entity.getId());
            if (pose == null || !pose.active()) return;
            targetPitch = (float) Math.toRadians(headPitch + pose.pitch() - 90.0f);
            targetYaw = (float) Math.toRadians(netHeadYaw + pose.yaw());
        }

        this.rightArm.xRot = targetPitch;
        this.rightArm.yRot = targetYaw;
        this.rightArm.zRot = 0.0f;
    }
}
//?} else {
/*// placeholder: render-state models (1.21.2+) need a new arm hook, written together with the 1.21.11 render backend
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PlayerModelMixin {
}
*///?}
