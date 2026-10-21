package com.example.realvillagers.client;

import com.example.realvillagers.RealVillager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class RealVillagerRenderer extends HumanoidMobRenderer<RealVillager, RealVillagerModel> {
    private static final ResourceLocation TEX = new ResourceLocation("realvillagers", "textures/entity/real_villager.png");

    public RealVillagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RealVillagerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(RealVillager e) { return TEX; }
}
