# GameScene Remaining Work Audit

## 1. Why This Audit Exists

This document re-audits the **current** `src/scenes/GameScene.ts` after the GS decomposition work that has already landed.

The previous battle-mainchain collection phase did **not** mean the project was ready to move into a broader frontend completion phase. The user's current direction is correct:

- the project is **still** in `GameScene Final Decomposition Phase`
- the primary goal is still to make `GameScene` thin enough to act as a **scene shell / renderer host**
- page shell, router, replay, mails, profile, discussion, and wider frontend product work should wait

Current fact base:

- `GameScene.ts` is still about **1794 lines**
- many important boundaries have already been extracted
- but `GameScene` still owns several large runtime and renderer responsibilities that are too heavy for the desired end state

---

## 2. Current Remaining Responsibility Overview

After GS-01 ~ GS-14, `GameScene` still owns these responsibility groups:

1. Phaser scene lifecycle and top-level update orchestration
2. Arena construction and obstacle / occludable registration
3. Camera follow, pointer offset, and occlusion alpha hosting
4. Player physics actor sync and movement glue
5. Dash / blink / jump trigger glue
6. Automatic pickup presentation glue
7. Weapon fire trigger glue
8. Projectile runtime update loop
9. Hit / damage / kill / respawn chain
10. Hero / projectile / pickup world-view sync
11. World-space VFX trigger and floating text glue
12. HUD bridge invocation and debug line injection
13. Small geometry / lookup helpers and legacy bridge leftovers

This means the file is **thinner than before**, but it is **not yet a clean scene shell**.

---

## 3. Classification

## A. Responsibilities That Still Must Be Extracted

These are the parts that should continue to move out of `GameScene`. If they remain there, the scene will still be a mixed runtime host instead of a renderer host.

### A1. Projectile runtime update chain

Current methods:

- `updateProjectiles()`
- `spawnProjectile()` glue around projectile creation still partly scene-owned
- rocket expiry / wall-hit / hero-hit routing inside the scene

Why it must move:

- this is runtime combat progression, not scene hosting
- it is still one of the largest dense logic blocks inside the scene
- it prevents `GameScene` from being a thin renderer shell

Recommended direction:

- extract a `projectileController` or `projectileRuntime` layer
- keep sprite creation and VFX trigger points in the scene
- move projectile movement, expiry, wall-hit routing, and hit dispatch outside

### A2. Hit / damage / kill / respawn main chain

Current methods:

- `onProjectileHit()`
- `applyDamage()`
- `explodeRocket()`
- `findHeroHitAlongPath()`
- `getSegmentHitTime()`
- `updateRespawnTimers()`
- `respawnHero()`

Why it must move:

- this is the biggest remaining rules-heavy chain
- it is the closest thing to a local battle-runtime service still embedded in the scene
- long-term, this is exactly the logic that should map to battle runtime / backend battle service boundaries

Recommended direction:

- separate hit resolution from scene
- separate damage / kill / respawn progression from scene
- let the scene consume outputs and trigger VFX, instead of deciding fairness-critical rules itself

### A3. Arena builder and obstacle registration

Current methods:

- `createArena()`
- `createPatternRect()`
- `createPickupPads()`
- `createArenaDecorations()`
- `createBorderWalls()`
- `createStaticObstacle()`
- `registerOccludable()`

Why it must move:

- this is renderer-side build logic, but it is still too large and too specialized to stay inside the scene class
- the scene should host the build process, not contain the full arena builder implementation
- this is one of the clearest remaining non-runtime extractions

Recommended direction:

- extract an `arenaBuilder`
- keep the scene responsible only for invoking the builder and retaining returned references

### A4. World view factories and sync helpers

Current methods:

- `createHeroViews()`
- `createPickupViews()`
- `createIndicators()`
- `syncHeroViews()`
- `syncProjectileViews()`
- `createProjectileView()`
- `syncPickupViews()`
- `syncIndicators()`

Why it must move:

- this is still renderer logic, but it is not scene-shell logic
- it is large enough to justify a dedicated renderer-side `viewFactory` / `worldViewSync` module
- it currently mixes creation, label positioning, bar positioning, visibility gating, and small presentation rules

Recommended direction:

- extract world-view factories and sync helpers
- keep the scene as the caller

### A5. Remaining small runtime-local geometry and lookup helpers

Current methods:

- `findNearbyPickup()`
- `findNearbyItemPickup()`
- `collidesWithObstacles()`
- `intersectsObstacle()`
- `isInsideWorld()`
- `normalizeVector()`

Why it must move:

- these are not intrinsically scene lifecycle responsibilities
- they are useful helper boundaries for the projectile / motion / pickup layers
- leaving them in `GameScene` keeps too much low-level utility knowledge in the scene

Recommended direction:

- move only if needed by final-phase tickets
- do not create a generic utility graveyard; move them alongside the concrete controller that consumes them

---

## B. Responsibilities That Can Acceptably Remain in GameScene

These are not ideal candidates for further decomposition right now, because they are natural scene-host responsibilities or scene-side glue.

### B1. Scene lifecycle and top-level orchestration

Current methods:

- `constructor()`
- `preload()`
- `create()`
- `update()`

Why they can stay:

- Phaser scene lifecycle belongs in the scene
- the goal is not to eliminate orchestration, but to make orchestration thin and explicit
- a renderer host still needs a top-level update order

Target state:

- `update()` should orchestrate clearly
- but the heavy rule blocks inside it should keep shrinking

### B2. Camera host and occlusion host

Current methods:

- `configureCamera()`
- `updateCameraTarget()`
- `calculateCameraOffsetByPointer()`
- `updateOccludableAlpha()`

Why they can stay:

- these are deeply tied to Phaser camera instances and world objects
- they are renderer-host responsibilities
- they define battle readability and camera feel in a scene-specific way

Important note:

- they can be extracted later into a `cameraDirector`
- but they are already acceptable to leave in the scene if the file is otherwise thin enough

### B3. Scene-side VFX trigger glue

Current methods:

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

- these are tightly tied to Phaser display objects and tween/effect lifetime
- they are part of renderer feedback, not battle rules
- even if some helper extraction is possible, there is no strong architectural pressure to move all of them out

### B4. DOM HUD bridge invocation

Current methods:

- `createHud()`
- `layoutHud()`
- `handleResize()`
- `updateHud()`

Why they can stay:

- `hudPresenter` is already extracted
- `Hud.ts` is already a pure DOM renderer
- the remaining role of `GameScene` here is largely bridge glue:
  - collect scene-side local data
  - call presenter
  - hand the result to `Hud`

This is already close to an acceptable endpoint.

### B5. Scene-owned player actor / camera target / physics glue

Current methods:

- `createPlayerActor()`
- `createCameraTarget()`
- `syncPlayerHeroFromPhysics()`
- `setHeroPosition()`

Why they can stay:

- these are exactly the sort of scene-side bridge glue a Phaser renderer host should still own
- trying to over-extract them now would create indirection with little architectural gain

---

## C. Responsibilities That Should Not Be Touched Further Right Now

These are the parts that could theoretically be split more, but doing so now would likely create more risk than value.

### C1. `startPlayerMotion()` internals

Current method:

- `startPlayerMotion()`

Why not to keep touching it:

- this is one of the most handfeel-sensitive functions in the whole battle frontend
- it controls tween timing, scale changes, afterimage timing, and motion presentation feel
- recent GS-10 work already proved that even nearby geometry extraction can drift semantics

Conclusion:

- keep it scene-owned for now
- only revisit if there is a very explicit user-approved motion-feel ticket

### C2. Fine-grained camera feel internals

Current methods:

- `configureCamera()`
- `updateCameraTarget()`
- `calculateCameraOffsetByPointer()`
- `updateOccludableAlpha()`

Why not to over-split now:

- camera feel is battle readability
- pointer offset and occlusion are already good enough to be treated as protected battle assets
- extracting every sub-step now would add risk without unlocking major architecture wins

Conclusion:

- leave them as acceptable scene-owned host logic for this phase

### C3. Cosmetic or legacy cleanup beyond current isolation

Examples:

- removing `legacyHandle*` methods entirely
- broad naming cleanup
- broad constants / assets / layout cleanup
- cosmetic restructuring just because modules now exist

Why not now:

- low architectural value compared with remaining runtime-heavy work
- can create noisy diffs and accidental behavior drift
- distracts from the real remaining target: shrinking the rules-heavy scene core

### C4. Deep VFX micro-extraction

Examples:

- splitting every effect helper into separate files
- moving every floating-text or pulse helper out of scene

Why not now:

- scene-side VFX glue is acceptable
- this is not where the remaining structural risk lives
- the cost/benefit is poor at this stage

---

## 4. What "Good Enough" Should Mean

`GameScene` does **not** need to become empty.

A reasonable acceptable endpoint is:

- `GameScene` owns:
  - Phaser lifecycle
  - top-level update order
  - camera host
  - player actor / physics glue
  - renderer-side VFX triggers
  - HUD bridge invocation
  - world-view sync invocation
- extracted modules own:
  - input mapping
  - pickup rules
  - timers
  - movement progression
  - motion destination rules
  - weapon state rules
  - projectile creation
  - remaining projectile runtime / hit / damage / respawn rules
  - arena build implementation
  - world-view factories / sync helpers

In plain terms:

`GameScene` is acceptable when it reads like a **Phaser host and renderer coordinator**, not like a local battle server.

---

## 5. Practical End-State Threshold

I would consider `GameScene` "acceptably decomposed" for this project when all of the following are true:

1. Projectile update / hit / damage / kill / respawn no longer live as a dense rules chain in the scene
2. Arena construction implementation is no longer embedded directly in the scene
3. World-view creation/sync is mostly delegated to renderer helpers
4. The scene update loop mostly reads as orchestration rather than rule implementation
5. `startPlayerMotion()`, camera host, HUD bridge, and VFX glue remain protected as scene-side host logic

That is the correct endpoint for this phase.

It is **not** necessary to erase every helper from the scene.

---

## 6. Final Audit Conclusion

The project cannot move into a broader frontend completion phase yet because `GameScene` is still carrying too much core battle runtime responsibility.

The remaining work is not "more pages" or "more product shell".  
The remaining work is:

- finish collecting the remaining rule-heavy and renderer-builder-heavy blocks out of `GameScene`
- stop once `GameScene` becomes a clean renderer host
- only then reassess whether the project is ready for typed contracts, backend integration, or wider frontend product work
