package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel;
import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PerFaceUvSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TexturedModel.class, remap = false)
public abstract class TexturedModelMixin {
    @Inject(method = "createWithUvOverride", at = @At("HEAD"))
    private void dreamweaving$beginPerFaceUvBake(int xOffset, int yOffset, Integer textureWidth,
                                                  Integer textureHeight, CallbackInfoReturnable<?> cir) {
        PerFaceUvSupport.beginCreate((TexturedModel) (Object) this);
    }

    @Inject(method = "createWithUvOverride", at = @At("RETURN"))
    private void dreamweaving$endPerFaceUvBake(int xOffset, int yOffset, Integer textureWidth,
                                                Integer textureHeight, CallbackInfoReturnable<?> cir) {
        PerFaceUvSupport.endCreate();
    }
}
