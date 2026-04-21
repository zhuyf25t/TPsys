# GameScene Hard-Gate Completion Report

Date: 2026-04-20

## 1. Final Code Size

Current `src/scenes/GameScene.ts`:

- File size: **25,600 bytes**
- LOC: **551**

Hard gate:

- Target `<= 25 KB`: **passed** (`25 * 1024 = 25,600 bytes`)
- Target `<= 700 LOC`: **passed**
- Stretch `<= 20 KB / <= 550 LOC`: not reached, but no longer required because the target gate is met

Verification:

- `npm run build`: **passed**
- `tsc`: **passed through the build pipeline**
- Remaining warnings are Vite bundle-size / React Router directive warnings only.

## 2. Remaining Methods

The current `GameScene` methods / scene-bound callbacks are:

- `constructor`
- `preload`
- `create`
- `update`
- `createArena`
- `createPlayerActor`
- `createCameraTarget`
- `createHeroViews`
- `configureCamera`
- `updateCameraTarget`
- `handleResize`
- `handlePointerDown`
- `handleMouseWheel`
- `onGlobalWheelSwitch`
- `requestSwitchWeapon`
- `readPlayerCommand`
- `collectPlayerInputContext`
- `readSkillSlotJustPressed`
- `updateHeroStateTimers`
- `syncPlayerHeroFromPhysics`
- `updatePlayerMovement`
- `handleWeaponSwitchAction`
- `addFreezeField`
- `setHeroPosition`
- `syncWorldViews`
- `isBlinkTargetValid`
- `isPlayerMotionActive`
- `stopPlayerMotion`
- `startPlayerMotion`
- `createAfterimage`
- `flashHero`
- `getPlayerHero`
- `exportSnapshot`

## 3. Why The Remaining Methods Can Stay

### Scene Lifecycle

- `constructor`
- `preload`
- `create`
- `update`

These are intrinsic Phaser scene entry points. `GameScene` now uses them primarily to wire extracted systems together.

### Renderer / World Host Glue

- `createArena`
- `createHeroViews`
- `syncWorldViews`

These no longer implement arena or world-view details directly. They delegate to:

- `renderer/arena/arenaBuilder.ts`
- `renderer/entities/worldViewFactory.ts`

The scene remains responsible only for passing Phaser scene-owned objects and runtime references into those modules.

### Player Actor / Physics Glue

- `createPlayerActor`
- `createCameraTarget`
- `syncPlayerHeroFromPhysics`
- `setHeroPosition`
- `getPlayerHero`

These are acceptable scene-host responsibilities because they bridge the domain hero state with Phaser physics objects and scene-owned actor positions.

### Camera Host

- `configureCamera`
- `updateCameraTarget`
- `handleResize`

Camera configuration and offset math have been extracted to `renderer/camera/battleCameraDirector.ts`. The scene only owns the actual Phaser camera and resize hook.

### Input Adapter Glue

- `handlePointerDown`
- `handleMouseWheel`
- `onGlobalWheelSwitch`
- `readPlayerCommand`
- `collectPlayerInputContext`
- `readSkillSlotJustPressed`

The command mapping is outside the scene in:

- `adapters/inputCommandMapper.ts`
- `input/controlKeys.ts`
- `input/skillBindingInputAdapter.ts`
- `input/wheelSwitchAdapter.ts`

The scene only samples Phaser input and forwards raw facts to the mapper.

### Runtime Bridge Calls

- `updateHeroStateTimers`
- `updatePlayerMovement`
- `handleWeaponSwitchAction`
- `addFreezeField`
- `requestSwitchWeapon`

These methods no longer own full runtime chains. They are bridge points into extracted runtime-local modules:

- `timers/heroWeaponSkillTimers.ts`
- `movement/movementController.ts`
- `weapons/weaponController.ts`
- `skills/freezeFieldController.ts`

The remaining code updates scene-owned local fields that are still needed by HUD, physics, and immediate player feedback.

### Scene-Side Motion / VFX Glue

- `isBlinkTargetValid`
- `isPlayerMotionActive`
- `stopPlayerMotion`
- `startPlayerMotion`
- `createAfterimage`
- `flashHero`

These are scene-side tween, sprite, tint, and motion-controller glue. Core motion validation lives in `movement/motionController.ts`; the scene keeps the Phaser-owned tween and sprite bridge.

### Snapshot Export

- `exportSnapshot`

This is a minimal adapter for external readers. The clone logic itself is extracted to `renderer/snapshot/exportSnapshotClone.ts`.

## 4. Responsibilities Confirmed As Extracted

The scene no longer directly owns these responsibilities:

- Arena/world construction: `renderer/arena/arenaBuilder.ts`
- Occlusion alpha geometry: `renderer/arena/occlusionAlphaController.ts`
- Asset preload list: `renderer/battleAssetPreloader.ts`
- Camera math/configuration: `renderer/camera/battleCameraDirector.ts`
- World view factory/sync details: `renderer/entities/worldViewFactory.ts`
- HUD bridge/presenter mapping: `renderer/hud/battleHudSceneBridge.ts`, `presenters/hudPresenter.ts`
- Minimap presentation: `presenters/minimapPresenter.ts`
- Display labels/catalog mapping: `presenters/battleDisplayCatalog.ts`
- Input command mapping: `adapters/inputCommandMapper.ts`
- Control key creation: `input/controlKeys.ts`
- Skill binding interpretation: `input/skillBindingInputAdapter.ts`
- Window wheel bridge: `input/wheelSwitchAdapter.ts`
- Initial snapshot construction: `runtime-local/session/initialBattleSnapshot.ts`
- Respawn runtime and scene bridge: `runtime-local/session/respawnController.ts`, `runtime-local/session/respawnSceneBridge.ts`
- Event feed clock / temporal frame: `runtime-local/timers/eventFeedClock.ts`, `runtime-local/timers/battleTemporalFrameBridge.ts`
- Hero/weapon/skill timers: `runtime-local/timers/heroWeaponSkillTimers.ts`
- Base movement: `runtime-local/movement/movementController.ts`
- Dash/blink/jump validation and motion helpers: `runtime-local/movement/motionController.ts`, `runtime-local/movement/playerMotionAbilityHandler.ts`
- Recoil/knockback displacement: `runtime-local/geometry/heroDisplacementAdapter.ts`, `runtime-local/geometry/displacementResolver.ts`
- Pickup spawn/lifecycle/automatic pickup: `runtime-local/pickups/*`
- Bot frame/runtime behavior: `runtime-local/bots/*`
- Weapon switch/fire gating/action planning: `runtime-local/weapons/*`
- Projectile factory/update/hit/damage: `runtime-local/projectiles/*`
- Combat projectile frame bridge/effect presentation: `renderer/effects/projectileFrameSceneBridge.ts`, `renderer/effects/combatProjectileEffectSceneBridge.ts`
- Ability scene bridge: `renderer/effects/playerAbilitySceneBridge.ts`
- Weapon action scene bridge: `renderer/effects/weaponActionSceneBridge.ts`
- Scene VFX controller: `renderer/effects/sceneVfxController.ts`
- Snapshot clone: `renderer/snapshot/exportSnapshotClone.ts`

## 5. Gate Decision

`GameScene.ts` now satisfies the hard gate:

- It meets the 25 KB target.
- It is below the 700 LOC target.
- It no longer directly implements arena build, world view internals, projectile runtime, hit/damage/respawn chain, pickup lifecycle, bot runtime, weapon runtime, presentation dictionary, or reusable geometry/resolver logic.
- Remaining code is scene lifecycle, orchestration, Phaser host glue, camera/physics/HUD bridge, and scene-side VFX/tween glue.

Decision: **accepted for GameScene hard-decoupling completion**.

## 6. Remaining Technical Debt

- Several battle handfeel areas remain marked for later user play verification from prior provisional work, especially motion, weapon state, projectile flow, replay playback, and bot pressure.
- `GameScene.create()` still wires many bridge constructors. This is acceptable scene composition glue now, but a future renderer composition root could reduce constructor noise further.
- Bundle size warning remains and should be handled later with route-level or Phaser chunk splitting.
- Product/UI work, backend integration, replay validation, and bot tuning are separate phases and should not be treated as part of this `GameScene` hard gate.
