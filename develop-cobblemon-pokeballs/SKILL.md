---
name: develop-cobblemon-pokeballs
description: Build, extend, and debug custom Cobblemon Poke Balls in Minecraft NeoForge mods. Use for Cobblemon ball registration, Kotlin capture effects, Blockbench Bedrock geo/animation exports, negative-size cubes, per-face UVs, custom PokeBallModel posers, isolated animation groups, capture animation timing, full-bright entity rendering, mixins, resource reload failures, or thrown-ball crashes.
---

# Develop Cobblemon Poke Balls

Implement custom balls without changing unrelated Cobblemon balls or Pokemon models. Treat the server capture state machine, client poser, geometry loader, and resource files as separate layers.

## Inspect First

1. Identify the Minecraft, NeoForge, Cobblemon, Kotlin for Forge, and mapping versions.
2. Inspect the bundled Cobblemon JAR with `jar tf` and `javap`; do not assume another release has the same APIs.
3. Read existing variation JSON, geo, animations, mixins, item registration, capture hooks, and `git status`.
4. Preserve user-edited models and textures. Never replace a Blockbench export with an approximate shape to make it load.
5. For Cobblemon 1.7.3, animated balls, negative cubes, or capture timing, read [references/cobblemon-1.7.3.md](references/cobblemon-1.7.3.md).

## Choose The Change Layer

- Use resources for textures, geometry, keyframes, interpolation, sounds, particles, bones, and locators.
- Use a dedicated poser/model class when a ball needs an independent animation group or stock `PokeBallModel` pose behavior.
- Use client Mixins for narrowly scoped renderer or state-to-animation changes.
- Use server code or Mixins when changing capture phases, shake count, delays, success/failure timing, Pokemon visibility, or entity removal.
- Keep gameplay authority on the server. Never let a client animation determine capture success.

## Repair Blockbench Exports

When the source is a `.bbmodel` with embedded textures, use the project-local exporter before normalization:

```bash
node develop-cobblemon-pokeballs/scripts/export_bbmodel_assets.mjs \
  --bedrock path/to/ball.bbmodel \
  --item path/to/ball_item.bbmodel \
  --namespace example_mod --id custom_ball \
  --geo path/to/custom_ball.geo.json \
  --animation path/to/custom_ball.animation.json \
  --item-model path/to/custom_ball_model.json \
  --entity-texture path/to/textures/poke_balls/custom_ball.png \
  --item-model-texture path/to/textures/item/poke_balls/models/custom_ball.png
```

The exporter targets Minecraft 1.21.1 Java item JSON. A cube with rotations on multiple axes cannot be represented exactly by that format; it exports the first non-zero axis, matching Blockbench's legacy fallback. Angles outside Minecraft's `-45/-22.5/0/22.5/45` set are clamped to the nearest supported endpoint. In both cases, the original rotation vector is retained in the non-runtime `rotated` field for diagnosis.

Run the bundled normalizer in check mode first:

```bash
python3 ~/.codex/skills/develop-cobblemon-pokeballs/scripts/normalize_pokeball_assets.py \
  --geo path/to/ball.geo.json \
  --identifier geometry.example_mod.custom_ball \
  --animation path/to/ball.animation.json \
  --animation-group custom_ball \
  --root-bone poke_ball \
  --attach-bones bone2
```

Add `--write` only after reviewing its report. Run it again without `--write` to verify the result.

Always check these invariants manually:

- Use a unique stable geometry identifier, never `geometry.unknown`.
- Keep `poke_ball -> ball -> bottom -> lid` where stock open/shut animations expect it.
- Put every animated auxiliary bone under the poser-visible root subtree. A valid animation key cannot move an unregistered sibling bone.
- Keep `center_particles`, `bottom_particles`, and `beam` locators when their effects use them.
- Match texture dimensions in geo to the actual PNG.
- Preserve negative `size` values and per-face `uv`/`uv_size` objects when that visual is intentional.
- Scope any loader compatibility to the unique geometry identifier. Global cube winding or UV changes can corrupt all balls and Pokemon.

## Isolate Animations

1. Give the file a unique basename such as `custom_ball.animation.json`; Cobblemon indexes animation groups by filename.
2. Rename keys to `animation.custom_ball.<clip>`.
3. Use a dedicated model/poser that requests group `custom_ball`.
4. Keep the stock clip contract unless client code changes it:
   `throw`, `open`, `open_idle`, `shut`, `shut_idle`, `bounce`, `bob1` through `bob6`, `critical`, `capture`, and `break`.
5. Let Cobblemon generate the default prefix:

```kotlin
bedrock(ANIMATION_GROUP, "throw")
bedrockStateful(ANIMATION_GROUP, "open")
```

Do not hand-build `animation.<group>.` prefixes. Cobblemon adds the separator; a trailing dot produces names such as `animation.custom_ball..throw`.

## Register A Dedicated Poser

Prefer a Kotlin subclass of `PokeBallModel` for an animated capture ball. Java inheritance can fail against Cobblemon's Kotlin/Mixin `ModelPart` to `Bone` bridge.

Override `registerPoses()`, call `super`, then replace the midair, idle, open, and shut animation references with the dedicated group. Preserve the stock poses so the `HIT` path still recognizes `PokeBallModel`.

Cobblemon 1.7.3 has no stable addon registration event. Its public poser map is cleared on resource reload, and `inbuilt()` forces the `cobblemon` namespace. Register the namespaced factory after `registerInBuiltPosers()` or through an equivalent reload-safe hook. Gate `PokeBallPosableState.getGroup()` by the dedicated model type.

Do not globally replace `poke_ball.animation.json` or return the custom group for every `PokeBallModel`.

## Add Rendering Effects

Gate rendering changes by the ball registry ID or dedicated model type.

- Use `LightTexture.FULL_BRIGHT` for a consistently bright thrown entity.
- State clearly that full-bright removes environmental darkening but does not create bloom.
- Use a dedicated render layer or shader only when real emissive bloom is required.
- Do not modify global `ModelPart`, cube winding, UV, or light behavior without the unique model guard active.

## Respect Timing

Animation length does not change capture scheduling. Keep clips within the current code windows or change the server/client schedule together.

- Shorter clips may leave idle gaps.
- Longer bob clips are replaced by the next shake event.
- Longer success/failure clips are cut off when the ball entity is removed.
- Adding a JSON clip does nothing until a poser or state mapper references it.

## Validate End To End

1. Parse every changed JSON with `jq empty` or another structured parser.
2. Verify animation bone names exist in geo and sit under the poser root.
3. Run `git diff --check`.
4. Run the project build.
5. Start the client and wait for Cobblemon to finish loading animations, models, posers, and variations.
6. Search the latest log for `Failed to load model`, `Expected BEGIN_ARRAY`, `Animation .* not found`, `InvalidInjection`, and the mod ID.
7. Test the first thrown-entity render, hit/open, shake, success, and failure paths. A title-screen resource load does not instantiate a variation poser.
8. Confirm a stock ball and a Pokemon still render normally.
9. Stop every test client before finishing and report the exact artifact path.
