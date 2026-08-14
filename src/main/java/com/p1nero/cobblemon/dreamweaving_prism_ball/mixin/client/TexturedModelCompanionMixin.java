package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PerFaceUvSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel$Companion", remap = false)
public abstract class TexturedModelCompanionMixin {
    @ModifyVariable(method = "from", at = @At("HEAD"), argsOnly = true)
    private String dreamweaving$capturePerFaceUv(String json) {
        return PerFaceUvSupport.captureAndMakeCobblemonCompatible(json);
    }

    @Inject(method = "from", at = @At("RETURN"))
    private void dreamweaving$bindPerFaceUv(String json, CallbackInfoReturnable<TexturedModel> cir) {
        PerFaceUvSupport.bindParsedFaces(cir.getReturnValue());
    }
}
