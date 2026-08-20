package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.pokeball.PosablePokeBallModel;
import com.cobblemon.mod.common.client.render.pokeball.PokeBallRenderer;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.render.PrismCaptureEffectRenderer;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.render.PrismBallRenderTypes;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.render.VanillaModelVertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PokeBallRenderer.class, remap = false)
public abstract class PokeBallRendererMixin {
    @Unique
    private boolean dreamweaving$fullBright;

    @Unique
    private boolean dreamweaving$translucentEmissive;

    @Inject(method = "render(Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void dreamweaving$selectLighting(EmptyPokeBallEntity entity, float yaw, float partialTicks,
                                              PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              CallbackInfo ci) {
        ResourceLocation id = entity.getPokeBall().getName();
        dreamweaving$fullBright = "cobblemon_dreamweaving_prism_ball".equals(id.getNamespace())
                && ("prism_ball".equals(id.getPath()) || "dreamweaving_prism_ball".equals(id.getPath()));
        dreamweaving$translucentEmissive = dreamweaving$fullBright;
    }

    @Redirect(method = "render(Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutout(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType dreamweaving$useTranslucentEmissive(ResourceLocation texture) {
        return dreamweaving$translucentEmissive
                ? PrismBallRenderTypes.translucentUnlitCull(texture)
                : RenderType.entityCutout(texture);
    }

    @ModifyArg(method = "render(Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/pokeball/PosablePokeBallModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 2)
    private int dreamweaving$useFullBright(int packedLight) {
        return dreamweaving$fullBright ? LightTexture.FULL_BRIGHT : packedLight;
    }

    @ModifyArg(method = "render(Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/pokeball/PosablePokeBallModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 1)
    private VertexConsumer dreamweaving$useVanillaModelPolygons(VertexConsumer consumer) {
        return dreamweaving$fullBright ? new VanillaModelVertexConsumer(consumer) : consumer;
    }

    @Inject(method = "render(Lcom/cobblemon/mod/common/entity/pokeball/EmptyPokeBallEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void dreamweaving$clearLighting(EmptyPokeBallEntity entity, float yaw, float partialTicks,
                                             PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                             CallbackInfo ci) {
        if (dreamweaving$fullBright) {
            PrismCaptureEffectRenderer.render(entity, partialTicks, poseStack, buffer);
        }
        dreamweaving$fullBright = false;
        dreamweaving$translucentEmissive = false;
    }
}
