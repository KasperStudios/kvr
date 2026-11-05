package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In Alt mode the pointer is drawn in the world where the ray touches a panel
 * ({@code SpatialRenderer}), so the screen-centre crosshair is hidden.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void kvr$hideCentreCrosshair(CallbackInfo ci) {
        if (KVRSpatial.ready() && KVRSpatial.cursor().active()) ci.cancel();
    }
}
