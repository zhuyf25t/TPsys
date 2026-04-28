# GameScene Hard-Gate Completion Report

Date: 2026-04-27 Asia/Shanghai

Scope: GS-HARD-GATE-REFRESH audit plus BP-40D follow-up. BP-40D added one scene-host clock adapter method so shared authoritative HUD time is not overwritten by Phaser scene-local time.

## 1. Final Code Size

Current target file:

- `frontend/src/scenes/GameScene.ts`
- File size: **24,989 bytes** (**24.40 KiB**)
- LOC: **522 physical lines**
- Non-empty LOC: **496**

Hard gate result:

- Target `<= 25 KB`: **passed**
- Target `<= 700 LOC`: **passed**
- Stretch `<= 20 KB`: not reached
- Stretch `<= 550 LOC`: **passed by physical LOC and non-empty LOC**

Verification:

- BP-40D worker ticket: `npm run build` **passed** after the clock adapter change.
- Architect review: `npm run build`, `npm run demo:bp40-freshness`, `scripts/bp40-browser-session-freshness-smoke.ps1`, and short MixedMovement render-feel smoke all **passed**.
- No git commands were run.

## 2. Remaining Methods

Current `GameScene` methods / callbacks:

- `constructor`
- `preload`
- `create`
- `update`
- `advanceRuntimeLocalClock`
- `createArena`
- `createPlayerActor`
- `createHeroViews`
- `configureWorldBounds`
- `updateCameraTarget`
- `handleResize`
- `renderHud`
- `handlePointerDown`
- `handleMouseWheel`
- `onGlobalWheelSwitch`
- `readPlayerCommand`
- `syncPlayerHeroFromPhysics`
- `setHeroPosition`
- `syncWorldViews`
- `isPlayerMotionActive`
- `stopPlayerMotion`
- `startPlayerMotion`
- `createAfterimage`
- `flashHero`
- `getPlayerHero`
- `setAuthoritativePreparedSkill`
- `applyAuthoritativePreparedSkillOverride`
- `applyAuthoritativeFrame`
- `exportSnapshot`
- `isLatestPlayerCommandMovementActive`

Methods no longer present in the audited file and therefore removed from this report:

- `createCameraTarget`
- `configureCamera`
- `configureCameraBounds`
- `getHeroDisplayPosition`

## 3. Why Remaining Methods Belong In The Scene Host

`constructor`, `preload`, `create`, and `update` are Phaser lifecycle and top-level orchestration. `create` remains the scene composition root for extracted bridges and Phaser-owned objects. `update` delegates frame work into bridges instead of directly implementing battle runtime chains.

`advanceRuntimeLocalClock` is minimal scene-host clock adapter glue. It preserves the local-runtime Phaser clock path while preventing shared authoritative runtime from overwriting server authoritative elapsed time with scene-local time; it does not implement battle duration, queue timing, or result semantics.

`createArena` is a renderer-host entry point. The arena implementation details live in `renderer/arena/arenaBuilder.ts`; the scene supplies Phaser context and scene-owned wall / occlusion collections.

`createPlayerActor` is player actor / physics glue. Actor creation and authoritative frame bridge construction live in `renderer/gameSceneHeroActorBridge.ts`; the scene only stores the Phaser handles it owns.

`createHeroViews` is renderer-host setup glue. World view state creation lives in `renderer/entities/worldViewFactory.ts`; the scene retains the returned view maps for later bridge calls.

`configureWorldBounds` is Phaser physics world glue. It applies the current snapshot world size to the Arcade physics world and does not contain arena-building logic.

`updateCameraTarget` is camera host glue. Camera target math lives in `renderer/gameSceneCameraBridge.ts`; the scene supplies active pointer, viewport size, display position, and the scene-owned camera target.

`handleResize` is viewport / HUD host glue. It resizes the Phaser camera and relays layout changes to the HUD bridge.

`renderHud` is a HUD bridge call. HUD presentation, formatting, and display mapping live outside the scene in `renderer/hud/battleHudSceneBridge.ts` and related presenter code.

`handlePointerDown`, `handleMouseWheel`, `onGlobalWheelSwitch`, and `readPlayerCommand` are Phaser/window input adapter glue. Command reading lives in `renderer/gameSceneInputBridge.ts`; wheel switch behavior lives in `runtime-local/weapons/weaponWheelSwitchSceneBridge.ts`.

`syncPlayerHeroFromPhysics` and `setHeroPosition` are minimal physics / actor synchronization glue. They copy positions between domain heroes and scene-owned Phaser Arcade objects without owning movement resolution.

`syncWorldViews` is a renderer-host sync entry point. View sync internals live in `renderer/gameScenePresentationBridge.ts` and renderer entity helpers.

`isPlayerMotionActive`, `stopPlayerMotion`, `startPlayerMotion`, and `createAfterimage` are tween / VFX adapter methods. Motion implementation lives in `renderer/effects/playerMotionTweenController.ts`; the scene exposes scene-owned tween/VFX handles to extracted bridges.

`flashHero` is scene-side VFX glue. It locates the relevant scene-owned view and delegates the flash effect to `renderer/gameSceneHeroActorBridge.ts`.

`getPlayerHero` is a tiny callback-compatible lookup wrapper. Lookup behavior lives in `renderer/snapshot/playerHeroLookup.ts`.

`setAuthoritativePreparedSkill` and `applyAuthoritativePreparedSkillOverride` are shared-authoritative renderer-host adapter methods. They keep the local renderer's player prepared-skill display/input override synchronized with the current snapshot and do not implement skill runtime behavior.

`applyAuthoritativeFrame` is the external scene adapter for authoritative runtime frames. Snapshot application and local correction live in `renderer/authoritativeFrameSceneBridge.ts` and `renderer/authoritativeFrameSnapshotApplier.ts`; the scene supplies Phaser-local bounds and display glue after the frame is applied.

`exportSnapshot` is an external snapshot adapter. Clone implementation lives in `renderer/snapshot/exportSnapshotClone.ts`.

`isLatestPlayerCommandMovementActive` is a minimal input-state predicate used by the authoritative renderer bridge to distinguish active local movement from remote correction. It does not resolve movement geometry or apply movement rules.

## 4. Extracted Responsibilities

Confirmed extracted out of `GameScene`:

- Arena/world build details: `renderer/arena/arenaBuilder.ts`
- Asset preloading: `renderer/battleAssetPreloader.ts`
- Camera target and camera bounds helpers: `renderer/gameSceneCameraBridge.ts`
- Player actor creation and hero flash helper: `renderer/gameSceneHeroActorBridge.ts`
- Phaser input command reading: `renderer/gameSceneInputBridge.ts`
- World view state creation and entity view internals: `renderer/entities/worldViewFactory.ts`
- World view sync, HUD render bridge, and occlusion update entry points: `renderer/gameScenePresentationBridge.ts`
- Hero presentation scale mapping: `renderer/entities/heroPresentationScale.ts`
- HUD layout and presentation: `renderer/hud/battleHudSceneBridge.ts`
- Player hero lookup: `renderer/snapshot/playerHeroLookup.ts`
- Snapshot cloning: `renderer/snapshot/exportSnapshotClone.ts`
- Initial battle snapshot creation: `runtime-local/session/initialBattleSnapshot.ts`
- Local battle frame orchestration: `runtime-local/session/localBattleFrameSceneBridge.ts`
- Temporal frame/event feed handling: `runtime-local/timers/battleTemporalFrameBridge.ts`
- Bot frame runtime: `runtime-local/bots/botFrameBridge.ts` and related bot modules
- Pickup lifecycle runtime: `runtime-local/pickups/pickupFrameBridge.ts` and related pickup modules
- Respawn runtime: `runtime-local/session/respawnSceneBridge.ts` and `runtime-local/session/respawnController.ts`
- Weapon switch state and wheel switching: `runtime-local/weapons/weaponSwitchStateBridge.ts`, `runtime-local/weapons/weaponWheelSwitchSceneBridge.ts`
- Weapon action runtime/presentation: `renderer/effects/weaponActionSceneBridge.ts` and weapon runtime modules
- Projectile sequence, frame progression, hit/damage effects: `runtime-local/projectiles/*`, `renderer/effects/projectileFrameSceneBridge.ts`, `renderer/effects/combatProjectileEffectSceneBridge.ts`
- Player ability scene bridge and freeze-field bridge: `renderer/effects/playerAbilitySceneBridge.ts`, `runtime-local/skills/freezeFieldSceneBridge.ts`
- Scene VFX controller: `renderer/effects/sceneVfxController.ts`
- Player motion tweens / afterimages: `renderer/effects/playerMotionTweenController.ts`
- Recoil / knockback displacement: `runtime-local/geometry/heroDisplacementAdapter.ts`, `runtime-local/geometry/displacementResolver.ts`
- Authoritative frame application and local display motion: `renderer/authoritativeFrameSceneBridge.ts`, `renderer/authoritativeFrameSnapshotApplier.ts`, `renderer/localHeroDisplayPose.ts`
- Battle feedback bridge factories and shared-authoritative local feedback: `renderer/gameSceneFeedbackBridgeFactory.ts`, `renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`

## 5. Duplicate Logic Gate

No duplicated formatting / mapping / presentation helper remains in `GameScene`.

Checked removals:

- No display label dictionaries in `GameScene`
- No local projectile kind normalization in `GameScene`
- No skill binding mapping in `GameScene`
- No duplicated hero presentation scale conditional in `GameScene`
- No wheel switch notice text in `GameScene`
- No combat debug reporter or hot-path debug logging in `GameScene`

## 6. Debug / Legacy Residue Gate

Current status:

- `GameScene.ts` no longer imports, owns, or passes `CombatDebugReporter`.
- The audited file contains no hot-path debug reporter ownership.
- Any remaining debug module outside `GameScene` is not part of this report's allowed edit scope and is not on the audited scene hot path.

## 7. Gate Decision

`GameScene.ts` satisfies the GameScene hard-decoupling hard gate by current code end-state.

It no longer directly implements:

- arena/world builder details
- world view create/sync internals
- projectile progression/update chain
- hit/damage/kill/respawn chain
- pickup lifecycle runtime chain
- weapon runtime controller chain
- combat frame orchestration chain
- display dictionary / label helper logic
- runtime-local geometry / resolver helpers
- legacy/debug residue on the hot path

All remaining methods can be justified as scene lifecycle, top-level orchestration, camera host, physics glue, HUD bridge, tween/VFX/indicator glue, shared-authoritative renderer-host adapter glue, or minimal Phaser-local adapter glue.

Decision: **accepted for GameScene hard-decoupling completion**.

## 8. Remaining Technical Debt

- `GameScene.create()` is still a large scene composition root. This is acceptable for the hard gate because it wires extracted bridges, but a future composition-root helper could reduce scene wiring noise.
- `isLatestPlayerCommandMovementActive` contains a tiny movement-threshold predicate for authoritative display correction. It is currently justified as adapter glue, but could be extracted if the authoritative renderer adapter grows.
- Runtime behavior still needs manual play verification for battle feel, bot pressure, result flow, replay playback, and authoritative multiplayer behavior.
- Product areas outside this hard gate remain next-phase work and were not audited in this ticket.

## 9. Provisional Pieces

No provisional pieces remain for the `GameScene` hard-decoupling gate.

Manual playtesting remains required for product acceptance, but that is outside this audit/report-refresh ticket.
