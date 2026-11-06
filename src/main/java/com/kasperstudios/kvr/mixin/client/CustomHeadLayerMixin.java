package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.glasses.client.WornGlassesModels;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the one legitimate place where KVR glasses are drawn on a head (0021): vanilla
 * {@code CustomHeadLayer}. {@code ItemRendererMixin} lets a HEAD-context glasses render through only
 * while this counter is up. A depth counter, not a flag: the generic bridge method may enter twice.
 */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void kvr$enter(CallbackInfo ci) {
        WornGlassesModels.enterHeadLayer();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kvr$exit(CallbackInfo ci) {
        WornGlassesModels.exitHeadLayer();
    }
}
