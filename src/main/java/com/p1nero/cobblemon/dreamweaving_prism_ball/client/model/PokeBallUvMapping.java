package com.p1nero.cobblemon.dreamweaving_prism_ball.client.model;

final class PokeBallUvMapping {
    private PokeBallUvMapping() {
    }

    static boolean shouldFlipU(String face, float sizeX, float sizeY, float sizeZ) {
        return switch (face) {
            case "east", "west" -> sizeZ < 0.0F;
            case "down", "up", "north", "south" -> sizeX < 0.0F;
            default -> throw new IllegalArgumentException("Unknown cube face: " + face);
        };
    }

    static float[] corners(float u, float v, float width, float height,
                           int textureWidth, int textureHeight, boolean flipU) {
        float u1 = u / textureWidth;
        float v1 = v / textureHeight;
        float u2 = (u + width) / textureWidth;
        float v2 = (v + height) / textureHeight;
        float left = flipU ? u2 : u1;
        float right = flipU ? u1 : u2;
        return new float[]{right, v1, left, v1, left, v2, right, v2};
    }
}
