package com.p1nero.cobblemon.dreamweaving_prism_ball.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** Translucent and unlit while preserving negative-size faces and normal depth occlusion. */
public final class PrismBallRenderTypes {
    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_UNLIT_CULL = Util.memoize(texture -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.CULL)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create(
                "dreamweaving_prism_ball_translucent_unlit_cull",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                RenderType.TRANSIENT_BUFFER_SIZE,
                true,
                true,
                state
        );
    });

    private static final RenderType CAPTURE_PRISM = RenderType.create(
            "dreamweaving_prism_ball_capture_prism",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    private static final RenderType CAPTURE_RAYS = RenderType.create(
            "dreamweaving_prism_ball_capture_rays",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false)
    );

    private PrismBallRenderTypes() {
    }

    public static RenderType translucentUnlitCull(ResourceLocation texture) {
        return TRANSLUCENT_UNLIT_CULL.apply(texture);
    }

    public static RenderType capturePrism() {
        return CAPTURE_PRISM;
    }

    public static RenderType captureRays() {
        return CAPTURE_RAYS;
    }
}
