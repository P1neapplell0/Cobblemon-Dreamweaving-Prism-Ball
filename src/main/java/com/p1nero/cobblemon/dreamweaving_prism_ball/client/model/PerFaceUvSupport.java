package com.p1nero.cobblemon.dreamweaving_prism_ball.client.model;

import com.cobblemon.mod.common.client.render.models.blockbench.Cube;
import com.cobblemon.mod.common.client.render.models.blockbench.ModelBone;
import com.cobblemon.mod.common.client.render.models.blockbench.ModelGeometry;
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.p1nero.cobblemon.dreamweaving_prism_ball.mixin.client.CubePolygonsAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDefinition;
import net.minecraft.core.Direction;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Bridges modern Bedrock per-face UV JSON to Cobblemon's legacy box-UV loader. */
public final class PerFaceUvSupport {
    private static final Set<String> MODEL_IDENTIFIERS = Set.of(
            "geometry.cobblemon_dreamweaving_prism_ball.prism_ball",
            "geometry.cobblemon_dreamweaving_prism_ball.dreamweaving_prism_ball"
    );
    private static final Map<TexturedModel, Boolean> OWNED_MODELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Cube, CubeFaces> CUBE_FACES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Cube, SizeSigns> CUBE_SIZE_SIGNS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<CubeDefinition, CubeFaces> DEFINITION_FACES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<CubeDefinition, SizeSigns> DEFINITION_SIZE_SIGNS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Deque<PendingCube>> PENDING_CUBES = new ThreadLocal<>();
    private static final ThreadLocal<List<CubeFaces>> PARSED_FACES = new ThreadLocal<>();

    private PerFaceUvSupport() {
    }

    public static String captureAndMakeCobblemonCompatible(String json) {
        if (MODEL_IDENTIFIERS.stream().noneMatch(json::contains)
                || !json.contains("\"uv\"")
                || !json.contains("\"uv_size\"")) {
            PARSED_FACES.remove();
            return json;
        }
        JsonElement parsedRoot;
        try {
            parsedRoot = JsonParser.parseString(json);
        } catch (RuntimeException ignored) {
            PARSED_FACES.remove();
            return json;
        }
        if (!parsedRoot.isJsonObject()) return json;
        JsonObject root = parsedRoot.getAsJsonObject();
        JsonElement geometriesElement = root.get("minecraft:geometry");
        if (geometriesElement == null || !geometriesElement.isJsonArray()) return json;
        JsonArray geometries = geometriesElement.getAsJsonArray();

        java.util.ArrayList<CubeFaces> parsed = new java.util.ArrayList<>();
        boolean changed = false;
        for (JsonElement geometryElement : geometries) {
            JsonArray bones = geometryElement.getAsJsonObject().getAsJsonArray("bones");
            if (bones == null) continue;
            for (JsonElement boneElement : bones) {
                JsonArray cubes = boneElement.getAsJsonObject().getAsJsonArray("cubes");
                if (cubes == null) continue;
                for (JsonElement cubeElement : cubes) {
                    JsonObject cube = cubeElement.getAsJsonObject();
                    JsonElement uv = cube.get("uv");
                    if (uv != null && uv.isJsonObject()) {
                        parsed.add(readFaces(uv.getAsJsonObject()));
                        JsonArray placeholder = new JsonArray();
                        placeholder.add(0);
                        placeholder.add(0);
                        cube.add("uv", placeholder);
                        changed = true;
                    } else {
                        parsed.add(CubeFaces.NONE);
                    }
                }
            }
        }

        if (!changed) {
            PARSED_FACES.remove();
            return json;
        }
        PARSED_FACES.set(parsed);
        return root.toString();
    }

    public static void bindParsedFaces(TexturedModel model) {
        List<CubeFaces> parsed = PARSED_FACES.get();
        PARSED_FACES.remove();
        if (parsed == null || model == null || model.getGeometry() == null) return;
        OWNED_MODELS.put(model, Boolean.TRUE);

        int index = 0;
        for (ModelGeometry geometry : model.getGeometry()) {
            if (geometry.getBones() == null) continue;
            for (ModelBone bone : geometry.getBones()) {
                if (bone.getCubes() == null) continue;
                for (Cube cube : bone.getCubes()) {
                    if (index < parsed.size() && parsed.get(index) != CubeFaces.NONE) {
                        CUBE_FACES.put(cube, parsed.get(index));
                        CUBE_SIZE_SIGNS.put(cube, SizeSigns.from(cube));
                    }
                    index++;
                }
            }
        }
    }

    public static void beginCreate(TexturedModel model) {
        if (!OWNED_MODELS.containsKey(model)) {
            PENDING_CUBES.remove();
            return;
        }
        Deque<PendingCube> pending = new ArrayDeque<>();
        if (model.getGeometry() != null) {
            for (ModelGeometry geometry : model.getGeometry()) {
                if (geometry.getBones() == null) continue;
                for (ModelBone bone : geometry.getBones()) {
                    if (bone.getCubes() == null) continue;
                    for (Cube cube : bone.getCubes()) {
                        if (cube.getOrigin() != null && cube.getSize() != null) {
                            pending.addLast(new PendingCube(CUBE_FACES.get(cube), CUBE_SIZE_SIGNS.get(cube)));
                        }
                    }
                }
            }
        }
        PENDING_CUBES.set(pending);
    }

    public static void attachNextDefinition(CubeDefinition definition) {
        Deque<PendingCube> pending = PENDING_CUBES.get();
        if (pending == null || pending.isEmpty()) return;
        PendingCube cube = pending.removeFirst();
        if (cube.faces() != null) {
            DEFINITION_FACES.put(definition, cube.faces());
            DEFINITION_SIZE_SIGNS.put(definition, cube.sizeSigns());
        }
    }

    public static void endCreate() {
        PENDING_CUBES.remove();
    }

    public static void applyFaces(CubeDefinition definition, ModelPart.Cube cube, int textureWidth, int textureHeight) {
        CubeFaces faces = DEFINITION_FACES.get(definition);
        if (faces == null) return;
        ModelPart.Polygon[] polygons = ((CubePolygonsAccessor) (Object) cube).dreamweaving$getPolygons();
        for (ModelPart.Polygon polygon : polygons) {
            Direction direction = Direction.getNearest(polygon.normal.x(), polygon.normal.y(), polygon.normal.z());
            // TexturedModel converts Bedrock Y to ModelPart Y by negating it, so the
            // baked horizontal polygons correspond to the opposite Bedrock face.
            Direction sourceDirection = switch (direction) {
                case DOWN -> Direction.UP;
                case UP -> Direction.DOWN;
                default -> direction;
            };
            FaceUv face = faces.faces().get(sourceDirection);
            if (face == null) {
                hideFace(polygon, textureWidth, textureHeight);
                continue;
            }
            SizeSigns sizeSigns = DEFINITION_SIZE_SIGNS.getOrDefault(definition, SizeSigns.POSITIVE);
            remap(polygon, face, textureWidth, textureHeight,
                    PokeBallUvMapping.shouldFlipU(sourceDirection.getName(), sizeSigns.sizeX(), sizeSigns.sizeY(), sizeSigns.sizeZ()));
        }
    }

    private static void hideFace(ModelPart.Polygon polygon, int textureWidth, int textureHeight) {
        float u = 0.5F / textureWidth;
        float v = 0.5F / textureHeight;
        for (int i = 0; i < polygon.vertices.length; i++) {
            polygon.vertices[i] = polygon.vertices[i].remap(u, v);
        }
    }

    private static void remap(ModelPart.Polygon polygon, FaceUv face, int textureWidth, int textureHeight,
                              boolean flipU) {
        float[] corners = PokeBallUvMapping.corners(
                face.u(), face.v(), face.width(), face.height(), textureWidth, textureHeight, flipU
        );
        for (int i = 0; i < polygon.vertices.length; i++) {
            polygon.vertices[i] = polygon.vertices[i].remap(corners[i * 2], corners[i * 2 + 1]);
        }
    }

    private static CubeFaces readFaces(JsonObject object) {
        Map<Direction, FaceUv> faces = new EnumMap<>(Direction.class);
        readFace(object, "down", Direction.DOWN, faces);
        readFace(object, "up", Direction.UP, faces);
        readFace(object, "west", Direction.WEST, faces);
        readFace(object, "north", Direction.NORTH, faces);
        readFace(object, "east", Direction.EAST, faces);
        readFace(object, "south", Direction.SOUTH, faces);
        return new CubeFaces(faces);
    }

    private static void readFace(JsonObject object, String name, Direction direction, Map<Direction, FaceUv> faces) {
        JsonObject face = object.has(name) ? object.getAsJsonObject(name) : null;
        if (face == null || !face.has("uv") || !face.has("uv_size")) return;
        JsonArray uv = face.getAsJsonArray("uv");
        JsonArray size = face.getAsJsonArray("uv_size");
        faces.put(direction, new FaceUv(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                size.get(0).getAsFloat(), size.get(1).getAsFloat()));
    }

    private record PendingCube(CubeFaces faces, SizeSigns sizeSigns) {
    }

    private record FaceUv(float u, float v, float width, float height) {
    }

    private record CubeFaces(Map<Direction, FaceUv> faces) {
        private static final CubeFaces NONE = new CubeFaces(Map.of());
    }

    private record SizeSigns(float sizeX, float sizeY, float sizeZ) {
        private static final SizeSigns POSITIVE = new SizeSigns(1.0F, 1.0F, 1.0F);

        private static SizeSigns from(Cube cube) {
            List<Float> size = cube.getSize();
            return new SizeSigns(size.get(0), size.get(1), size.get(2));
        }
    }

}
