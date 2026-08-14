#!/usr/bin/env python3
"""Validate and optionally normalize Cobblemon Poke Ball Bedrock assets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


CORE_CLIPS = {
    "throw",
    "open",
    "open_idle",
    "shut",
    "shut_idle",
    "bounce",
    "critical",
    "capture",
    "break",
    *(f"bob{i}" for i in range(1, 7)),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--geo", required=True, type=Path)
    parser.add_argument("--identifier", required=True)
    parser.add_argument("--animation", type=Path)
    parser.add_argument("--animation-group")
    parser.add_argument("--root-bone", default="poke_ball")
    parser.add_argument("--attach-bones", default="", help="Comma-separated bones to parent to --root-bone")
    parser.add_argument("--write", action="store_true", help="Write normalized JSON in place")
    return parser.parse_args()


def load(path: Path) -> Any:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def write(path: Path, value: Any) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent="\t") + "\n", encoding="utf-8")


def unwrap_vectors(value: Any) -> tuple[Any, int]:
    if isinstance(value, dict):
        if set(value) == {"vector"}:
            normalized, nested = unwrap_vectors(value["vector"])
            return normalized, nested + 1
        result: dict[str, Any] = {}
        count = 0
        for key, child in value.items():
            result[key], child_count = unwrap_vectors(child)
            count += child_count
        return result, count
    if isinstance(value, list):
        result = []
        count = 0
        for child in value:
            normalized, child_count = unwrap_vectors(child)
            result.append(normalized)
            count += child_count
        return result, count
    return value, 0


def normalize_geo(data: dict[str, Any], args: argparse.Namespace) -> tuple[set[str], list[str]]:
    changes: list[str] = []
    geometries = data.get("minecraft:geometry")
    if not isinstance(geometries, list) or not geometries:
        raise ValueError("geo has no minecraft:geometry array")
    geometry = geometries[0]
    description = geometry.setdefault("description", {})
    old_identifier = description.get("identifier")
    if old_identifier != args.identifier:
        description["identifier"] = args.identifier
        changes.append(f"geometry identifier: {old_identifier!r} -> {args.identifier!r}")

    bones = geometry.get("bones")
    if not isinstance(bones, list):
        raise ValueError("geo has no bones array")
    by_name = {bone.get("name"): bone for bone in bones if isinstance(bone, dict) and bone.get("name")}
    if args.root_bone not in by_name:
        raise ValueError(f"root bone {args.root_bone!r} does not exist")

    requested = [name.strip() for name in args.attach_bones.split(",") if name.strip()]
    for name in requested:
        bone = by_name.get(name)
        if bone is None:
            raise ValueError(f"bone requested by --attach-bones does not exist: {name}")
        old_parent = bone.get("parent")
        if old_parent != args.root_bone:
            bone["parent"] = args.root_bone
            changes.append(f"bone parent {name}: {old_parent!r} -> {args.root_bone!r}")

    for name, bone in by_name.items():
        parent = bone.get("parent")
        if parent is not None and parent not in by_name:
            raise ValueError(f"bone {name!r} references missing parent {parent!r}")
    return set(by_name), changes


def normalize_animation(data: dict[str, Any], group: str) -> tuple[dict[str, Any], set[str], set[str], list[str]]:
    normalized, vector_count = unwrap_vectors(data)
    animations = normalized.get("animations")
    if not isinstance(animations, dict):
        raise ValueError("animation file has no animations object")

    renamed: dict[str, Any] = {}
    clips: set[str] = set()
    animated_bones: set[str] = set()
    rename_count = 0
    for key, animation in animations.items():
        parts = key.split(".", 2)
        if len(parts) != 3 or parts[0] != "animation":
            raise ValueError(f"unexpected animation key: {key!r}")
        clip = parts[2]
        new_key = f"animation.{group}.{clip}"
        if new_key != key:
            rename_count += 1
        if new_key in renamed:
            raise ValueError(f"animation key collision after rename: {new_key}")
        renamed[new_key] = animation
        clips.add(clip)
        if isinstance(animation, dict) and isinstance(animation.get("bones"), dict):
            animated_bones.update(animation["bones"])
    normalized["animations"] = renamed

    changes = []
    if vector_count:
        changes.append(f"unwrapped {vector_count} vector object(s)")
    if rename_count:
        changes.append(f"renamed {rename_count} animation key(s) to group {group!r}")
    return normalized, clips, animated_bones, changes


def main() -> int:
    args = parse_args()
    if args.animation and not args.animation_group:
        raise ValueError("--animation-group is required with --animation")

    geo = load(args.geo)
    bone_names, changes = normalize_geo(geo, args)
    animation = None
    if args.animation:
        animation = load(args.animation)
        animation, clips, animated_bones, animation_changes = normalize_animation(animation, args.animation_group)
        changes.extend(animation_changes)
        missing_clips = sorted(CORE_CLIPS - clips)
        missing_bones = sorted(animated_bones - bone_names)
        if missing_clips:
            raise ValueError("missing core animation clips: " + ", ".join(missing_clips))
        if missing_bones:
            raise ValueError("animation references missing geo bones: " + ", ".join(missing_bones))

    if changes:
        for change in changes:
            print(f"CHANGE: {change}")
    else:
        print("OK: assets already satisfy requested normalization")

    if args.write:
        write(args.geo, geo)
        if args.animation and animation is not None:
            write(args.animation, animation)
        print("WROTE: normalized assets")
    elif changes:
        print("DRY RUN: add --write to apply changes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
