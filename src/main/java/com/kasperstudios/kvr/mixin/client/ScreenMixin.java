package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.vanilla.SpatialScreenProxy;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A detached screen paints only itself into the window — no dim gradient, no world blur. */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void kvr$noBackgroundWhenDetached(CallbackInfo ci) {
        if (SpatialScreenProxy.isPainting((Screen) (Object) this)) ci.cancel();
    }
}
