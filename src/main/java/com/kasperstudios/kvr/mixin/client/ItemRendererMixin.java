package com.kasperstudios.kvr.mixin.client;

import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Glasses: flat icon in the inventory / hand / on the ground, 3D model only on the head (0020).
 * At the start of {@code ItemRenderer.render(...)} the baked model argument is replaced for
 * {@code ItemDisplayContext.HEAD} — exactly where vanilla swaps the trident / spyglass models for
 * the GUI. Everything else (dye tint, enchantment glint, light) stays vanilla.
 *
 * <p>Only up to 1.21.3: from 1.21.4 items select their model by display context in
 * {@code assets/kvr/items/*.json}, and the client mixins are skipped on 1.21.5+ anyway.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    //? if <1.21.4 {
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
