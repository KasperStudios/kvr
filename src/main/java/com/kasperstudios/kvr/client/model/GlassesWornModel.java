package com.kasperstudios.kvr.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class GlassesWornModel<T extends LivingEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("kvr", "glasses_worn"), "main");

    public final ModelPart armorHead;
    public final ModelPart shell;
    public final ModelPart glass;
    public final ModelPart sensors;
    public final ModelPart strap;

    public GlassesWornModel(ModelPart root) {
        this.armorHead = root.getChild("armorHead");
        this.shell = this.armorHead.getChild("shell");
        this.glass = this.armorHead.getChild("glass");
        this.sensors = this.armorHead.getChild("sensors");
        this.strap = this.armorHead.getChild("strap");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition armorHead = partdefinition.addOrReplaceChild("armorHead", 
            CubeListBuilder.create(), 
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // shell: box 1: [-4.5, -6.5, -5] size [9, 4, 1]
        //        box 2: [-4.5, -2.5, -5.5] size [9, 1, 2]
        //        box 3: [4, -6, -5] size [1, 3, 4]
        //        box 4: [-5, -6, -5] size [1, 3, 4]
        armorHead.addOrReplaceChild("shell", 
            CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -6.5F, -5.0F, 9.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 5).addBox(-4.5F, -2.5F, -5.5F, 9.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(30, 0).addBox(4.0F, -6.0F, -5.0F, 1.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(30, 0).mirror().addBox(-5.0F, -6.0F, -5.0F, 1.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // glass: [-4, -6, -5.5] size [8, 3, 1]
        armorHead.addOrReplaceChild("glass", 
            CubeListBuilder.create()
                .texOffs(20, 0).addBox(-4.0F, -6.0F, -5.5F, 8.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // sensors: [-3.5, -2.5, -6] size [1, 1, 1], [2.5, -2.5, -6] size [1, 1, 1]
        armorHead.addOrReplaceChild("sensors", 
            CubeListBuilder.create()
                .texOffs(0, 8).addBox(-3.5F, -2.5F, -6.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(0, 8).addBox(2.5F, -2.5F, -6.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // strap: [4, -5.5, -1] size [1, 2, 5]
        //        [-5, -5.5, -1] size [1, 2, 5]
        //        [-5, -5.5, 4] size [10, 2, 1]
        armorHead.addOrReplaceChild("strap", 
            CubeListBuilder.create()
                .texOffs(36, 0).addBox(4.0F, -5.5F, -1.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).mirror().addBox(-5.0F, -5.5F, -1.0F, 1.0F, 2.0F, 5.0F, new CubeDeformation(0.0F))
                .texOffs(43, 0).addBox(-5.0F, -5.5F, 4.0F, 10.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        armorHead.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
