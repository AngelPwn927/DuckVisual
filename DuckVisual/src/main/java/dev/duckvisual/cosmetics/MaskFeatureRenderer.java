package dev.duckvisual.cosmetics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Draws the mask as a box a bit larger than the head (over the hat layer, under a helmet). */
public class MaskFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final ModelPart MASK = build();

    public MaskFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    private static ModelPart build() {
        ModelData data = new ModelData();
        data.getRoot().addChild("mask", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8, new Dilation(0.62f)), ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 16).createModel().getChild("mask");
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean self = mc.player != null && state.id == mc.player.getId();
        Identifier tex = CosmeticsManager.mask(state.name, self);
        if (tex == null) return;
        matrices.push();
        getContextModel().head.rotate(matrices);
        MASK.render(matrices, vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(tex)), light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
