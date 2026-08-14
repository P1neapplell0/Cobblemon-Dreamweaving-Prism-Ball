package com.p1nero.cobblemon.dreamweaving_prism_ball.client.model;

public final class PerFaceUvSupportTest {
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};

    private PerFaceUvSupportTest() {
    }

    public static void main(String[] args) {
        assertFlip(false, "north", 1.0F, 1.0F, 1.0F);
        assertFlip(true, "north", -1.0F, 1.0F, 1.0F);
        assertFlip(true, "south", -1.0F, 1.0F, 1.0F);
        assertFlip(true, "up", -1.0F, 1.0F, 1.0F);
        assertFlip(true, "down", -1.0F, 1.0F, 1.0F);

        assertFlip(false, "east", -1.0F, 1.0F, 1.0F);
        assertFlip(true, "east", 1.0F, 1.0F, -1.0F);
        assertFlip(true, "west", 1.0F, 1.0F, -1.0F);

        // A negative Y dimension must never vertically invert per-face UVs.
        for (String face : FACES) {
            assertFlip(false, face, 1.0F, -1.0F, 1.0F);
        }

        for (String face : FACES) {
            assertFlip(true, face, -1.0F, -1.0F, -1.0F);
        }

        assertCorners(
                new float[]{24, 16, 16, 16, 16, 20, 24, 20},
                PokeBallUvMapping.corners(16, 16, 8, 4, 1, 1, false)
        );
        assertCorners(
                new float[]{16, 16, 24, 16, 24, 20, 16, 20},
                PokeBallUvMapping.corners(16, 16, 8, 4, 1, 1, true)
        );
        assertCorners(
                new float[]{8, 16, 16, 16, 16, 8, 8, 8},
                PokeBallUvMapping.corners(8, 16, 8, -8, 1, 1, true)
        );
    }

    private static void assertFlip(boolean expected, String face, float sizeX, float sizeY, float sizeZ) {
        boolean actual = PokeBallUvMapping.shouldFlipU(face, sizeX, sizeY, sizeZ);
        if (actual != expected) {
            throw new AssertionError("Unexpected U flip for " + face + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertCorners(float[] expected, float[] actual) {
        if (actual.length != expected.length) {
            throw new AssertionError("Unexpected UV corner count");
        }
        for (int i = 0; i < expected.length; i++) {
            if (Float.compare(expected[i], actual[i]) != 0) {
                throw new AssertionError("Unexpected UV corner at " + i + ": expected " + expected[i] + ", got " + actual[i]);
            }
        }
    }
}
