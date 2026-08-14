package com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.Cube.class)
public interface CubePolygonsAccessor {
    @Accessor("polygons")
    ModelPart.Polygon[] dreamweaving$getPolygons();
}
