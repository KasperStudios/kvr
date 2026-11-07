package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.render.RenderCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scissor boxes are computed against the main window (its height and GUI scale) and ignore the pose
 * stack. Inside an offscreen window surface that clips the wrong rectangle — the player model of a
 * detached inventory simply vanished. While a surface paints, scissoring is off; the surface itself is
 * the clip.
 *
 * <p>0026: {@code RenderSystem.disableScissor()} does not exist on 1.21.5+ (GPU device rewrite). There the
 * mixin plugin skips every client mixin anyway, so the body is simply empty on that node — it only has to compile.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "applyScissor", at = @At("HEAD"), cancellable = true)
    private void kvr$noScissorInWindowSurface(@Nullable ScreenRectangle rect, CallbackInfo ci) {
        if (!RenderCompat.paintingSurface()) return;
        //? if <1.21.5 {
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        ci.cancel();
        //?}
    }
}
