# GameScene Final Tickets V2

## 1. Purpose

This document replaces the old idea that `GF-01 ~ GF-04` alone defined completion.

Those tickets were valuable, but they did **not** by themselves guarantee that `GameScene` had reached a genuinely elegant endpoint.

The tickets below are the **remaining or recently landed tickets that matter under the stricter V2 end-state**:

- `GameScene` as scene shell / renderer host / glue layer
- no remaining pickup lifecycle runtime glue in scene
- no remaining weapon action orchestration runtime glue in scene
- no remaining projectile / hit / damage / respawn orchestration glue in scene

No broad ticket expansion is intended. This list remains intentionally short.

---

## 2. Ticket Selection Rules

A ticket is included here only if it satisfies at least one of these:

- it removes a remaining runtime-local chain that still clearly does not belong in a scene host
- it materially reduces `GameScene` line weight without forcing handfeel-sensitive logic out of the scene
- it improves the final boundary between scene glue and local battle runtime

Tickets are intentionally **not** included if they are mainly:

- camera micro-cleanup
- VFX-only micro-splitting
- broad legacy cleanup
- frontend shell / route / app work
- typed contracts / backend work

---

## 3. V2 Tickets Already Landed

## GF-05: Pickup Lifecycle Extraction

### Status

- landed
- accepted

### What it removed

- pickup respawn advancement from `GameScene`
- pickup spawn-context assembly from `GameScene`
- nearby pickup lookup from `GameScene`

### Files

- `src/scenes/GameScene.ts`
- `src/features/battle/runtime-local/pickups/pickupLifecycle.ts`

## GF-06: Weapon Action Extraction

### Status

- landed
- provisional

### What it removed

- dense weapon-kind branching for fire / reload / projectile emission planning from `GameScene`
- direct scene ownership of runtime weapon action orchestration

### Files

- `src/scenes/GameScene.ts`
- `src/features/battle/runtime-local/weapons/weaponActionController.ts`

---

## 4. Remaining V2 Final Ticket

## GF-07: Combat Frame / Respawn Orchestration Extraction

### Status

- landed
- provisional

### Goal

Extract the remaining combat-frame orchestration out of `GameScene`, so the scene no longer directly owns projectile outcome routing or hit/damage/respawn application flow.

### Target scope

- `updateProjectiles()`
- `explodeRocket()`
- `onProjectileHit()`
- `updateRespawnTimers()`
- `respawnHero()` only to the extent needed to remove runtime orchestration, while keeping Phaser-facing actor/VFX glue in scene

### Expected result

- `GameScene` stops directly routing projectile outcomes
- `GameScene` stops directly applying rocket explosion damage loops
- `GameScene` stops directly applying hit/damage/death/score orchestration logic
- `GameScene` stops directly advancing respawn readiness and packaging respawn state transitions
- `GameScene` keeps only Phaser-facing glue:
  - VFX
  - floating text
  - actor body enable/disable
  - playerActor reposition/update
  - camera shake / pulse / spark / flash dispatch
  - event emission

### Allowed files

- `src/scenes/GameScene.ts`
- new file(s) under something like:
  - `src/features/battle/runtime-local/combat/combatFrameController.ts`
- and, only if truly needed, focused extensions to already-extracted modules:
  - `src/features/battle/runtime-local/projectiles/projectileController.ts`
  - `src/features/battle/runtime-local/projectiles/hitResolver.ts`
  - `src/features/battle/runtime-local/projectiles/damageResolver.ts`
  - `src/features/battle/runtime-local/session/respawnController.ts`

### Forbidden files

- `src/features/battle/runtime-local/movement/**`
- `src/features/battle/runtime-local/weapons/**`
- `src/features/battle/presenters/**`
- `src/features/battle/adapters/**`
- `src/ui/Hud.ts`
- `src/game/constants.ts`
- `src/game/weapons.ts`
- `src/game/skills.ts`

### Acceptance criteria

- build / typecheck pass
- projectile routing semantics remain unchanged
- visible hit still implies real damage application
- rocket splash application and respawn timing remain behaviorally equivalent
- `GameScene` no longer contains the dense combat-frame orchestration block

### Risks

- this is the new highest-risk remaining ticket
- any semantic drift will affect combat fairness directly
- VFX glue and runtime fairness logic are tightly interleaved, so boundary mistakes are easy

---

## 5. Tickets Intentionally Not Included Right Now

### Not included: further motion trigger extraction

Why:

- dash / blink / jump trigger glue is too close to handfeel-sensitive tween orchestration
- the gain is lower than the regression risk right now

### Not included: camera extraction

Why:

- camera host is already acceptable as scene-owned host responsibility
- this would not move the phase materially closer to the desired end-state

### Not included: VFX-only extraction

Why:

- VFX / pulse / floating-text / afterimage glue is allowed to remain in the scene
- extracting it now would mostly create indirection, not architectural improvement

---

## 6. Completion Rule After V2 Tickets

After `GF-07`, phase completion can be declared only if:

1. the old extracted boundaries (`GF-01 ~ GF-04`) are still intact
2. pickup lifecycle runtime glue is no longer scene-owned
3. weapon action runtime orchestration is no longer scene-owned
4. projectile / hit / damage / kill / respawn orchestration is no longer scene-owned
5. the remaining `GameScene` methods can be justified as host glue, camera host, actor glue, HUD bridge, or scene-side motion/VFX glue
6. either:
   - `GameScene` is now within the preferred size target
   - or the remaining size can be justified as acceptable host glue rather than uncollected runtime logic
