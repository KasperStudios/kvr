package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
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
 * Positions the player's right arm to follow the spatial Alt pointer / cursor in 1st & 3rd person.
 * Concept: "Альт режим это когда на альт нажал и переходишь в режим который правую руку за курсором водит"
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
        if (entity != mc.player) return;

        SpatialCursor cursor = KVRSpatial.cursor();
        if (!cursor.active()) return;

        // When Alt mode is active, the right arm aims towards the cursor ray
        float targetPitch = (float) Math.toRadians(headPitch + cursor.pitchOffset() - 90.0f);
        float targetYaw = (float) Math.toRadians(netHeadYaw + cursor.yawOffset());

        this.rightArm.xRot = targetPitch;
        this.rightArm.yRot = targetYaw;
        this.rightArm.zRot = 0.0f;
    }
}
