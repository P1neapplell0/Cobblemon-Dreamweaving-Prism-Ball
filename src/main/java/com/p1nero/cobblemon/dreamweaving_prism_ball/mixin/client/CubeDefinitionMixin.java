package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PerFaceUvSupport;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CubeDefinition.class)
public abstract class CubeDefinitionMixin {
    @Inject(method = "bake", at = @At("RETURN"))
    private void dreamweaving$applyPerFaceUv(int textureWidth, int textureHeight,
                                              CallbackInfoReturnable<ModelPart.Cube> cir) {
        PerFaceUvSupport.applyFaces((CubeDefinition) (Object) this, cir.getReturnValue(), textureWidth, textureHeight);
    }
}
