# GameScene Final Phase Tickets

## 1. Purpose

This document defines the **final phase** of `GameScene` decomposition.

It does **not** mechanically continue the old GS list.  
It only lists the tickets that are still worth doing now, based on the current codebase and the current project goal:

- make `GameScene` thin enough to serve as a scene shell / renderer host
- do not drift into unrelated frontend completion work
- do not over-split low-value scene-side glue

---

## 2. Ticket Selection Rules

Only tickets that satisfy at least one of these are listed:

- they remove a still-heavy runtime rule chain from `GameScene`
- they remove a still-heavy renderer-builder implementation from `GameScene`
- they materially improve the scene-shell boundary

Tickets are **not** listed if they are mainly:

- cosmetic cleanup
- broad renaming
- page shell work
- route/app work
- low-value micro-extraction

---

## 3. Recommended Final-Phase Tickets

## GF-01: Arena Builder Extraction

### Goal

Extract arena/world build implementation out of `GameScene`.

Target scope:

- `createArena()`
- `createPatternRect()`
- `createPickupPads()`
- `createArenaDecorations()`
- `createBorderWalls()`
- `createStaticObstacle()`
- `registerOccludable()`

### Allowed files

- `src/scenes/GameScene.ts`
- new file(s) under something like:
  - `src/features/battle/renderer/arena/arenaBuilder.ts`
  - optionally one small companion helper if truly needed

### Forbidden files

- `src/features/battle/runtime-local/**`
- `src/features/battle/adapters/**`
- `src/features/battle/presenters/**`
- `src/game/weapons.ts`
- `src/game/skills.ts`
- `src/ui/Hud.ts`

### Acceptance criteria

- build/typecheck pass
- map visuals and blocking layout remain the same
- `GameScene.create()` is visibly thinner
- scene only invokes the arena builder and stores returned references
- no gameplay rule changes

### Risks

- obstacle registration and occludable registration are easy to accidentally separate incorrectly
- wall-body creation must remain collision-equivalent
- map appearance drift is possible if builder extraction is sloppy

---

## GF-02: World View Sync Extraction

### Goal

Extract creation and sync of hero/projectile/pickup world views into renderer helpers.

Target scope:

- `createHeroViews()`
- `createPickupViews()`
- `createIndicators()`
- `syncHeroViews()`
- `syncProjectileViews()`
- `createProjectileView()`
- `syncPickupViews()`
- `syncIndicators()`

### Allowed files

- `src/scenes/GameScene.ts`
- new file(s) under something like:
  - `src/features/battle/renderer/entities/worldViewFactory.ts`
  - `src/features/battle/renderer/entities/worldViewSync.ts`

### Forbidden files

- `src/features/battle/runtime-local/**`
- `src/features/battle/adapters/**`
- `src/features/battle/presenters/**`
- `src/features/battle/input/**`
- `src/game/weapons.ts`
- `src/game/skills.ts`
- `src/ui/Hud.ts`

### Acceptance criteria

- build/typecheck pass
- on-screen hero bars, pickup labels, indicators, and projectile sprites behave the same
- no change to battle rules
- `GameScene` mostly delegates creation/sync to renderer helpers

### Risks

- UI-like world elements are easy to misposition by a few pixels
- reload/switch action bars must remain visually consistent
- blink target/range indicators must not drift

---

## GF-03: Projectile Runtime Controller

### Goal

Extract projectile runtime progression from `GameScene` without yet changing combat fairness rules.

Target scope:

- `updateProjectiles()`
- projectile expiry
- projectile movement step
- wall-hit routing
- rocket explosion dispatch routing

### Allowed files

- `src/scenes/GameScene.ts`
- new file(s) under something like:
  - `src/features/battle/runtime-local/projectiles/projectileController.ts`

### Forbidden files

- `src/features/battle/runtime-local/movement/**`
- `src/features/battle/runtime-local/weapons/**`
- `src/features/battle/presenters/**`
- `src/features/battle/input/**`
- `src/ui/Hud.ts`
- `src/game/constants.ts` unless absolutely unavoidable

### Acceptance criteria

- build/typecheck pass
- projectile lifetime and routing semantics remain unchanged
- scene delegates projectile progression instead of implementing the loop itself
- no accidental change to spawn formulas already collected in `projectileFactory`

### Risks

- this is fairness-adjacent
- rocket special cases can drift
- hidden coupling with debug logging and VFX trigger points is likely

---

## GF-04: Hit / Damage / Respawn Resolver

### Goal

Extract the remaining combat rules chain out of `GameScene`.

Target scope:

- `onProjectileHit()`
- `applyDamage()`
- `explodeRocket()` damage loop
- `findHeroHitAlongPath()`
- `getSegmentHitTime()`
- `updateRespawnTimers()`
- `respawnHero()`

### Allowed files

- `src/scenes/GameScene.ts`
- new file(s) under something like:
  - `src/features/battle/runtime-local/projectiles/hitResolver.ts`
  - `src/features/battle/runtime-local/projectiles/damageResolver.ts`
  - `src/features/battle/runtime-local/session/respawnController.ts`

### Forbidden files

- `src/features/battle/presenters/**`
- `src/features/battle/adapters/**`
- `src/features/battle/input/**`
- `src/ui/Hud.ts`
- `src/game/weapons.ts`
- `src/game/skills.ts`

### Acceptance criteria

- build/typecheck pass
- visible hit still implies real damage resolution
- death, score, and respawn timing stay equivalent
- `GameScene` no longer owns the dense combat rules chain

### Risks

- this is the highest-risk remaining ticket
- any semantic drift will directly affect combat fairness
- this ticket should only be attempted after GF-03 is stable

---

## 4. Tickets Explicitly Not Worth Doing Now

These are intentionally **not** recommended as final-phase tickets.

### Not worth it now: camera micro-extraction

Why:

- camera host logic is already acceptable as scene-owned glue
- battle feel risk is high
- structural gain is limited compared with remaining runtime chains

### Not worth it now: `startPlayerMotion()` micro-extraction

Why:

- it is too handfeel-sensitive
- the architecture gain is low
- it is acceptable scene-side tween/VFX glue

### Not worth it now: broad legacy cleanup

Why:

- low architectural value
- noisy diffs
- easy to create accidental regressions

### Not worth it now: broad constants/assets/layout decomposition

Why:

- worth doing eventually
- but not before `GameScene` stops owning the heavy runtime/combat chains

---

## 5. Recommended Order

Recommended order for the final phase:

1. `GF-01 Arena Builder Extraction`
2. `GF-02 World View Sync Extraction`
3. `GF-03 Projectile Runtime Controller`
4. `GF-04 Hit / Damage / Respawn Resolver`

Why this order:

- first remove large renderer-builder implementation blocks
- then remove renderer-side sync bulk
- only then move into fairness-sensitive projectile/combat runtime
- finish with the highest-risk combat rules chain last

---

## 6. Exit Condition for This Phase

This phase should stop when:

- arena build implementation is outside `GameScene`
- world-view creation/sync is outside `GameScene`
- projectile progression is outside `GameScene`
- hit/damage/respawn rules are outside `GameScene`
- `GameScene` remains as:
  - scene lifecycle
  - update orchestration
  - camera host
  - player actor / physics glue
  - HUD bridge
  - VFX trigger glue

That is the **acceptable terminal state** for `GameScene Final Decomposition Phase`.

At that point, the project can legitimately reassess whether to move toward typed contracts, backend integration, or broader frontend completion work.
