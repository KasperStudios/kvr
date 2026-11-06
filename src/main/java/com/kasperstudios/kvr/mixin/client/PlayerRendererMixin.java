package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.client.model.GlassesWornModel;
import com.kasperstudios.kvr.client.renderer.GlassesHeadLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kvr$attachGlassesLayer(EntityRendererProvider.Context context, boolean useSlimModel, CallbackInfo ci) {
        GlassesWornModel<AbstractClientPlayer> glassesModel = new GlassesWornModel<>(context.bakeLayer(GlassesWornModel.LAYER_LOCATION));
        this.addLayer(new GlassesHeadLayer<>(this, glassesModel));
    }
}
