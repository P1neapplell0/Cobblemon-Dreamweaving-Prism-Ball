# Cobblemon 1.7.3 Poke Ball Reference

## Contents

- Asset layout and repositories
- Client poser behavior
- Capture animation order
- Blockbench export compatibility
- Isolated implementation pattern
- Failure signatures

## Asset Layout And Repositories

Cobblemon 1.7.3 for Minecraft 1.21.1 scans these resource paths across namespaces:

```text
assets/<namespace>/bedrock/poke_balls/models/*.geo.json
assets/<namespace>/bedrock/poke_balls/animations/*.animation.json
assets/<namespace>/bedrock/poke_balls/variations/*.json
assets/<namespace>/bedrock/poke_balls/posers/*.json
```

`BedrockAnimationRepository` keys groups by the animation filename without `.animation.json`, not by namespace. A file named `prism_ball.animation.json` is requested as group `prism_ball`.

`VaryingModelRepository` owns the poser map. In 1.7.3:

- `registerPosers()` clears the map on reload.
- `registerInBuiltPosers()` repopulates built-ins.
- `inbuilt(name, factory)` is public but constructs a `cobblemon:<name>` ID.
- `getPosers()` exposes the mutable map, but there is no stable addon registration event.
- JSON poke-ball posers deserialize as generic `PosableModel` and do not satisfy code that checks `currentModel is PokeBallModel`.

Use a reload-safe, namespaced factory for custom `PokeBallModel` subclasses.

## Client Poser Behavior

Relevant classes:

```text
com.cobblemon.mod.common.client.render.pokeball.PokeBallRenderer
com.cobblemon.mod.common.client.render.pokeball.PokeBallPosableState
com.cobblemon.mod.common.client.entity.EmptyPokeBallClientDelegate
com.cobblemon.mod.common.client.render.models.blockbench.pokeball.PokeBallModel
com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
```

`PokeBallModel` registers:

- `flying` with `poke_ball/throw`
- `shut` with `poke_ball/shut_idle`
- `open` with `poke_ball/open_idle`
- shut-to-open transition with `poke_ball/open`
- open-to-shut transition with `poke_ball/shut`

`PokeBallPosableState.getGroup()` returns `ancient_poke_ball` for `AncientPokeBallModel`, otherwise `poke_ball`. A custom group therefore needs an isolated override or injection.

On `HIT`, the client only moves to the `open` pose when the current model is a `PokeBallModel`. This is why a generic JSON poser is insufficient for a stock-style capture ball.

## Capture Animation Order

Authoritative states in `EmptyPokeBallEntity.CaptureState`:

```text
NOT, HIT, FALL, CRITICAL, SHAKE, CAPTURED, BROKEN_FREE
```

Normal flow:

```text
NOT/throw
  -> HIT
  -> after 0.2 s: open transition
  -> after another 1.75 s: shut transition
  -> at 2.2 s after hit: FALL
  -> land, or force beginCapture after 1.5 s
  -> SHAKE entry: bounce
  -> first shake after 1.0 s
  -> later shakes every 1.25 s: random bob1..bob6
  -> CAPTURED/capture or BROKEN_FREE/break
```

`FALL` itself does not start `bounce`; entering `SHAKE` from a non-critical state does.

Critical flow:

```text
CRITICAL/critical
  -> after 1.0 s: SHAKE without bounce
  -> after another 1.25 s: shake/result processing
```

Stock ordinary successful capture emits three bob events before `CAPTURED`. Failure emits zero to three depending on the calculated result.

Timing constraints:

| Clip | Stock length | Practical maximum without schedule changes |
| --- | ---: | ---: |
| `open` | 0.5 s | below 1.75 s |
| `bounce` | 1.0 s | 1.0 s |
| `bob1..6` | 1.0 s | 1.25 s |
| `critical` | 1.0 s | keep near 1.0 s |
| `capture` | 0.75 s | 1.0 s for ordinary balls |
| `break` | 1.2 s | 1.2 s |

## Blockbench Export Compatibility

Common export regressions:

### `geometry.unknown`

Blockbench may reset the identifier. Any compatibility gate looking for a unique identifier will stop running. Cobblemon's legacy loader then encounters per-face UV objects and logs:

```text
Expected BEGIN_ARRAY but was BEGIN_OBJECT ... cubes[...].uv
Failed to load model file ...
Unable to load model ... for ...
```

Restore a stable identifier such as:

```json
"identifier": "geometry.example_mod.custom_ball"
```

### Animated Bone Outside The Root

`PokeBallModel` registers `poke_ball` and its descendants. A root-level sibling such as `bone2` can render in Blockbench but is absent from the poser's named part tree. Put it under `poke_ball` when its transform should be independent, or under `bottom`/`lid` when it should inherit those transforms.

### Per-Face UV And Negative Sizes

Cobblemon 1.7.3 expects legacy array UVs. Modern Blockbench can export per-face objects:

```json
"uv": {
  "north": {"uv": [0, 0], "uv_size": [8, 4]}
}
```

If exact negative-size appearance matters, parse this structure before Cobblemon's loader replaces it. Bind face data to the parsed model/cubes, then restore UVs after baking. Scope every step to the unique geometry identifier. Negative dimensions also reverse geometry direction and can require explicit face winding/UV corner alignment; do not turn this on globally.

### Animation `vector` Wrappers

Some Blockbench exports wrap values as `{"vector": [...]}` while Cobblemon's tested assets use arrays directly. Recursively unwrap objects whose sole key is `vector` before loading.

### Duplicate Animation Separator

Passing an explicit prefix ending in `.` to Cobblemon's helpers can produce:

```text
Animation animation.prism_ball..throw not found in animation group prism_ball
```

Use Kotlin default arguments and pass only group plus clip.

## Isolated Implementation Pattern

For one custom animated ball:

1. Variation references a unique model and poser ID.
2. Geo has a unique identifier used by any compatibility bridge.
3. Animation filename and internal group are unique.
4. Kotlin model extends `PokeBallModel`, calls `super.registerPoses()`, and swaps pose animations/transitions to the custom group.
5. A client reload hook registers the namespaced poser factory after built-ins.
6. A client state hook returns the custom group only when the current model is the custom subclass.
7. Renderer hooks check the exact ball registry ID before changing lighting/render type.
8. Server capture effects check the exact ball ID before applying shiny, IV, or other post-capture changes.

Do not overwrite normal `poke_ball` group data. Do not use a global model-class check if other variations share that class.

## Failure Signatures

| Log/crash text | Likely cause |
| --- | --- |
| `Animation animation.<group>..<clip> not found` | Explicit prefix includes a trailing dot |
| `Animation ... not found in animation group ...` | Filename group, internal key, or poser request mismatch |
| `Expected BEGIN_ARRAY but was BEGIN_OBJECT ... uv` | Per-face UV bridge did not activate, often due to identifier reset |
| `Unable to load model ... for <ball>` | Variation points to a model that failed parsing or was not registered |
| Animation plays except one auxiliary bone | Bone is outside poser root, misspelled, or absent from geo |
| Starts at title but crashes on throw | Variation poser is lazily instantiated on first entity render |
| Long capture/break animation is cut off | Server removes the entity on the stock schedule |
