# GameScene Final Completion Report

## 1. Phase Definition

This report closes the **V2** version of `GameScene Final Decomposition Phase`.

The old conclusion that `GF-01 ~ GF-04` alone were enough was rejected. Completion is now judged by **code terminal state**, not by whether an older ticket list was exhausted.

The accepted end-state for this phase is:

- `GameScene` keeps:
  - scene lifecycle
  - update orchestration
  - camera host
  - player actor / physics glue
  - HUD bridge
  - scene-side VFX / indicator glue
- `GameScene` does **not** keep:
  - arena build implementation details
  - world view factory / sync implementation details
  - projectile runtime main chain
  - hit / damage / kill / respawn main chain
  - pickup lifecycle runtime glue
  - weapon action runtime orchestration

---

## 2. Ticket Completion Status

### Accepted

- `GF-01 Arena Builder Extraction`
- `GF-02 World View Sync Extraction`
- `GF-05 Pickup Lifecycle Extraction`

### Provisional

- `GF-03 Projectile Runtime Controller`
- `GF-04 Hit / Damage / Respawn Resolver`
- `GF-06 Weapon Action Extraction`
- `GF-07 Combat Frame / Respawn Orchestration Extraction`

Provisional means:

- business-code boundaries are clean
- `npm run build` passes
- `tsc` passes through the build pipeline
- code-level semantic audit is strong enough to adjudicate
- but reliable browser play verification is still not available in the current environment

These tickets still require later unified user handfeel acceptance.

---

## 3. What Was Extracted

### GF-01 Arena Builder Extraction

Extracted into:

- `src/features/battle/renderer/arena/arenaBuilder.ts`

Effect:

- map construction, pickup pad construction, border walls, static obstacle registration, and occludable registration are no longer implemented inside `GameScene`

### GF-02 World View Sync Extraction

Extracted into:

- `src/features/battle/renderer/entities/worldViewFactory.ts`

Effect:

- hero / projectile / pickup / indicator creation and sync rules are no longer implemented inside `GameScene`
- the scene now acts as the caller and host only

### GF-03 Projectile Runtime Controller

Extracted into:

- `src/features/battle/runtime-local/projectiles/projectileController.ts`

Effect:

- projectile progression, ttl decay, wall-hit routing, hero-hit routing, and rocket-explode routing moved out of the scene

### GF-04 Hit / Damage / Respawn Resolver

Extracted into:

- `src/features/battle/runtime-local/projectiles/hitResolver.ts`
- `src/features/battle/runtime-local/projectiles/damageResolver.ts`
- `src/features/battle/runtime-local/session/respawnController.ts`

Effect:

- hit validation, splash target selection, damage application, score mutation, respawn timer advancement, and respawn-state reset logic were no longer directly authored in `GameScene`

### GF-05 Pickup Lifecycle Extraction

Extracted into:

- `src/features/battle/runtime-local/pickups/pickupLifecycle.ts`

Effect:

- pickup respawn advancement, pickup spawn-context assembly, and nearby pickup lookup are no longer scene-owned

### GF-06 Weapon Action Extraction

Extracted into:

- `src/features/battle/runtime-local/weapons/weaponActionController.ts`

Effect:

- dense weapon-kind branching for fire / reload / projectile emission planning moved out of the scene
- `GameScene` now consumes an action plan and only applies scene-facing VFX / recoil / floating-text glue

### GF-07 Combat Frame / Respawn Orchestration Extraction

Extracted into:

- `src/features/battle/runtime-local/combat/combatFrameController.ts`

Effect:

- `GameScene` no longer directly orchestrates projectile frame routing, rocket explosion damage application, hit-result processing, or respawn readiness packaging
- the scene now consumes combat effects and applies only Phaser-facing glue:
  - VFX
  - floating text
  - actor body enable / disable
  - camera shake
  - event emission

---

## 4. Current GameScene Responsibilities

After the V2 final phase, `src/scenes/GameScene.ts` still owns:

- Phaser scene lifecycle
- top-level update orchestration
- camera host / pointer offset / occlusion host
- player actor creation and physics synchronization
- scene-side motion trigger glue
- scene-side motion tween hosting
- scene-side VFX / floating text / afterimage / pulse / shockwave glue
- DOM HUD bridge invocation
- renderer bridge invocation for world-view sync
- small scene-local geometry helpers used by host glue

This is a much narrower, host-oriented set of responsibilities than before.

---

## 5. Why These Remaining Responsibilities Are Now Acceptable

The remaining responsibilities are acceptable because they are now dominated by one of these categories:

### A. Scene lifecycle and orchestration

- Phaser still requires a scene-owned lifecycle
- explicit update ordering is a legitimate scene-host responsibility

### B. Renderer host responsibilities

- camera follow / offset / occlusion are renderer-specific host concerns
- world-view sync invocation is renderer-host glue, not runtime rule ownership

### C. Actor / physics glue

- `playerActor` creation
- body enable / disable
- scene-owned actor position synchronization

These belong naturally in the scene host.

### D. Handfeel-sensitive motion glue

- `startPlayerMotion()` and related tween glue remain scene-owned intentionally
- moving this further would increase regression risk more than architectural value

### E. Scene-side VFX / feedback glue

- pulse, spark, floating text, muzzle burst, shockwave, afterimage, hero flash

These are tightly tied to Phaser display objects and are appropriate to leave in the scene.

In short:

`GameScene` still has meaningful work, but that work is now mostly **host glue**, not **embedded battle runtime**.

---

## 6. Size Goal Check

Current approximate size after the V2 phase:

- around **1160 LOC**
- around **45 KB**

This does **not** hit the preferred ideal of `<= 35 KB`, but it does hit the preferred line-count threshold band and, more importantly, the remaining overage is explainable as acceptable scene-host glue rather than uncollected runtime logic.

That is why completion can now be justified under the V2 code-terminal-state standard.

---

## 7. Has GameScene Reached the Acceptable Endpoint?

Yes.

The decisive reason is not that a ticket list was exhausted, but that the remaining scene code now reads as:

- scene shell
- renderer host
- actor/physics glue
- HUD bridge
- scene-side motion / VFX glue

and **does not** still read like:

- arena builder
- world-view factory implementation
- local battle runtime
- projectile progression loop
- hit / damage / kill / respawn main-chain owner

Continuing to split further is now much more likely to damage handfeel or over-fragment scene glue than to produce meaningful architectural improvement.

---

## 8. Remaining Technical Debt

The remaining technical debt is now outside the narrow goal of final `GameScene` decomposition:

1. Unified user handfeel acceptance is still needed for provisional tickets:
   - `GS-10`
   - `GS-11`
   - `GS-12`
   - `GF-03`
   - `GF-04`
   - `GF-06`
   - `GF-07`
2. `GameScene` still contains some small host-side helpers that could be moved later if a concrete need appears.
3. Camera / occlusion remain hand-managed inside the scene host, which is acceptable now but could be revisited later.
4. Typed battle contracts still do not exist as a dedicated frontend contract layer.
5. The battle frontend still runs a local runtime rather than consuming a typed backend battle snapshot.

These are real debts, but they are no longer blockers for `GameScene Final Decomposition Phase`.

---

## 9. Recommended Next Direction

The next direction should **not** be more `GameScene` splitting by default.

The most reasonable next step is:

1. unified user battle play acceptance across all provisional tickets
2. then explicit choice of one next-phase direction:
   - typed battle contracts planning
   - backend battle integration planning
   - broader battle-runtime-to-backend migration planning

If one direction must be named first, the best next direction is:

- **typed battle contracts + backend integration planning**

because the frontend battle scene is now structurally thin enough to be collected as a renderer asset rather than repeatedly decomposed further.
