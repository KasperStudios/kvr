package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raw mouse for the Alt pointer and the grab depth. Neither loader has a portable event for
 * "mouse moved while no screen is open", so this lives in a common mixin (no loader imports).
 * Handlers take only {@link CallbackInfo}, which also skips the 1.21 {@code turnPlayer(double)}
 * signature seam.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void kvr$routeToPointer(CallbackInfo ci) {
        if (!KVRSpatial.ready()) return;
        if (KVRSpatial.cursor().consumeMouse(accumulatedDX, accumulatedDY)) {
            accumulatedDX = 0;
            accumulatedDY = 0;
            ci.cancel();
        }
    }

    /**
     * 0031: the wheel used to be dropped whenever ANY screen was open — including KVR's invisible keyboard capture of
     * a PC window (D1), i.e. exactly while the player works in that window: "scrolling in host windows is impossible".
     * KVR capture screens ({@link com.kasperstudios.kvr.spatial.input.SpatialCaptureScreen}) now let it through.
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void kvr$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (!KVRSpatial.ready()) return;
        var screen = Minecraft.getInstance().screen;
        if (screen != null && !(screen instanceof com.kasperstudios.kvr.spatial.input.SpatialCaptureScreen)) return;
        if (yOffset != 0 && KVRSpatial.input().scroll(yOffset)) ci.cancel();
    }
}
