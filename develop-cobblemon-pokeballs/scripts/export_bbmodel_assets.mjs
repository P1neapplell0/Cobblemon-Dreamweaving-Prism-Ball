#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const args = parseArgs(process.argv.slice(2));
const required = [
    "bedrock", "item", "namespace", "id", "geo", "animation", "item-model",
    "entity-texture", "item-model-texture"
];
for (const key of required) {
    if (!args[key]) throw new Error(`Missing --${key}`);
}

const bedrockProject = readJson(args.bedrock);
const itemProject = readJson(args.item);
writeJson(args.geo, exportBedrockGeometry(bedrockProject, args.namespace, args.id));
writeJson(args.animation, exportBedrockAnimations(bedrockProject, args.id));
writeJson(args["item-model"], exportJavaItemModel(itemProject, args.namespace, args.id));
writeTexture(bedrockProject, args["entity-texture"]);
writeTexture(itemProject, args["item-model-texture"]);

function parseArgs(values) {
    const result = {};
    for (let index = 0; index < values.length; index += 2) {
        const key = values[index];
        if (!key?.startsWith("--") || values[index + 1] === undefined) {
            throw new Error(`Invalid argument near ${key ?? "end of command"}`);
        }
        result[key.slice(2)] = values[index + 1];
    }
    return result;
}

function readJson(file) {
    return JSON.parse(fs.readFileSync(file, "utf8"));
}

function writeJson(file, value) {
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, `${JSON.stringify(value, null, "\t")}\n`);
}

function writeTexture(project, file) {
    const source = project.textures?.[0]?.source;
    const match = /^data:image\/png;base64,(.+)$/s.exec(source ?? "");
    if (!match) throw new Error(`Project ${project.name ?? "<unnamed>"} has no embedded PNG texture`);
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, Buffer.from(match[1], "base64"));
}

function exportBedrockGeometry(project, namespace, id) {
    const elements = new Map(project.elements.map(element => [element.uuid, element]));
    const groups = new Map(project.groups.map(group => [group.uuid, group]));
    const bones = [];

    function visit(node, parentName) {
        if (typeof node === "string") return;
        const group = groups.get(node.uuid);
        if (!group || group.export === false) return;
        const bone = {
            name: group.name,
            ...(parentName ? {parent: parentName} : {}),
            pivot: [-group.origin[0], group.origin[1], group.origin[2]]
        };
        if (!allZero(group.rotation)) {
            bone.rotation = [-group.rotation[0], -group.rotation[1], group.rotation[2]];
        }
        if (group.reset) bone.reset = true;
        if (group.mirror_uv) bone.mirror = true;

        const cubes = [];
        const locators = {};
        for (const child of node.children ?? []) {
            if (typeof child !== "string") continue;
            const element = elements.get(child);
            if (!element || element.export === false) continue;
            if (element.type === "cube") cubes.push(exportBedrockCube(element, bone));
            if (element.type === "locator") {
                const offset = [-element.position[0], element.position[1], element.position[2]];
                if (!allZero(element.rotation) || element.ignore_inherited_scale) {
                    locators[element.name] = {
                        offset,
                        ...(!allZero(element.rotation) ? {
                            rotation: [-element.rotation[0], -element.rotation[1], element.rotation[2]]
                        } : {}),
                        ...(element.ignore_inherited_scale ? {ignore_inherited_scale: true} : {})
                    };
                } else {
                    locators[element.name] = offset;
                }
            }
        }
        if (cubes.length) bone.cubes = cubes;
        if (Object.keys(locators).length) bone.locators = locators;
        bones.push(bone);
        for (const child of node.children ?? []) visit(child, group.name);
    }

    for (const node of project.outliner ?? []) visit(node, null);
    return {
        format_version: "1.12.0",
        "minecraft:geometry": [{
            description: {
                identifier: `geometry.${namespace}.${id}`,
                texture_width: project.resolution.width,
                texture_height: project.resolution.height,
                visible_bounds_width: 2,
                visible_bounds_height: 2,
                visible_bounds_offset: [0, 0.5, 0]
            },
            bones
        }]
    };
}

function exportBedrockCube(cube, bone) {
    const size = cube.to.map((value, axis) => value - cube.from[axis]);
    const result = {
        origin: [-(cube.from[0] + size[0]), cube.from[1], cube.from[2]],
        size
    };
    if (cube.inflate) result.inflate = cube.inflate;
    if (!allZero(cube.rotation)) {
        result.pivot = [-cube.origin[0], cube.origin[1], cube.origin[2]];
        result.rotation = [-cube.rotation[0], -cube.rotation[1], cube.rotation[2]];
    }
    if (cube.box_uv) {
        result.uv = cube.uv_offset;
        if (!!cube.mirror_uv !== !!bone.mirror) result.mirror = !!cube.mirror_uv;
    } else {
        result.uv = {};
        for (const [name, face] of Object.entries(cube.faces ?? {})) {
            if (face.texture === null || face.enabled === false) continue;
            let uv = [face.uv[0], face.uv[1]];
            let uvSize = [face.uv[2] - face.uv[0], face.uv[3] - face.uv[1]];
            if (name === "up" || name === "down") {
                uv = [uv[0] + uvSize[0], uv[1] + uvSize[1]];
                uvSize = [-uvSize[0], -uvSize[1]];
            }
            result.uv[name] = {
                uv,
                uv_size: uvSize,
                ...(face.rotation ? {uv_rotation: face.rotation} : {})
            };
        }
    }
    return result;
}

function exportBedrockAnimations(project, group) {
    const animations = {};
    for (const animation of project.animations ?? []) {
        const clip = animation.name.split(".").at(-1);
        const compiled = {};
        const maxTime = Math.max(0, ...Object.values(animation.animators ?? {})
            .flatMap(animator => animator.keyframes ?? []).map(keyframe => keyframe.time));
        if (animation.loop === "hold") compiled.loop = "hold_on_last_frame";
        else if (animation.loop === "loop" || maxTime === 0) compiled.loop = true;
        if (animation.length) compiled.animation_length = round(animation.length, 4);
        if (animation.override) compiled.override_previous_animation = true;

        const bones = {};
        for (const animator of Object.values(animation.animators ?? {})) {
            if (animator.type !== "bone" || !(animator.keyframes?.length)) continue;
            const bone = {};
            for (const channel of ["rotation", "position", "scale"]) {
                const keyframes = animator.keyframes
                    .filter(keyframe => keyframe.channel === channel)
                    .sort((left, right) => left.time - right.time);
                if (!keyframes.length) continue;
                const values = {};
                for (let index = 0; index < keyframes.length; index++) {
                    const keyframe = keyframes[index];
                    values[timecode(keyframe.time, animation.snapping)] = compileTransformKeyframe(
                        keyframe, keyframes[index - 1], channel
                    );
                }
                const keys = Object.keys(values);
                if (keys.length === 1 && keyframes[0].data_points.length === 1
                    && keyframes[0].interpolation !== "catmullrom") {
                    bone[channel] = values[keys[0]];
                    if (channel === "scale" && Array.isArray(bone[channel])
                        && bone[channel].every(value => value === bone[channel][0])) {
                        bone[channel] = bone[channel][0];
                    }
                } else {
                    bone[channel] = values;
                }
            }
            if (Object.keys(bone).length) bones[animator.name] = bone;
        }
        if (Object.keys(bones).length) compiled.bones = bones;

        const effects = animation.animators?.effects?.keyframes ?? [];
        for (const channel of ["sound", "particle", "timeline"]) {
            const keyframes = effects.filter(keyframe => keyframe.channel === channel)
                .sort((left, right) => left.time - right.time);
            if (!keyframes.length) continue;
            const key = channel === "sound" ? "sound_effects"
                : channel === "particle" ? "particle_effects" : "timeline";
            compiled[key] = {};
            for (const keyframe of keyframes) {
                const points = keyframe.data_points.map(point => compileEffectPoint(point, channel))
                    .filter(Boolean);
                compiled[key][timecode(keyframe.time, animation.snapping)] = points.length === 1 ? points[0] : points;
            }
        }
        animations[`animation.${group}.${clip}`] = compiled;
    }
    return {format_version: "1.8.0", animations};
}

function compileTransformKeyframe(keyframe, previous, channel) {
    const array = index => flipTransform([
        exportMolang(keyframe.data_points[Math.min(index, keyframe.data_points.length - 1)].x),
        exportMolang(keyframe.data_points[Math.min(index, keyframe.data_points.length - 1)].y),
        exportMolang(keyframe.data_points[Math.min(index, keyframe.data_points.length - 1)].z)
    ], channel);
    if (keyframe.interpolation === "catmullrom") {
        const includePre = (!previous && keyframe.time > 0)
            || (previous && previous.interpolation !== "catmullrom");
        return {
            ...(includePre ? {pre: array(0)} : {}),
            post: array(includePre ? 1 : 0),
            lerp_mode: "catmullrom"
        };
    }
    if (keyframe.data_points.length === 1) return array(0);
    return {pre: array(0), post: array(1)};
}

function compileEffectPoint(point, channel) {
    if (channel === "timeline") {
        const lines = (point.script ?? "").split("\n").filter(line => line.replace(/[\s;.]+/g, ""));
        const compiled = lines.map(line => /;\s*$/.test(line) || line.startsWith("/") ? line : `${line};`);
        return compiled.length <= 1 ? compiled[0] : compiled;
    }
    if (!point.effect) return null;
    const result = {effect: point.effect};
    if (point.locator) result.locator = point.locator;
    if (channel === "particle") {
        if (point.bind_to_actor === false) result.bind_to_actor = false;
        let script = point.script?.trim();
        if (script) {
            if (!script.endsWith(";")) script += ";";
            result.pre_effect_script = script;
        }
    }
    return result;
}

function exportJavaItemModel(project, namespace, id) {
    const elementsById = new Map(project.elements.map(element => [element.uuid, element]));
    const cubes = [];
    function visit(node) {
        if (typeof node === "string") {
            const element = elementsById.get(node);
            if (element?.type === "cube" && element.export !== false) cubes.push(exportJavaCube(element, project));
            return;
        }
        for (const child of node.children ?? []) visit(child);
    }
    for (const node of project.outliner ?? []) visit(node);
    return {
        format_version: "1.9.0",
        gui_light: "front",
        texture_size: [project.resolution.width, project.resolution.height],
        textures: {"2": `${namespace}:item/poke_balls/models/${id}`},
        elements: cubes,
        display: project.display ?? {}
    };
}

function exportJavaCube(cube, project) {
    const result = {
        ...(cube.name && cube.name !== "cube" ? {name: cube.name} : {}),
        from: cube.from.map(value => value - (cube.inflate ?? 0)),
        to: cube.to.map(value => value + (cube.inflate ?? 0))
    };
    if (cube.shade === false) result.shade = false;
    const rotation = cube.rotation ?? [0, 0, 0];
    const nonZeroAxes = rotation.map((value, axis) => value ? axis : -1).filter(axis => axis >= 0);
    if (nonZeroAxes.length) {
        const axis = nonZeroAxes[0];
        const angle = Math.max(-45, Math.min(45, Math.round(rotation[axis] / 22.5) * 22.5));
        result.rotation = {
            angle,
            axis: "xyz"[axis],
            origin: cube.origin
        };
        if (cube.rescale) result.rotation.rescale = true;
        if (nonZeroAxes.length > 1 || angle !== rotation[axis]) result.rotated = rotation;
    }
    result.faces = {};
    for (const [name, face] of Object.entries(cube.faces ?? {})) {
        if (face.texture === null) continue;
        const exported = {
            uv: face.uv.map((value, index) => value * 16
                / (index % 2 ? project.resolution.height : project.resolution.width)),
            texture: "#2"
        };
        if (face.rotation) exported.rotation = face.rotation;
        if (face.cullface) exported.cullface = face.cullface;
        if ((face.tint ?? -1) >= 0) exported.tintindex = face.tint;
        result.faces[name] = exported;
    }
    return result;
}

function exportMolang(value) {
    if (typeof value === "number") return value;
    if (!value) return 0;
    const numeric = Number(value);
    return Number.isNaN(numeric) ? value.replaceAll("\n", "") : numeric;
}

function flipTransform(array, channel) {
    if (channel === "position" || channel === "rotation") array[0] = invert(array[0]);
    if (channel === "rotation") array[1] = invert(array[1]);
    return array;
}

function invert(value) {
    if (typeof value === "number") return Object.is(-value, -0) ? 0 : -value;
    if (value === "" || value === "0") return value;
    if (/^-?\d+(\.\d+f?)?$/.test(value)) return (-Number.parseFloat(value)).toString();
    return processMolangReturn(value, expression => {
        let shouldInvert = true;
        let bracketDepth = 0;
        let lastOperator;
        let result = "";
        for (const character of expression) {
            let operator;
            let hadInput = true;
            if (!bracketDepth) {
                if (character === "-" && lastOperator !== "*" && lastOperator !== "/") {
                    if (!shouldInvert && !lastOperator) result += "+";
                    shouldInvert = false;
                    continue;
                }
                if (character === " " || character === "\n") {
                    hadInput = false;
                } else if (character === "+" && lastOperator !== "*" && lastOperator !== "/") {
                    result += "-";
                    shouldInvert = false;
                    continue;
                } else if ("?:".includes(character)) {
                    shouldInvert = true;
                    operator = character;
                } else if (shouldInvert) {
                    result += "-";
                    shouldInvert = false;
                } else if ("+-*/&|".includes(character)) {
                    operator = character;
                }
                if (hadInput) lastOperator = operator;
            }
            if ("{([".includes(character)) bracketDepth++;
            else if ("})]".includes(character)) bracketDepth--;
            result += character;
        }
        return result;
    });
}

function processMolangReturn(molang, callback) {
    if (molang.includes("return ")) {
        return molang.replace(/return (.+?)(;|$)/g,
            (match, expression, end) => `return ${callback(expression)}${end}`);
    }
    molang = molang.replace(/;+$/, "");
    const lastSemicolon = molang.lastIndexOf(";");
    if (lastSemicolon === -1) {
        return molang.includes("=") ? `${molang};${callback("")}` : callback(molang);
    }
    const before = molang.substring(0, lastSemicolon);
    const after = molang.substring(lastSemicolon + 1);
    return after.includes("=") ? `${molang};${callback("")}` : `${before};${callback(after)}`;
}

function timecode(time, snapping = 24) {
    const snapped = Math.round(time * Math.min(120, Math.max(1, snapping)))
        / Math.min(120, Math.max(1, snapping));
    const formatted = round(snapped, 4).toString();
    return formatted.includes(".") ? formatted : `${formatted}.0`;
}

function round(value, digits) {
    return Number(value.toFixed(digits));
}

function allZero(values = []) {
    return values.every(value => value === 0);
}
