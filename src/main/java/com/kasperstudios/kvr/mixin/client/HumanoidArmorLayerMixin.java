package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.glasses.client.WornGlassesModels;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Glasses are not armor: while the armor layer renders (including Fabric {@code ArmorRenderer}
 * callbacks it dispatches), {@code ItemRendererMixin} drops every KVR glasses item render (0021:
 * the second visor floating above the head).
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void kvr$enter(CallbackInfo ci) {
        WornGlassesModels.enterArmorLayer();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kvr$exit(CallbackInfo ci) {
        WornGlassesModels.exitArmorLayer();
    }
}
