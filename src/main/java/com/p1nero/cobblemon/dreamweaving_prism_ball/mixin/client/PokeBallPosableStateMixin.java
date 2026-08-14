package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.client.render.pokeball.PokeBallPosableState;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PrismBallModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PokeBallPosableState.class, remap = false)
public abstract class PokeBallPosableStateMixin {
    @Inject(method = "getGroup", at = @At("HEAD"), cancellable = true)
    private void dreamweaving$usePrismBallAnimationGroup(CallbackInfoReturnable<String> cir) {
        PokeBallPosableState state = (PokeBallPosableState) (Object) this;
        if (state.getCurrentModel() instanceof PrismBallModel prismBallModel) {
            cir.setReturnValue(prismBallModel.getAnimationGroup());
        }
    }
}
