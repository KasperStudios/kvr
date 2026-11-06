package com.kasperstudios.kvr.mixin.client;

import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Glasses on the head: flat icon everywhere else, 3D model only on the head (0020), and exactly ONE
 * copy on the head (0021).
 * <ul>
 *   <li>{@code kvr$onlyOneHead}: cancels any render of KVR glasses in HEAD context that does not come
 *       from vanilla {@code CustomHeadLayer}, and any glasses render inside {@code HumanoidArmorLayer}
 *       (an extra layer or a Fabric {@code ArmorRenderer} would draw a second visor).</li>
 *   <li>{@code kvr$wornGlasses}: replaces the baked model with the 3D one for HEAD — exactly where vanilla
 *       swaps the trident / spyglass models for the GUI. Dye tint, glint and light stay vanilla.</li>
 * </ul>
 * Only up to 1.21.3: from 1.21.4 items select their model by display context in
 * {@code assets/kvr/items/*.json}, and the client mixins are skipped on 1.21.5+ anyway.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    //? if <1.21.4 {
    @org.spongepowered.asm.mixin.injection.Inject(method = "render", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void kvr$onlyOneHead(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext context,
                                 boolean leftHand, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                 int light, int overlay, net.minecraft.client.resources.model.BakedModel model,
                                 org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!com.kasperstudios.kvr.glasses.client.WornGlassesModels.allowRender(stack, context)) ci.cancel();
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "render", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true)
    private net.minecraft.client.resources.model.BakedModel kvr$wornGlasses(net.minecraft.client.resources.model.BakedModel model,
                                                                             net.minecraft.world.item.ItemStack stack,
                                                                             net.minecraft.world.item.ItemDisplayContext context) {
        if (context != net.minecraft.world.item.ItemDisplayContext.HEAD) return model;
        Object worn = com.kasperstudios.kvr.glasses.client.WornGlassesModels.forHead(stack);
        return worn instanceof net.minecraft.client.resources.model.BakedModel baked ? baked : model;
    }
    //?}
}
