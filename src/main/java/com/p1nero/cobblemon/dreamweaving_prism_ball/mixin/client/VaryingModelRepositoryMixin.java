package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PrismBallModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryMixin {
    private static final ResourceLocation PRISM_BALL_POSER = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_dreamweaving_prism_ball", "prism_ball"
    );
    private static final ResourceLocation DREAMWEAVING_PRISM_BALL_POSER = ResourceLocation.fromNamespaceAndPath(
            "cobblemon_dreamweaving_prism_ball", "dreamweaving_prism_ball"
    );

    @Inject(method = "registerInBuiltPosers", at = @At("TAIL"))
    private void dreamweaving$registerPrismBallPoser(CallbackInfo ci) {
        VaryingModelRepository.INSTANCE.getPosers().put(
                PRISM_BALL_POSER,
                root -> new PrismBallModel((ModelPart) (Object) root, PrismBallModel.PRISM_ANIMATION_GROUP)
        );
        VaryingModelRepository.INSTANCE.getPosers().put(
                DREAMWEAVING_PRISM_BALL_POSER,
                root -> new PrismBallModel((ModelPart) (Object) root, PrismBallModel.DREAMWEAVING_ANIMATION_GROUP)
        );
    }
}
