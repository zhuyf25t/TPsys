# GameScene Remaining Work Audit V2

## 1. Why V1 Is No Longer Sufficient

The previous final-phase conclusion was based too heavily on whether `GF-01 ~ GF-04` had landed, not on whether `src/scenes/GameScene.ts` had actually reached the desired terminal shape.

That conclusion is no longer accepted.

The user has explicitly reset the standard:

- old ticket completion is only historical evidence
- current code state is the only valid completion criterion
- `GameScene` must be judged by what it still directly owns today

Current fact base:

- `src/scenes/GameScene.ts` is still roughly **47 KB** after the first V2 extractions
- the old heavy chains around arena build, world-view sync, projectile runtime, and hit/damage/respawn are no longer directly implemented in full detail inside the scene, which is good
- however, several **runtime-local orchestration and resolver responsibilities still remain in the scene itself**, and they are large enough to keep the scene from feeling like a truly elegant shell

So the new question is not "are the old GF tickets done?" but:

**Does `GameScene` now contain only scene-host responsibilities?**

At the moment, the answer is still **no**.

---

## 2. Current Responsibility Inventory

After re-auditing the current `src/scenes/GameScene.ts`, the scene still owns these responsibility groups:

1. Phaser scene lifecycle and top-level update ordering
2. Camera follow / offset / occlusion hosting
3. Player actor creation and physics synchronization glue
4. DOM HUD bridge invocation
5. Scene-side VFX / floating text / afterimage / shockwave glue
6. Pickup respawn clock and pickup spawn-context assembly
7. Nearby pickup lookup used by HUD / auto-pickup prompts
8. Weapon fire orchestration, reload start, projectile spawn triggering, recoil/feedback branching
9. Dash / blink / jump trigger orchestration and motion tween hosting
10. Small geometry / lookup helpers consumed by scene-side glue
11. Legacy compatibility entrypoints that still forward into scene paths

Only groups `1` through `5` cleanly fit the desired scene-shell target. Groups `6` through `10` still require judgment.

---

## 3. Classification

## A. Responsibilities That Still Must Be Extracted

These are the remaining blocks that still prevent `GameScene` from reading like a clean scene shell / renderer host.

### A1. Projectile / hit / damage / respawn orchestration is still scene-owned

Current methods / logic:

- `updateProjectiles()`
- `explodeRocket()`
- `onProjectileHit()`
- `updateRespawnTimers()` + `respawnHero()` scene-side orchestration

Why it still must move:

- the low-level math and state mutations have been extracted, but the scene still directly owns the **combat frame orchestration**
- route branching, rocket target application, damage-result handling, player death body disable, respawn scene re-entry, and related dispatch still happen in `GameScene`
- this means the scene still reads partly like a local battle runtime, not just a renderer host

Why this matters more than the old GF-03 / GF-04 completion labels:

- old ticket names suggested the chain had been collected
- but the code terminal state shows that a dense part of the combat main chain is still being orchestrated in the scene
- under the stricter V2 standard, this is not yet acceptable

Recommended direction:

- extract a combat runtime / combat frame controller that owns:
  - projectile outcome routing
  - rocket explosion target application
  - damage-result orchestration
  - respawn readiness progression and respawn state packaging
- let `GameScene` consume effect intents and keep only Phaser-facing glue

### A2. Pickup lifecycle runtime glue

Current methods / logic:

- `updateWeaponPickups()`
- `getPickupSpawnResolverContext()`
- `findNearbyPickup()`
- `findNearbyItemPickup()`

Why it still must move:

- this is runtime-local pickup state progression, not scene lifecycle
- it still builds a fairly dense data context in the scene
- nearby pickup queries are not inherently Phaser-scene responsibilities; they are lookup helpers for runtime/HUD interaction

Why V1 under-estimated it:

- V1 focused mostly on the obviously large runtime chains
- but this pickup lifecycle block is still a real scene impurity and still contributes to the file feeling like a local battle runtime

Recommended direction:

- extract a pickup lifecycle / lookup helper that owns:
  - pickup respawn advancement
  - spawn-resolver context construction from runtime data
  - nearby pickup lookup
- let `GameScene` consume only returned values

Status note:

- this block was removed by `GF-05`
- it remains listed here only as part of the V2 audit trail

### A3. Weapon action orchestration still embedded in the scene

Current methods / logic:

- `handleWeaponFireAction()`
- `tryFireWeapon()`
- `spawnProjectile()`
- `startReload()`
- `getCurrentWeapon()`

Why it still must move:

- this is no longer just scene-side VFX glue
- the scene still branches on weapon kind and decides how many projectiles are emitted, when reload starts, and when depletion feedback appears
- this is runtime-local action orchestration that should sit next to the existing weapon controller, not inside the scene host

What should remain in the scene afterward:

- muzzle flash / pulse / spark VFX
- recoil / knockback application as actor-position glue
- Phaser-facing floating text / effect dispatch

Status note:

- this block was removed by `GF-06`
- the scene now consumes a weapon action plan instead of implementing the dense weapon-kind branching directly

### A4. Small scene-local geometry / lookup helpers that are really controller dependencies

Current methods / logic:

- `collidesWithObstacles()`
- `intersectsObstacle()`
- `normalizeVector()`
- `getCurrentWeapon()` if not moved with weapon action extraction

Why they still must move:

- these helpers mostly exist to support runtime-local controllers
- leaving them in the scene keeps controller dependencies inverted
- they are small individually, but collectively they keep too much low-level logic in `GameScene`

Extraction rule:

- do **not** move them into a generic misc utils dump
- move them only alongside the concrete controller that owns their semantics

---

## B. Responsibilities That Can Acceptably Remain in GameScene

These responsibilities now fit the desired scene-shell / renderer-host endpoint and do not need to be forced out.

### B1. Phaser lifecycle and top-level orchestration

- `constructor()`
- `preload()`
- `create()`
- `update()`

Why they can stay:

- a scene shell still needs lifecycle and explicit update ordering
- the goal is not zero orchestration, but thin orchestration

### B2. Camera host and occlusion host

- `configureCamera()`
- `updateCameraTarget()`
- `calculateCameraOffsetByPointer()`
- `updateOccludableAlpha()`

Why they can stay:

- they are tightly bound to Phaser camera objects and visual readability
- they are renderer-host concerns rather than portable runtime rules

### B3. Player actor / physics glue

- `createPlayerActor()`
- `createCameraTarget()`
- `syncPlayerHeroFromPhysics()`
- `setHeroPosition()`

Why they can stay:

- this is exactly the kind of host glue a Phaser scene should still own
- over-extracting this would add indirection with low architectural gain

### B4. Scene-side motion tween / VFX glue

- `startPlayerMotion()`
- `stopPlayerMotion()`
- `createPulse()`
- `createImpactSpark()`
- `createMuzzleBurst()`
- `createShockwave()`
- `createFloatingText()`
- `showFloatingText()`
- `createAfterimage()`
- `flashHero()`
- `updateVisualEffects()`

Why they can stay:

- they are deeply tied to Phaser tweens, display objects, and actor feedback
- they are scene-facing effect glue, not portable battle logic

### B5. HUD bridge and renderer bridge invocation

- `createHud()`
- `layoutHud()`
- `handleResize()`
- `updateHud()`
- `createHeroViews()` / `syncHeroViews()` etc. as **callers** only

Why they can stay:

- the heavy presenter / renderer logic is already extracted
- what remains is invocation glue and local data assembly that is acceptable for a scene host

---

## C. Responsibilities That Should Not Be Touched Further Right Now

These are theoretically splittable, but touching them now would likely create more risk than architectural gain.

### C1. `startPlayerMotion()` internals

Why not now:

- it is tightly coupled to dash / blink / jump feel
- tween timing, scale, afterimage cadence, and landing pulse are highly handfeel-sensitive
- the architecture gain is low compared with the regression risk

### C2. Camera micro-decomposition

Why not now:

- camera offset and occlusion are already acceptable as host responsibilities
- further decomposition is possible, but it would not materially improve the scene-shell boundary
- risk to battle readability is high

### C3. Cosmetic / legacy / debug tidy-up beyond what is strictly needed

Why not now:

- low structural value
- high diff noise
- easy to dilute the final-phase goal

---

## 4. What Must Be True Before Phase Completion Can Be Declared

`GameScene Final Decomposition Phase` should be considered complete only when **all** of the following are true:

1. `GameScene` no longer directly implements arena/world builder details
2. `GameScene` no longer directly implements hero/projectile/pickup world-view factory/sync details
3. `GameScene` no longer directly implements projectile progression / hit / damage / kill / respawn main chains
4. `GameScene` no longer directly implements pickup lifecycle runtime glue
5. `GameScene` no longer directly implements weapon action orchestration runtime glue
6. `GameScene` no longer directly orchestrates projectile / hit / damage / kill / respawn main-chain application
7. the remaining scene code can be justified line by line as scene-shell / renderer-host / actor-glue responsibility
8. if the file remains above the preferred size target, the overage is explainable as acceptable host glue rather than uncollected runtime logic

---

## 5. Practical End-State Judgment

The practical acceptable endpoint is:

`GameScene` should still own:

- scene lifecycle
- update orchestration
- camera host
- player actor / physics glue
- HUD bridge
- scene-side VFX / indicator glue
- motion tween host for handfeel-sensitive abilities

`GameScene` should no longer own:

- arena builder implementation details
- world-view factory / sync implementation details
- projectile runtime progression
- hit / damage / kill / respawn rules
- pickup lifecycle runtime logic
- weapon action runtime orchestration
- combat frame / respawn orchestration glue

Only after that state is reached should this phase be considered genuinely complete.
