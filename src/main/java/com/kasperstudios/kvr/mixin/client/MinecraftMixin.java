package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clicks on a panel are already consumed at tick start by {@code SpatialInput}; this stops the
 * <em>held</em> buttons from mining or using items in the world behind the panel.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    /** Passing {@code false} lets vanilla call stopDestroyBlock() cleanly instead of freezing progress. */
    @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
    private boolean kvr$noMiningThroughPanels(boolean leftClick) {
        return leftClick && !(KVRSpatial.ready() && KVRSpatial.input().capturesMouse());
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void kvr$noUseThroughPanels(CallbackInfo ci) {
        if (KVRSpatial.ready() && KVRSpatial.input().capturesMouse()) ci.cancel();
    }
}
