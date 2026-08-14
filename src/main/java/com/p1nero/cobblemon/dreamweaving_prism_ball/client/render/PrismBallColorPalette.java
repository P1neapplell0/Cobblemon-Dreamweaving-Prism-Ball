package com.p1nero.cobblemon.dreamweaving_prism_ball.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.p1nero.cobblemon.dreamweaving_prism_ball.DreamweavingPrismBallMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Samples capture-effect gradients from the thrown-ball textures on resource reload. */
public final class PrismBallColorPalette implements ResourceManagerReloadListener {
    public static final PrismBallColorPalette INSTANCE = new PrismBallColorPalette();
    private static final Logger LOGGER = LoggerFactory.getLogger(DreamweavingPrismBallMod.ID + "/Palette");
    private static final float MIN_SAMPLE_LUMINANCE = 0.16F;

    private static final ResourceLocation PRISM_BALL = id("prism_ball");
    private static final ResourceLocation DREAMWEAVING_PRISM_BALL = id("dreamweaving_prism_ball");
    private static final Map<ResourceLocation, ResourceLocation> ENTITY_TEXTURES = Map.of(
            PRISM_BALL, entityTexture("prism_ball"),
            DREAMWEAVING_PRISM_BALL, entityTexture("dreamweaving_prism_ball")
    );
    private static final Map<ResourceLocation, float[][]> FALLBACKS = Map.of(
            PRISM_BALL, new float[][]{
                    {0.18F, 0.12F, 0.70F}, {0.12F, 0.78F, 1.00F},
                    {0.85F, 0.20F, 1.00F}, {1.00F, 0.88F, 1.00F}
            },
            DREAMWEAVING_PRISM_BALL, new float[][]{
                    {0.03F, 0.12F, 0.42F}, {0.05F, 0.48F, 0.92F},
                    {0.20F, 0.92F, 1.00F}, {0.90F, 0.98F, 1.00F}
            }
    );

    private volatile Map<ResourceLocation, float[][]> palettes = FALLBACKS;

    private PrismBallColorPalette() {
    }

    public float[][] get(ResourceLocation ballId) {
        return palettes.getOrDefault(ballId, FALLBACKS.get(PRISM_BALL));
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<ResourceLocation, float[][]> sampled = new HashMap<>();
        ENTITY_TEXTURES.forEach((ballId, texture) -> sampled.put(
                ballId, sample(resourceManager, texture, FALLBACKS.get(ballId))
        ));
        palettes = Map.copyOf(sampled);
    }

    private static float[][] sample(ResourceManager resourceManager, ResourceLocation texture,
                                    float[][] fallback) {
        try (InputStream stream = resourceManager.getResourceOrThrow(texture).open();
             NativeImage image = NativeImage.read(stream)) {
            List<float[]> pixels = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int color = image.getPixelRGBA(x, y);
                    if (FastColor.ABGR32.alpha(color) < 128) continue;
                    float red = FastColor.ABGR32.red(color) / 255.0F;
                    float green = FastColor.ABGR32.green(color) / 255.0F;
                    float blue = FastColor.ABGR32.blue(color) / 255.0F;
                    if (luminance(red, green, blue) < MIN_SAMPLE_LUMINANCE) continue;
                    pixels.add(new float[]{red, green, blue});
                }
            }
            return pixels.size() < 4 ? fallback : clusterPalette(pixels, 4);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unable to sample Prism Ball palette from {}", texture, exception);
            return fallback;
        }
    }

    private static float[][] clusterPalette(List<float[]> pixels, int colorCount) {
        float[][] centers = new float[colorCount][3];
        centers[0] = Arrays.copyOf(pixels.stream().max(Comparator.comparingDouble(
                color -> saturation(color) * 0.75F + luminance(color) * 0.25F
        )).orElseThrow(), 3);

        for (int center = 1; center < colorCount; center++) {
            float bestDistance = -1.0F;
            float[] best = pixels.getFirst();
            for (float[] pixel : pixels) {
                float nearest = Float.MAX_VALUE;
                for (int existing = 0; existing < center; existing++) {
                    nearest = Math.min(nearest, distanceSquared(pixel, centers[existing]));
                }
                if (nearest > bestDistance) {
                    bestDistance = nearest;
                    best = pixel;
                }
            }
            centers[center] = Arrays.copyOf(best, 3);
        }

        for (int iteration = 0; iteration < 8; iteration++) {
            float[][] sums = new float[colorCount][3];
            int[] counts = new int[colorCount];
            for (float[] pixel : pixels) {
                int nearest = nearestCenter(pixel, centers);
                sums[nearest][0] += pixel[0];
                sums[nearest][1] += pixel[1];
                sums[nearest][2] += pixel[2];
                counts[nearest]++;
            }
            for (int center = 0; center < colorCount; center++) {
                if (counts[center] == 0) continue;
                centers[center][0] = sums[center][0] / counts[center];
                centers[center][1] = sums[center][1] / counts[center];
                centers[center][2] = sums[center][2] / counts[center];
            }
        }

        Arrays.sort(centers, Comparator.comparingDouble(PrismBallColorPalette::luminance));
        return centers;
    }

    private static int nearestCenter(float[] color, float[][] centers) {
        int nearest = 0;
        float nearestDistance = Float.MAX_VALUE;
        for (int center = 0; center < centers.length; center++) {
            float distance = distanceSquared(color, centers[center]);
            if (distance < nearestDistance) {
                nearest = center;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static float distanceSquared(float[] left, float[] right) {
        float red = left[0] - right[0];
        float green = left[1] - right[1];
        float blue = left[2] - right[2];
        return red * red + green * green + blue * blue;
    }

    private static float saturation(float[] color) {
        float max = Math.max(color[0], Math.max(color[1], color[2]));
        float min = Math.min(color[0], Math.min(color[1], color[2]));
        return max <= 0.0F ? 0.0F : (max - min) / max;
    }

    private static float luminance(float[] color) {
        return luminance(color[0], color[1], color[2]);
    }

    private static float luminance(float red, float green, float blue) {
        return red * 0.2126F + green * 0.7152F + blue * 0.0722F;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DreamweavingPrismBallMod.ID, path);
    }

    private static ResourceLocation entityTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                DreamweavingPrismBallMod.ID, "textures/poke_balls/" + name + ".png"
        );
    }
}
