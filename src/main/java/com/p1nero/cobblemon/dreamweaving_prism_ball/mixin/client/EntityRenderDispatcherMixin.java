package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    private static void renderShadow(PoseStack poseStack, MultiBufferSource buffer, Entity entity,
                                     float strength, float partialTicks, LevelReader level, float radius) {
        throw new AssertionError();
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"))
    private void dreamweaving$skipDreamweavingPrismBallShadow(PoseStack poseStack, MultiBufferSource buffer,
                                                               Entity entity, float strength, float partialTicks,
                                                               LevelReader level, float radius) {
        if (entity instanceof EmptyPokeBallEntity pokeBallEntity) {
            ResourceLocation id = pokeBallEntity.getPokeBall().getName();
            if ("cobblemon_dreamweaving_prism_ball".equals(id.getNamespace())
                    && ("prism_ball".equals(id.getPath())
                    || "dreamweaving_prism_ball".equals(id.getPath()))) {
                return;
            }
        }
        renderShadow(poseStack, buffer, entity, strength, partialTicks, level, radius);
    }
}
