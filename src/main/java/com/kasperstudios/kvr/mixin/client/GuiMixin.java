package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.input.SpatialCursor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In Alt mode, crosshair detaches from the screen centre and renders freely at the pointer ray coordinates!
 * Concept: "Визуальный курсор: Дефолтный ванильный прицел Minecraft (крестик crosshair), который отрывается от центра и летает за рукой."
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    //? if >=1.21 {
    private static final ResourceLocation CROSSHAIR_TEXTURE = ResourceLocation.withDefaultNamespace("hud/crosshair");
    //? } else {
    /*private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");
    *///? }

    // 1.21 replaced float partialTick with DeltaTracker in renderCrosshair
    //? if <1.21 {
    /*@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void kvr$renderDetachedCrosshair(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        doRenderDetached(graphics, ci);
    }
    *///?} else {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void kvr$renderDetachedCrosshair(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        doRenderDetached(graphics, ci);
    }
    //?}

    private static void doRenderDetached(GuiGraphics graphics, CallbackInfo ci) {
        if (!KVRSpatial.ready()) return;
        SpatialCursor cursor = KVRSpatial.cursor();
        if (!cursor.active()) return;

        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // Compute detached screen position from yaw/pitch offset
        double halfY = mc.options.fov().get() / 2.0;
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfX = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfY)) * aspect));

        float normX = cursor.yawOffset() / (float) halfX;
        float normY = cursor.pitchOffset() / (float) halfY;

        int cx = (int) (screenW / 2.0f + normX * (screenW / 2.0f));
        int cy = (int) (screenH / 2.0f + normY * (screenH / 2.0f));

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        //? if >=1.21 {
        graphics.blitSprite(CROSSHAIR_TEXTURE, cx - 7, cy - 7, 15, 15);
        //? } else {
        /*graphics.blit(GUI_ICONS_LOCATION, cx - 7, cy - 7, 0, 0, 15, 15);
        *///? }
        RenderSystem.defaultBlendFunc();
    }
}
