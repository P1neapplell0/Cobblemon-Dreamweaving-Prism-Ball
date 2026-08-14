package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import com.p1nero.cobblemon.dreamweaving_prism_ball.client.model.PerFaceUvSupport;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(CubeListBuilder.class)
public abstract class CubeListBuilderMixin {
    @Inject(method = "addBox(FFFFFFLnet/minecraft/client/model/geom/builders/CubeDeformation;)Lnet/minecraft/client/model/geom/builders/CubeListBuilder;", at = @At("RETURN"))
    private void dreamweaving$attachPerFaceUv(float x, float y, float z, float width, float height, float depth,
                                               CubeDeformation deformation, CallbackInfoReturnable<CubeListBuilder> cir) {
        List<CubeDefinition> cubes = cir.getReturnValue().getCubes();
        PerFaceUvSupport.attachNextDefinition(cubes.getLast());
    }
}
