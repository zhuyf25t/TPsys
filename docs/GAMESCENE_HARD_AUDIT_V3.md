# GameScene Hard Audit V3

## 1. Why This Audit Exists

This V3 audit replaces all earlier completion claims based on old ticket sets.

From now on, phase completion is judged only by the **real code terminal state** of `src/scenes/GameScene.ts`.

Current fact base at the start of this audit:

- `GameScene.ts` is about **1162 LOC**
- `GameScene.ts` is about **45 KB**
- the scene is much thinner than the early prototype
- but it still contains several methods that are not yet acceptable under the hard-gate standard

The user has explicitly rejected “tickets are done” as a completion criterion.

So this document answers the only valid question:

**Method by method, which pieces still do not belong inside the final scene host?**

---

## 2. Classification Rules

### A = Must move out

The method still clearly belongs in one of these instead of the scene:

- runtime-local controller
- presenter
- formatter / catalog
- builder
- resolver
- sync module
- adapter module

### B = Acceptable to remain

The method is a legitimate scene-shell / renderer-host / actor-glue responsibility.

### C = Do not touch now

The method is theoretically splittable, but touching it now would create more risk than architectural gain.

---

## 3. Initial Method-by-Method Audit

| Method | Class | Why | Destination if A |
| --- | --- | --- | --- |
| `constructor` | B | Phaser scene lifecycle | |
| `preload` | B | asset preload belongs to scene lifecycle | |
| `create` | B | top-level scene setup orchestration | |
| `update` | B | top-level update orchestration | |
| `createControls` | B | scene-local input registration glue | |
| `createArena` | B | now only invokes extracted arena builder | |
| `createPlayerActor` | B | player actor / physics glue | |
| `createCameraTarget` | B | camera host glue | |
| `createHeroViews` | B | now only invokes extracted world-view factory | |
| `createPickupViews` | C | trivial no-op leftover; low value | |
| `createIndicators` | C | trivial no-op leftover; low value | |
| `createHud` | B | HUD bridge setup | |
| `configureCamera` | B | camera host responsibility | |
| `updateCameraTarget` | B | camera host responsibility | |
| `calculateCameraOffsetByPointer` | B | camera host / readability glue | |
| `layoutHud` | B | HUD bridge glue | |
| `handleResize` | B | scene host resize glue | |
| `updateOccludableAlpha` | B | scene-owned renderer readability logic | |
| `formatMatchTime` | A | presentation formatter helper should not remain in scene | formatter / presentation catalog |
| `handlePointerDown` | B | scene input bridge | |
| `handleMouseWheel` | B | scene input bridge / wheel adapter consumer | |
| `onGlobalWheelSwitch` | B | scene input bridge | |
| `requestSwitchWeapon` | B | scene-local switch trigger glue around extracted controller | |
| `readPlayerCommand` | B | thin bridge to input mapper | |
| `collectPlayerInputContext` | B | scene-side input collection glue | |
| `updateHeroStateTimers` | B | thin bridge to extracted timer helper | |
| `updateEvents` | B | thin bridge to extracted event clock | |
| `updateWeaponPickups` | B | thin bridge to extracted pickup lifecycle helper | |
| `handleRespawnEffect` | B | Phaser-facing respawn glue only | |
| `syncPlayerHeroFromPhysics` | B | actor / physics glue | |
| `updatePlayerMovement` | B | thin bridge to extracted movement controller | |
| `handleAutomaticWeaponPickup` | B | thin bridge to pickup controller | |
| `handleAutomaticItemPickup` | B | thin bridge to pickup controller | |
| `handleAutomaticWeaponPickupPresentation` | B | scene-facing pickup feedback glue | |
| `handleAutomaticItemPickupPresentation` | B | scene-facing pickup feedback glue | |
| `handleSkillInputs` | B | skill trigger glue around extracted motion helpers | |
| `handleJumpAction` | B | jump trigger glue around motion host | |
| `handleWeaponSwitchAction` | B | switch trigger glue around extracted controller | |
| `handleWeaponFireAction` | B | now mostly consumes extracted weapon action plan and applies scene-side effects | |
| `legacyHandleJump` | A | legacy runtime path with old jump logic; not acceptable in final scene | remove or move to legacy adapter module |
| `legacyHandleWeaponSwitch` | A | legacy compatibility entrypoint; no final scene value | remove or move to legacy adapter module |
| `legacyHandleWeaponFire` | A | legacy compatibility entrypoint; no final scene value | remove or move to legacy adapter module |
| `updateProjectiles` | B | now thin bridge to extracted combat-frame controller | |
| `handleCombatProjectileEffect` | B | Phaser-facing combat/VFX/actor glue | |
| `createPulse` | B | scene-side VFX glue | |
| `createImpactSpark` | B | scene-side VFX glue | |
| `createMuzzleBurst` | B | scene-side VFX glue | |
| `createShockwave` | B | scene-side VFX glue | |
| `createFloatingText` | B | scene-side VFX / text feedback glue | |
| `showFloatingText` | B | scene-side VFX / tone bridge | |
| `applyRecoil` | B | actor-position glue driven by runtime plan | |
| `applyKnockback` | B | actor-position glue driven by runtime result | |
| `setHeroPosition` | B | actor / physics glue | |
| `updateVisualEffects` | B | scene-side effect lifetime host | |
| `syncHeroViews` | B | thin bridge to extracted world-view sync | |
| `syncProjectileViews` | B | thin bridge to extracted world-view sync | |
| `syncPickupViews` | B | thin bridge to extracted world-view sync | |
| `syncIndicators` | B | thin bridge to extracted world-view sync | |
| `updateHud` | B | HUD bridge invocation, although it still depends on some formatter/catalog residue | |
| `pushEvent` | B | scene-local event feed bridge | |
| `isBlinkTargetValid` | B | thin bridge to motion validity helper | |
| `isPlayerMotionActive` | B | scene-local tween state check | |
| `stopPlayerMotion` | B | scene-side motion host glue | |
| `startPlayerMotion` | C | handfeel-sensitive tween/VFX host; too risky to split further now | |
| `getBaseHeroScale` | B | small renderer host constant bridge | |
| `createAfterimage` | B | scene-side VFX glue | |
| `flashHero` | B | scene-side VFX glue | |
| `getWeaponLabel` | A | label/dictionary helper explicitly disallowed in final scene | formatter / catalog |
| `getProjectileLabel` | A | label/dictionary helper explicitly disallowed in final scene | formatter / catalog |
| `getItemPickupLabel` | A | label/dictionary helper explicitly disallowed in final scene | formatter / catalog |
| `findDashDestination` | A | thin runtime geometry wrapper still better owned by motion helper or scene adapter module | motion helper / geometry adapter |
| `collidesWithObstacles` | A | runtime-local obstacle collision helper, not host-only logic | geometry / collision helper |
| `intersectsObstacle` | A | runtime-local collision primitive | geometry / collision helper |
| `getPlayerHero` | B | acceptable scene-local snapshot accessor | |
| `normalizeVector` | A | generic runtime-local geometry helper, should not stay in final scene | geometry helper |
| `tintPickupSprite` | A | presentation dictionary helper; should be catalog-driven, not scene-owned | presentation catalog |

---

## 4. Summary of A-Class Methods

The remaining A-class residue falls into four concrete cleanup buckets:

### A1. Presentation / label / catalog helpers

- `formatMatchTime`
- `getWeaponLabel`
- `getProjectileLabel`
- `getItemPickupLabel`
- `tintPickupSprite`

These are explicitly disallowed by the hard-gate standard.

### A2. Runtime-local geometry helpers

- `findDashDestination`
- `collidesWithObstacles`
- `intersectsObstacle`
- `normalizeVector`

These still keep low-level runtime knowledge inside the scene.

### A3. Legacy runtime residue

- `legacyHandleJump`
- `legacyHandleWeaponSwitch`
- `legacyHandleWeaponFire`

`legacyHandleJump` is especially important because it still contains a real old runtime path, not just a trivial forwarder.

### A4. Trivial no-op residue

- `createPickupViews`
- `createIndicators`

These are not architecturally dangerous, but they are dead scene noise and should not remain in a hard-gate final scene unless there is a concrete reason.

---

## 5. Summary of B-Class Methods

The B-class methods are acceptable because they fit one of these categories:

- scene lifecycle
- update orchestration
- camera host
- player actor / physics glue
- HUD bridge
- scene-side motion tween glue
- scene-side VFX / indicator glue
- thin bridge to already-extracted controllers / presenters / builders

This is the target final identity of `GameScene`.

---

## 6. Summary of C-Class Methods

Only a few methods belong in C:

- `startPlayerMotion`
- `createPickupViews`
- `createIndicators`

Reasoning:

- `startPlayerMotion` is too handfeel-sensitive to keep splitting now
- the no-op methods can be removed opportunistically, but they are not where the real architectural value lies

---

## 7. Hard-Gate Conclusion

At the initial V3 audit state, `GameScene` was **close**, but it still did **not** pass the hard gate.

The reasons are concrete and limited:

1. presentation/dictionary helpers still remain inside the scene
2. runtime-local geometry helpers still remain inside the scene
3. legacy runtime residue still remains inside the scene

---

## 8. Post-Cleanup Recheck

After `GF-08`, `GF-09`, and `GF-10`, the former A-class residue has been removed:

- `formatMatchTime`
- `getWeaponLabel`
- `getProjectileLabel`
- `getItemPickupLabel`
- `tintPickupSprite`
- `findDashDestination`
- `collidesWithObstacles`
- `intersectsObstacle`
- `normalizeVector`
- `legacyHandleJump`
- `legacyHandleWeaponSwitch`
- `legacyHandleWeaponFire`
- trivial no-op residue around `createPickupViews` / `createIndicators`

### Current hard-gate result

At the **current** code state:

- no A-class methods remain inside `GameScene`
- the remaining methods can now be justified as:
  - scene lifecycle
  - update orchestration
  - camera host
  - actor / physics glue
  - HUD bridge
  - scene-side motion tween glue
  - scene-side VFX / indicator glue
  - thin bridge calls into already-extracted controllers / presenters / builders

This means the hard-gate audit now passes.

These are small compared with the old giant runtime chains, but under the hard-gate standard they are still sufficient to block final completion.
