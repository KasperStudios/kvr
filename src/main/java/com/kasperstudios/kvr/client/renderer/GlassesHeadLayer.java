package com.kasperstudios.kvr.client.renderer;

import com.kasperstudios.kvr.client.model.GlassesWornModel;
import com.kasperstudios.kvr.items.KVRContent;
import com.kasperstudios.kvr.power.GlassesPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class GlassesHeadLayer<T extends LivingEntity, M extends EntityModel<T> & HeadedModel> extends RenderLayer<T, M> {
    private static final ResourceLocation GLASSES_TEX = ResourceLocation.fromNamespaceAndPath("kvr", "textures/armor/glasses_worn.png");
    private static final ResourceLocation HACK_TEX = ResourceLocation.fromNamespaceAndPath("kvr", "textures/armor/hackglasses_worn.png");

    private final GlassesWornModel<T> model;

    public GlassesHeadLayer(RenderLayerParent<T, M> parent, GlassesWornModel<T> model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = livingEntity.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty() || !GlassesPower.isGlasses(stack)) {
            return;
        }

        boolean isHack = stack.is(KVRContent.item("hackglasses"));
        ResourceLocation tex = isHack ? HACK_TEX : GLASSES_TEX;

        poseStack.pushPose();
        // Attach to parent entity head bone!
        this.getParentModel().getHead().translateAndRotate(poseStack);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(tex));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        poseStack.popPose();
    }
}
