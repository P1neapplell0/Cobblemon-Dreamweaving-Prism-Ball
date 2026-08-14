package com.p1nero.cobblemon.dreamweaving_prism_ball.client.render;

import com.cobblemon.mod.common.client.entity.EmptyPokeBallClientDelegate;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Renders the temporary expanding prism and firework rays used by this mod's capture balls. */
public final class PrismCaptureEffectRenderer {
    private static final int REULEAUX_SUBDIVISIONS = 10;
    private static final float REULEAUX_RENDER_SCALE = 0.96F;
    private static final float SURFACE_ALPHA = 0.450F;
    private static final float SURFACE_COLOR_FLOOR = 0.12F;
    private static final float SURFACE_COLOR_SCALE = 0.88F;
    private static final Vec3 FALLBACK_CORE_OFFSET = new Vec3(0.0, 0.28, 0.0);

    private static final Vector3f[] TETRAHEDRON_VERTICES = {
            new Vector3f(1.0F, 1.0F, 1.0F).normalize(),
            new Vector3f(1.0F, -1.0F, -1.0F).normalize(),
            new Vector3f(-1.0F, 1.0F, -1.0F).normalize(),
            new Vector3f(-1.0F, -1.0F, 1.0F).normalize()
    };

    private static final MeshTriangle[] REULEAUX_MESH = buildReuleauxMesh();

    private static final Vector3f[] RAY_DIRECTIONS = {
            new Vector3f(0.08F, 1.0F, 0.18F),
            new Vector3f(-0.38F, 0.92F, 0.12F),
            new Vector3f(0.46F, 0.86F, -0.18F),
            new Vector3f(-0.64F, 0.58F, -0.22F),
            new Vector3f(0.72F, 0.62F, 0.28F),
            new Vector3f(-0.28F, 0.72F, 0.62F),
            new Vector3f(0.24F, 0.76F, -0.66F),
            new Vector3f(-0.82F, 0.34F, 0.46F),
            new Vector3f(0.86F, 0.30F, -0.42F),
            new Vector3f(0.18F, 0.48F, 0.92F)
    };

    private PrismCaptureEffectRenderer() {
    }

    public static void render(EmptyPokeBallEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource buffer) {
        if (!(entity.getDelegate() instanceof EmptyPokeBallClientDelegate delegate)) return;

        EffectPhase phase = getPhase(delegate);
        if (phase.firstScale() <= 0.001F && phase.secondScale() <= 0.001F) return;

        float age = entity.tickCount + partialTicks;
        float[][] palette = PrismBallColorPalette.INSTANCE.get(entity.getPokeBall().getName());
        Vec3 coreOffset = getCoreOffset(delegate);

        poseStack.pushPose();
        poseStack.translate(coreOffset.x, coreOffset.y, coreOffset.z);
        renderReuleauxPair(poseStack, buffer.getBuffer(PrismBallRenderTypes.capturePrism()),
                phase, age, entity.getId(), palette);
        poseStack.popPose();

        if (phase.rayIntensity() > 0.001F) {
            poseStack.pushPose();
            poseStack.translate(coreOffset.x, coreOffset.y, coreOffset.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 29.0F));
            renderRays(poseStack.last().pose(), buffer.getBuffer(PrismBallRenderTypes.captureRays()),
                    phase.rayIntensity(), palette);
            poseStack.popPose();
        }
    }

    private static Vec3 getCoreOffset(EmptyPokeBallClientDelegate delegate) {
        MatrixWrapper core = delegate.getLocatorStates().get("core_particles");
        if (core == null) return FALLBACK_CORE_OFFSET;
        Vec3 origin = core.getOrigin();
        Vec3 offset = origin.subtract(core.getPosition());
        return Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z)
                ? offset
                : FALLBACK_CORE_OFFSET;
    }

    private static EffectPhase getPhase(EmptyPokeBallClientDelegate delegate) {
        String pose = delegate.getCurrentPose();
        PrimaryAnimation primary = delegate.getPrimaryAnimation();
        boolean transitioning = primary != null && primary.isTransition();

        if (!transitioning && "open".equals(pose)) {
            return new EffectPhase(1.0F, 1.0F, SURFACE_ALPHA, 0.0F);
        }

        // Cobblemon keeps currentPose on the source pose until moveToPose's transition completes.
        if (transitioning && ("flying".equals(pose) || "shut".equals(pose))) {
            float progress = transitionProgress(delegate, primary);
            float firstScale = easeOutBack(progress);
            float secondScale = easeOutBack(delayedProgress(progress, 0.08F));
            float alpha = SURFACE_ALPHA * smootherStep(progress);
            float rays = Mth.sin(Mth.PI * Mth.clamp(progress / 0.82F, 0.0F, 1.0F));
            return new EffectPhase(firstScale, secondScale, alpha, rays);
        }

        if (transitioning && "open".equals(pose)) {
            float progress = transitionProgress(delegate, primary);
            float firstScale = 1.0F - smootherStep(delayedProgress(progress, 0.08F));
            float secondScale = 1.0F - smootherStep(Mth.clamp(progress / 0.92F, 0.0F, 1.0F));
            float remaining = Math.max(firstScale, secondScale);
            float rays = 0.75F * Mth.sin(Mth.PI * progress);
            return new EffectPhase(firstScale, secondScale, SURFACE_ALPHA * remaining, rays);
        }

        return EffectPhase.HIDDEN;
    }

    private static float transitionProgress(EmptyPokeBallClientDelegate delegate, PrimaryAnimation primary) {
        if (primary.getDuration() <= 0.0F) return 1.0F;
        float elapsed = delegate.getAnimationSeconds() - primary.getStarted();
        return Mth.clamp(elapsed / primary.getDuration(), 0.0F, 1.0F);
    }

    private static float smootherStep(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    private static float delayedProgress(float progress, float delay) {
        return Mth.clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F);
    }

    private static float easeOutBack(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F) - 1.0F;
        float overshoot = 1.35F;
        return 1.0F + (overshoot + 1.0F) * t * t * t + overshoot * t * t;
    }

    private static void renderReuleauxPair(PoseStack poseStack, VertexConsumer consumer,
                                           EffectPhase phase, float age, int entityId, float[][] palette) {
        float pulse = 1.0F + 0.025F * Mth.sin(age * 0.42F);
        float seedRotation = entityId * 37.0F;

        if (phase.firstScale() > 0.001F) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(seedRotation + age * 22.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(18.0F + 4.0F * Mth.sin(age * 0.09F)));
            float scale = REULEAUX_RENDER_SCALE * phase.firstScale() * pulse;
            poseStack.scale(scale, scale, scale);
            renderReuleaux(poseStack.last().pose(), consumer, phase.alpha(), palette, false);
            poseStack.popPose();
        }

        if (phase.secondScale() > 0.001F) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - seedRotation - age * 27.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-22.0F - 5.0F * Mth.cos(age * 0.08F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(14.0F));
            float scale = REULEAUX_RENDER_SCALE * 0.94F * phase.secondScale() * pulse;
            poseStack.scale(scale, scale, scale);
            renderReuleaux(poseStack.last().pose(), consumer, phase.alpha() * 0.9F, palette, true);
            poseStack.popPose();
        }
    }

    private static void renderReuleaux(Matrix4f matrix, VertexConsumer consumer,
                                       float alpha, float[][] palette, boolean reverseGradient) {
        for (MeshTriangle meshTriangle : REULEAUX_MESH) {
            gradientVertex(consumer, matrix, meshTriangle.a(), palette, reverseGradient, alpha);
            gradientVertex(consumer, matrix, meshTriangle.b(), palette, reverseGradient, alpha);
            gradientVertex(consumer, matrix, meshTriangle.c(), palette, reverseGradient, alpha);
        }
    }

    private static MeshTriangle[] buildReuleauxMesh() {
        List<MeshTriangle> triangles = new ArrayList<>();
        float edgeLength = new Vector3f(TETRAHEDRON_VERTICES[0])
                .sub(TETRAHEDRON_VERTICES[1]).length();

        for (int face = 0; face < TETRAHEDRON_VERTICES.length; face++) {
            int[] corners = new int[3];
            int cornerIndex = 0;
            for (int vertex = 0; vertex < TETRAHEDRON_VERTICES.length; vertex++) {
                if (vertex != face) corners[cornerIndex++] = vertex;
            }

            Vector3f center = TETRAHEDRON_VERTICES[face];
            Vector3f directionA = new Vector3f(TETRAHEDRON_VERTICES[corners[0]]).sub(center).normalize();
            Vector3f directionB = new Vector3f(TETRAHEDRON_VERTICES[corners[1]]).sub(center).normalize();
            Vector3f directionC = new Vector3f(TETRAHEDRON_VERTICES[corners[2]]).sub(center).normalize();

            for (int row = 0; row < REULEAUX_SUBDIVISIONS; row++) {
                for (int column = 0; column <= row; column++) {
                    Vector3f a = sphericalPoint(center, directionA, directionB, directionC,
                            row, column, edgeLength);
                    Vector3f b = sphericalPoint(center, directionA, directionB, directionC,
                            row + 1, column, edgeLength);
                    Vector3f c = sphericalPoint(center, directionA, directionB, directionC,
                            row + 1, column + 1, edgeLength);
                    triangles.add(new MeshTriangle(a, b, c));

                    if (column < row) {
                        Vector3f d = sphericalPoint(center, directionA, directionB, directionC,
                                row, column + 1, edgeLength);
                        triangles.add(new MeshTriangle(a, c, d));
                    }
                }
            }
        }
        return triangles.toArray(MeshTriangle[]::new);
    }

    private static Vector3f sphericalPoint(Vector3f center,
                                           Vector3f directionA, Vector3f directionB, Vector3f directionC,
                                           int row, int column, float radius) {
        float rowProgress = row / (float) REULEAUX_SUBDIVISIONS;
        float weightA = 1.0F - rowProgress;
        float weightB = (row - column) / (float) REULEAUX_SUBDIVISIONS;
        float weightC = column / (float) REULEAUX_SUBDIVISIONS;
        Vector3f direction = new Vector3f(directionA).mul(weightA)
                .fma(weightB, directionB)
                .fma(weightC, directionC)
                .normalize();
        return new Vector3f(center).fma(radius, direction);
    }

    private static void renderRays(Matrix4f matrix, VertexConsumer consumer,
                                   float intensity, float[][] palette) {
        for (int i = 0; i < RAY_DIRECTIONS.length; i++) {
            Vector3f direction = new Vector3f(RAY_DIRECTIONS[i]).normalize();
            float length = (1.3F + (i % 4) * 0.24F) * intensity;
            Vector3f end = new Vector3f(direction).mul(length);
            Vector3f perpendicular = new Vector3f(direction).cross(0.0F, 1.0F, 0.0F);
            if (perpendicular.lengthSquared() < 0.01F) perpendicular.set(1.0F, 0.0F, 0.0F);
            perpendicular.normalize().mul(0.035F + (i % 3) * 0.008F);
            Vector3f secondPerpendicular = new Vector3f(direction).cross(perpendicular).normalize()
                    .mul(perpendicular.length());
            float[] color = palette[i % palette.length];
            rayTriangle(consumer, matrix, end, perpendicular, color, intensity);
            rayTriangle(consumer, matrix, end, secondPerpendicular, color, intensity);
        }
    }

    private static void rayTriangle(VertexConsumer consumer, Matrix4f matrix, Vector3f end,
                                    Vector3f width, float[] color, float intensity) {
        float alpha = 0.8F * intensity;
        vertex(consumer, matrix, -width.x, -width.y, -width.z,
                1.0F, 1.0F, 1.0F, alpha);
        vertex(consumer, matrix, width.x, width.y, width.z,
                color[0], color[1], color[2], alpha);
        vertex(consumer, matrix, end.x, end.y, end.z,
                color[0], color[1], color[2], 0.04F * intensity);
    }

    private static void gradientVertex(VertexConsumer consumer, Matrix4f matrix, Vector3f point,
                                       float[][] palette, boolean reverse, float alpha) {
        Vector3f direction = new Vector3f(point).normalize();
        float gradient = Mth.clamp(
                0.5F + direction.y * 0.31F + direction.x * 0.13F - direction.z * 0.06F,
                0.0F,
                1.0F
        );
        if (reverse) gradient = 1.0F - gradient;

        float palettePosition = gradient * (palette.length - 1);
        int lower = Mth.floor(palettePosition);
        int upper = Math.min(lower + 1, palette.length - 1);
        float blend = palettePosition - lower;
        float red = brighten(Mth.lerp(blend, palette[lower][0], palette[upper][0]));
        float green = brighten(Mth.lerp(blend, palette[lower][1], palette[upper][1]));
        float blue = brighten(Mth.lerp(blend, palette[lower][2], palette[upper][2]));
        float surfaceAlpha = alpha * (0.88F + 0.12F * Math.abs(direction.y));
        vertex(consumer, matrix, point.x, point.y, point.z, red, green, blue, surfaceAlpha);
    }

    private static float brighten(float color) {
        return Mth.clamp(SURFACE_COLOR_FLOOR + color * SURFACE_COLOR_SCALE, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float x, float y, float z,
                               float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, x, y, z).setColor(red, green, blue, alpha);
    }

    private record MeshTriangle(Vector3f a, Vector3f b, Vector3f c) {
    }

    private record EffectPhase(float firstScale, float secondScale, float alpha, float rayIntensity) {
        private static final EffectPhase HIDDEN = new EffectPhase(0.0F, 0.0F, 0.0F, 0.0F);
    }
}
