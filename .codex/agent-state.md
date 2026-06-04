# Agent State

## Current Frontend Goal

Optimize battle frontend structure using the `BorrowManage` sample as the target shape: page entry files should compose feature panels, complex areas should split into local `components/hooks/objects/functions`, and battle contracts/objects should mirror backend microservice ownership.

Extracted `BorrowManage` sample target:

```text
BorrowManage/
  index.tsx
  components/
    BookSelectionPanel/
      index.tsx
      BookSearchToolbar.tsx
      components/
        BookCandidateList/
          components/
          index.tsx
      hooks/
      objects/
    BorrowDraftPanel/
      components/
      index.tsx
    BorrowRecordPanel/
      components/
        BorrowRecordTable/
          components/
          index.tsx
      functions/
      hooks/
      objects/
    ReaderPanel/
      components/
```

Recent frontend progress:

- `FRONTEND-BATTLE-TYPES-FACADE-CALLER-CLEAR-01`: removed all `frontend/src` imports from `objects/battle/types.ts`; callers now import battle value objects, API DTOs, snapshots, events, pickups, projectiles, weapons, and core vectors from their owning microservice object/API files.
- `FRONTEND-BATTLE-PAGE-FUNCTIONS-EXTRACT-01`: added `frontend/src/pages/battle/functions/` and moved battle page helper functions out of `useBattlePageRuntime.ts`.
- `FRONTEND-BATTLE-PAGE-OVERLAY-SPLIT-01`: moved BattlePage drawer, matching, settlement, and quick-access button assembly into page-level components/functions while keeping `index.tsx` as the visible page composition.
- `FRONTEND-BATTLE-MATCHING-OVERLAY-SPLIT-01`: split `MatchingOverlay` into local `components/` and `objects/` like the BorrowManage sample.
- `FRONTEND-BATTLE-PAGE-OBJECTS-SPLIT-01`: moved page state/view objects and matchmaking time formatting out of `hooks/battlePageTypes.ts` into `pages/battle/objects` and `pages/battle/functions`; deleted the unused hooks facade.
- `FRONTEND-BATTLE-HOOKS-PURITY-SPLIT-01`: moved pure battle page helpers from `hooks` into `functions`.
- `FRONTEND-BATTLE-HOOKS-BOUNDARY-CLEAR-01`: moved page localStorage/session store, authoritative input capture, and command history into `stores`, `input`, and `objects`; `pages/battle/hooks` now contains only `use*` React hooks.
- `FRONTEND-BATTLE-RUNTIME-NOTICE-HOOK-01`: extracted transient battle notice state, de-dupe, and timer cleanup from `useBattlePageRuntime.ts` into `useBattleTransientNotice`.
- `FRONTEND-BATTLE-GAME-SCREEN-COMPONENT-ALIGN-01`: moved single-file `BattleGameScreen` from `game-screen/` into page `components/`, removing an unnecessary directory level.
- `FRONTEND-BATTLE-AUTHORITATIVE-STATE-FUNCTIONS-01`: moved authoritative state duration/elapsed/recoverable/result-ready predicates into `pages/battle/functions`.
- `FRONTEND-BATTLE-AUTHORITATIVE-TIMERS-HOOK-01`: moved authoritative bridge timer refs and cleanup into `useBattleAuthoritativeBridgeTimers`.
- `FRONTEND-BATTLE-RUNTIME-IDENTITY-FUNCTIONS-01`: moved battle runtime identity and player-selection resolvers from the runtime hook into `pages/battle/functions`.
- `FRONTEND-BATTLE-MATCHING-FUNCTIONS-SPLIT-01`: moved `MatchingOverlay` queue/phase/room label helpers into its local `functions/` folder, matching the sample's nested page-region structure.
- `FRONTEND-BATTLE-URL-INTENT-HOOK-01`: moved battle page `new/resume` URL intent parsing into `useBattlePageUrlIntent`.
- `FRONTEND-BATTLE-RUNTIME-REFS-HOOK-01`: moved grouped runtime refs into `useBattlePageRuntimeRefs`, keeping the main runtime hook focused on orchestration.
- `FRONTEND-BATTLE-RUNTIME-CONFIG-OBJECTS-01`: moved runtime timing constants and the visitor-entry block message into `objects/BattlePageRuntimeConfig.ts`.
- `FRONTEND-BATTLE-VIEW-STATE-HOOK-01`: moved battle page React view state into `useBattlePageViewState`, leaving `useBattlePageRuntime` to orchestrate runtime flow.
- `FRONTEND-BATTLE-SESSION-OWNER-FUNCTION-01`: moved battle session owner resolution into `functions/resolveBattleSessionOwner`.
- `FRONTEND-BATTLE-SESSION-MERGE-FUNCTION-01`: moved completed-session replay merge rules out of `stores/activeBattleSessionStore.ts` into `functions/mergeCompletedActiveBattleSessions`.
- `FRONTEND-BATTLE-SESSION-OWNER-RULES-01`: moved active session owner normalization and legacy owner matching rules into `functions/activeBattleSessionOwnerRules`.
- `FRONTEND-BATTLE-SESSION-COMPLETION-RULES-01`: moved active session completion, elapsed advancement, and completed-session normalization rules into `functions/activeBattleSessionCompletionRules`, with `Date.now()` kept at store call sites.
- `FRONTEND-BATTLE-RUNTIME-REPLAY-FRAMES-01`: moved runtime replay-frame sampling and recovered replay-frame completion rules into `functions/battleRuntimeReplayFrames`.
- `FRONTEND-BATTLE-ACTIVE-SESSION-BUILDER-01`: moved `ActiveBattleSession` object construction into `functions/buildActiveBattleSession`, keeping the field shape centralized and explicit.
- `FRONTEND-BATTLE-MATCHMAKING-SCHEDULE-FUNCTIONS-01`: moved matchmaking countdown, minimum wait deadline, synced wait, and match-start delay rules into `functions/battleMatchmakingSchedule`.
- `FRONTEND-BATTLE-FINALIZATION-PLAN-FUNCTIONS-01`: moved local runtime finalization and completed-session recovery snapshot/replay planning into `functions/battleRuntimeFinalizationPlans`.
- `FRONTEND-BATTLE-AUTHORITATIVE-RESULT-LOADER-01`: moved authoritative result polling and backend result summary loading into `functions/loadAuthoritativeResultSummaryWhenReady`, with cancellation and cached state access injected by the hook.
- `FRONTEND-BATTLE-AUTHORITATIVE-RESTORE-IDENTITY-01`: moved shared authoritative session restore identity parsing and shared-session detection into `functions/authoritativeSessionRestore`.
- `FRONTEND-BATTLE-QUEUE-FLOW-FUNCTIONS-01`: moved matchmaking queue join input construction, retry eligibility, queue status usability, and shared-session polling stop checks into `functions/battleMatchmakingQueueFlow`.
- `FRONTEND-BATTLE-MATCHING-LAYER-FOLDER-01`: converted `BattleMatchingLayer` into a complex feature-region folder and moved its matching overlay, local components, local functions, local objects, and entry-blocked overlay under that folder.
- `FRONTEND-BATTLE-NON-GAME-BUCKET-RETIRE-01`: removed the ambiguous `non-game` bucket by moving settlement UI under `components/BattleSettlementLayer/components` and drawer presentation planning into page-level `functions/buildBattleDrawer`.
- `FRONTEND-BATTLE-AUTHORITATIVE-COMMAND-FLOW-01`: moved authoritative command uplink readiness, pending flush decision, backend command DTO construction, accepted command sequence advancement, and acknowledged command pruning into `functions/authoritativeBattleCommandFlow` with explicit ADT decisions.
- `FRONTEND-BATTLE-AUTHORITATIVE-STATE-BRIDGE-01`: moved authoritative state polling readiness, poll result application, polling timer startup, stream startup planning, and stream fallback decisions into `functions/authoritativeBattleStateBridge` with explicit ADT decisions.
- `FRONTEND-BATTLE-RUNTIME-LIFECYCLE-PLANS-01`: moved runtime persistence planning, runtime finalization branching, shared authoritative finalization start guards, and shared authoritative result handling into `functions/battleRuntimeLifecyclePlans` with explicit ADT decisions. The main runtime hook still owns side-effect execution and needs a follow-up execution-flow extraction to reduce file size.
- `FRONTEND-BATTLE-RUNTIME-LIFECYCLE-CONTROLLER-01`: moved runtime lifecycle side-effect execution for active-session persistence, replay-frame capture, runtime teardown, local/shared finalization, authoritative result settlement, and completed-session recovery into `functions/createBattleRuntimeLifecycleController`; `useBattlePageRuntime.ts` now constructs the controller and calls its focused methods.
- `FRONTEND-BATTLE-MATCHMAKING-QUEUE-CONTROLLER-01`: moved matchmaking queue join, join retry, queue status polling, room presence polling, failed-join cleanup, and idle queue leave into `functions/createBattleMatchmakingQueueController`; `useBattlePageRuntime.ts` now keeps startup scheduling while delegating queue side effects.
- `FRONTEND-BATTLE-STARTUP-SCHEDULER-CONTROLLER-01`: moved match start scheduling, latest queue refresh before startup, restored-session startup, authoritative bootstrap retry, and local/authoritative runtime startup branching into `functions/createBattleStartupSchedulerController`; `useBattlePageRuntime.ts` now delegates startup scheduling to the controller and dropped direct imports for queue status refresh and startup-only authoritative recovery helpers.
- `FRONTEND-BATTLE-COMPLETED-SESSION-RECOVERY-CONTROLLER-01`: moved completed-session recovery, shared authoritative recovery polling, recovered runtime restart, and failed-recovery session preservation into `functions/createBattleCompletedSessionRecoveryController`; `createBattleRuntimeLifecycleController` now delegates that recovery slice while retaining persistence and finalization.
- `FRONTEND-BATTLE-PROJECTIONS-REPLAY-OBJECTS-01`: added frontend `objects/battle/microservices/projections/objects/replay/{BattleReplayFrameState,BattleReplayHeroLifeState}.ts` to mirror the backend projections replay object slice; battle page/runtime code now imports replay frame state from that microservice path while `objects/replay/replayTypes.ts` keeps replay catalog compatibility aliases.
- `FRONTEND-BATTLE-PROJECTIONS-REPLAY-FUNCTIONS-01`: moved battle replay-frame capture, compaction, continuity, final-frame, and playback timeline helpers into `runtime/battle/microservices/projections/functions/BattleReplayFrameRecorder`; battle page/runtime code imports the microservice function directly and `objects/replay/replayRecorder.ts` remains a compatibility re-export for replay pages/APIs.
- `FRONTEND-BATTLE-RESULTS-API-CLIENT-01`: moved the frontend battle results API client into `apis/battle/microservices/results/api/BattleResultsApi.ts`, matching backend `microservices/results/api` ownership and existing results DTO paths; all callers now import the microservice client and `apis/battle/battleResultsApi.ts` remains a compatibility re-export.
- `FRONTEND-BATTLE-RESULTS-APIMESSAGE-CLIENT-01`: extracted shared battle APIMessage transport into `apis/battle/BattleApiMessageTransport.ts` and moved results APIMessage post helpers into `apis/battle/microservices/results/api/BattleResultApiMessageClient.ts`; results load/sync code now depends on the results microservice client while the flat battle APIMessage client keeps compatibility re-exports.
- `FRONTEND-BATTLE-QUEUE-APIMESSAGE-CLIENT-01`: moved queue join/status/leave and room snapshot/heartbeat APIMessage post helpers into `apis/battle/microservices/queue/api/BattleQueueApiMessageClient.ts`; matchmaking runtime code now imports the queue microservice client while the flat battle APIMessage client keeps compatibility re-exports.
- `FRONTEND-BATTLE-SESSION-APIMESSAGE-CLIENT-01`: moved session state-read and command APIMessage post helpers into `apis/battle/microservices/session/api/BattleSessionApiMessageClient.ts`; authoritative battle runtime now imports the session microservice client and `apis/battle/battleApiMessageClient.ts` is a compatibility-only aggregate export.
- `FRONTEND-BATTLE-FLAT-API-COMPAT-RETIRE-01`: deleted unused flat compatibility files `apis/battle/battleApiMessageClient.ts` and `apis/battle/battleResultsApi.ts` after verifying no `frontend/src` imports remained; `apis/battle` now keeps battle-level transport plus microservice API clients.
- `FRONTEND-BATTLE-LOADOUT-STORE-BOUNDARY-01`: moved the local loadout state/preset/skill binding store out of `apis/battle/loadoutGateway.ts` into `runtime/battle/loadout/BattleLoadoutStore.ts`; loadout page, home, battle page, and runtime skill input now import the local battle loadout boundary directly, leaving `apis/battle` for API transport and microservice clients only.
- `FRONTEND-BATTLE-RUNTIME-PERSISTENCE-CONTROLLER-01`: moved active-session construction, replay-frame sampling, and active/completed session persistence into `functions/createBattleRuntimePersistenceController.ts`; `createBattleRuntimeLifecycleController` now delegates persistence and keeps teardown/finalization/recovery flow control.
- `FRONTEND-BATTLE-RESTORED-STARTUP-CONTROLLER-01`: moved restored active-session startup, shared authoritative restore recovery, compatible local restored-session startup, and stale restored-session cleanup into `functions/createBattleRestoredSessionStartupController.ts`; `createBattleStartupSchedulerController` now focuses on scheduling, latest queue refresh, and local/shared runtime branch selection.
- `FRONTEND-BATTLE-AUTHORITATIVE-BRIDGE-CONTROLLER-01`: moved authoritative state stream/polling, command uplink, input capture, prepared-skill syncing, and authoritative command-history pruning out of `useBattlePageRuntime.ts` into `functions/createBattleAuthoritativeBridgeController.ts`; the hook now delegates session authoritative bridge side effects while preserving existing session API client paths and DTO field names.
- `FRONTEND-BATTLE-RUNTIME-LAUNCH-CONTROLLER-01`: moved battle runtime creation, initial authoritative state application, initial replay/persistence, runtime duration timers, snapshot sampling, and completion polling into `functions/createBattleRuntimeLaunchController.ts`; `useBattlePageRuntime.ts` now delegates Phaser runtime launch and timer setup.
- `FRONTEND-BATTLE-SESSION-BOOTSTRAP-AND-EXIT-01`: moved initial active/completed session selection and restore eligibility into `functions/initializeBattlePageSessionProgress.ts`, and moved `pagehide`/`beforeunload` runtime persistence registration into `functions/createBattlePageExitPersistenceController.ts`; `useBattlePageRuntime.ts` no longer reads completed/active session stores or installs page-exit listeners directly.
- `FRONTEND-BATTLE-MATCHMAKING-STARTUP-CONTROLLER-01`: moved matching phase initialization, countdown deadline/tick setup, queue state application, queue controller wiring, and startup scheduler wiring into `functions/createBattleMatchmakingStartupController.ts`; `useBattlePageRuntime.ts` no longer imports the queue controller, startup scheduler, matchmaking schedule helpers, or queue request ID helper directly.
- `FRONTEND-BATTLE-PAGE-COMMAND-CONTROLLER-01`: moved `selectBattleMode` and `startNewMatch` page command side effects into `functions/createBattlePageCommandController.ts`; `useBattlePageRuntime.ts` no longer directly publishes new active-session epochs, clears active session progress, resolves battle play mode, or resets authoritative prepared skill for those commands.
- `FRONTEND-BATTLE-EFFECT-SCOPE-SETUP-01`: moved `useEffect` runtime-scope reset and entry-blocked setup/cleanup into `functions/createBattlePageEffectScopeController.ts`; `useBattlePageRuntime.ts` no longer directly resets every runtime ref or directly installs the entry-blocked DOM cleanup branch.
- `FRONTEND-BATTLE-SESSION-AUTHORITATIVE-CLIENT-ALIGN-01`: moved the authoritative state/command client from `runtime/battle/authoritative/authoritativeBattleClient.ts` to `runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient.ts`; page/runtime callers now import the session microservice API client path while the DTO fields, APIMessage transport, and normalization behavior remain unchanged.
- `FRONTEND-BATTLE-SESSION-COMMAND-MAPPER-ALIGN-01`: moved local input-to-`BattlePlayerCommand` construction from `runtime/battle/authoritative/inputCommandMapper.ts` to `runtime/battle/microservices/session/functions/BattlePlayerCommandMapper.ts`, updated the Phaser command reader import, and deleted the unused `battleContractAdapter.ts` plus the now-empty authoritative bucket placeholder; `runtime/battle/authoritative` no longer owns session API/command logic.
- `FRONTEND-BATTLE-SESSION-CLIENT-NORMALIZERS-01`: split `runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient.ts` so the API client keeps exported session state/command types plus transport methods, while state/command payload normalization now lives in `runtime/battle/microservices/session/functions/BattleAuthoritativeSessionResponseNormalizers.ts`; DTO fields and normalization rules were preserved.
- `FRONTEND-BATTLE-SESSION-AUTHORITATIVE-FRAME-BUILDER-01`: moved pure session authoritative state to runtime frame construction from `runtime/battle/game/renderer/authoritativeBattleStateBridge.ts` to `runtime/battle/microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder.ts`; renderer files now consume the session microservice frame builder while Phaser scene application remains in renderer files.
- `FRONTEND-BATTLE-SESSION-FRAME-SNAPSHOT-SYNC-01`: moved authoritative frame collection/state synchronization from `runtime/battle/game/renderer/authoritativeFrameSnapshotApplier.ts` into `runtime/battle/microservices/session/functions/BattleRuntimeAuthoritativeSnapshotSync.ts`; the renderer applier now keeps local hero replay/correction orchestration while session microservice functions own frame-to-snapshot object field synchronization.
- `FRONTEND-BATTLE-SESSION-NORMALIZER-SPLIT-01`: split the session authoritative response normalizer into `BattleAuthoritativeSessionStateResponseNormalizer.ts`, `BattleAuthoritativeSessionCommandResponseNormalizer.ts`, and `BattleAuthoritativeSessionNormalizerPrimitives.ts`; the old response normalizer file is now a small compatibility re-export and the session API client imports the state/command/common normalizers directly.
- `FRONTEND-BATTLE-SESSION-STATE-ENTITY-NORMALIZER-01`: split session authoritative state normalization again so `BattleAuthoritativeSessionStateResponseNormalizer.ts` owns only root battle-state fields and composition, while `BattleAuthoritativeSessionStateEntityNormalizer.ts` owns player, pickup, projectile, slow-field, event, weapon, skill, and state enum field normalization.
- `FRONTEND-BATTLE-SESSION-STATE-DTO-NORMALIZERS-01`: split the remaining session state entity normalizer by backend DTO ownership: `BattleAuthoritativeSessionPlayerStateNormalizer.ts` owns player/weapon/skill response fields, `BattleAuthoritativeSessionProjectileStateNormalizer.ts` owns projectile/terminal response fields, and `BattleAuthoritativeSessionStateEntityNormalizer.ts` now owns pickup/slow-field/event/phase response fields.
- `FRONTEND-BATTLE-SESSION-LOCAL-REPLAY-CORE-01`: extracted the authoritative local hero replay projection algorithm from `runtime/battle/game/renderer/authoritativeLocalHeroReplay.ts` into `runtime/battle/microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection.ts`; the renderer file is now a small adapter that injects movement, freeze-field speed, blink/dash prediction, replay tuning constants, and diagnostics without making the session core depend on renderer/local modules.
- `FRONTEND-BATTLE-RUNTIME-PLAYER-COMMAND-TAP-01`: moved the shared-authoritative player command tap from `runtime/battle/game/renderer/createBattleRuntime.ts` into `runtime/battle/local/input/BattleAuthoritativePlayerCommandTap.ts`; runtime creation now only installs/destroys the tap, while local input owns DOM mouse/key listeners, skill binding reads, command cloning, and pending input coalescing.
- `FRONTEND-BATTLE-RENDERER-DIAGNOSTICS-FOLDER-01`: moved renderer diagnostics modules into `runtime/battle/game/renderer/diagnostics/`, including remote view, vision, local feedback, local hero correction, authoritative replay diagnostics, and the diagnostics gate; renderer/effects/entities callers now import from the diagnostics subfolder.
- `FRONTEND-BATTLE-LOCAL-SKILL-PREDICTION-TRACKER-01`: moved local authoritative blink/dash prediction helpers from renderer root into `runtime/battle/local/skills/`, and extracted scene-bridge pending blink/dash prediction plus cooldown tracking into `BattleAuthoritativeLocalSkillPredictionTracker`; `authoritativeFrameSceneBridge.ts` now delegates local skill prediction state while retaining frame application and correction orchestration.
- `FRONTEND-BATTLE-LOCAL-CORRECTION-RULES-01`: moved pure local authoritative hero correction thresholds, mode selection, hard-snap/deadzone/smooth decision rules, and finite-position checks into `runtime/battle/local/movement/BattleLocalAuthoritativeHeroCorrectionRules.ts`; the renderer correction controller now only reads/writes display pose and records diagnostics, and diagnostics imports the correction mode from the local movement rule boundary.
- `FRONTEND-BATTLE-SESSION-LOCAL-REPLAY-SNAPSHOT-PROJECTION-01`: moved local replay projection setup from `authoritativeFrameSnapshotApplier.ts` into `runtime/battle/microservices/session/functions/BattleRuntimeAuthoritativeLocalReplaySnapshotProjection.ts`; session functions now own authoritative skill cooldown merging and authoritative-only fallback projection while the renderer applier keeps snapshot mutation and correction callback orchestration.
- `FRONTEND-BATTLE-LOCAL-DISPLAY-MOTION-RULES-01`: moved authoritative local hero display-motion planning into `runtime/battle/local/movement/BattleAuthoritativeLocalHeroDisplayMotionRules.ts`; the renderer `authoritativeLocalHeroMotion.ts` now only reads the current display pose, writes the planned pose, and records local motion diagnostics.
- `FRONTEND-BATTLE-RENDERER-PRESENTATION-SPLIT-01`: split `gameScenePresentationBridge.ts` into `renderer/presentation/BattleGameSceneWorldViewPresentation.ts`, `BattleGameSceneHudPresentation.ts`, and `BattleGameSceneOcclusionPresentation.ts`; the renderer root file is now a compatibility aggregate export while world/HUD/occlusion presentation logic lives in a local feature folder like the `BorrowManage` sample.
- `FRONTEND-BATTLE-RENDERER-FEEDBACK-FACTORY-SPLIT-01`: split `gameSceneFeedbackBridgeFactory.ts` into `renderer/effects/factories/GameSceneBattleFeedbackBridgeFactory.ts` and `GameSceneSharedAuthoritativeLocalFeedbackBridgeFactory.ts`; the renderer root file is now a compatibility aggregate export while feedback bridge creation lives under the effects boundary.
- `FRONTEND-BATTLE-LOCAL-PREPARED-SKILL-INPUT-RULES-01`: moved shared-authoritative prepared-skill target validity from renderer effects into `runtime/battle/local/skills/BattleSharedAuthoritativeTargetValidity.ts`, moved invalid prepared-skill confirm suppression into `runtime/battle/local/input/BattleAuthoritativePreparedSkillInputRules.ts`, updated renderer input/presentation/feedback call sites, and removed the old renderer effects target-validity file.
- `FRONTEND-BATTLE-SESSION-HERO-SNAPSHOT-SYNC-01`: moved authoritative hero field synchronization and local hero replay projection orchestration from renderer `authoritativeFrameSnapshotApplier.ts` into `runtime/battle/microservices/session/functions/BattleRuntimeAuthoritativeHeroSnapshotSync.ts`; renderer snapshot application now keeps frame-level composition, extraction clones, event merging, and injection of the renderer/local replay resolver.
- `FRONTEND-BATTLE-SESSION-RUNTIME-STARTUP-RULES-01`: moved pure shared-authoritative runtime startup rules from renderer `createBattleRuntime.ts` into `runtime/battle/microservices/session/functions/BattleRuntimeAuthoritativeStartupRules.ts`, including local player id normalization, initial local hero resolution, and last acknowledged command sequence resolution.
- `FRONTEND-BATTLE-RENDERER-RUNTIME-LIFECYCLE-SPLIT-01`: moved Phaser game construction into `renderer/runtime/BattlePhaserGameFactory.ts` and mount/hud cleanup, context-menu locking, and thumbnail capture into `renderer/runtime/BattleRuntimeDomLifecycle.ts`; `createBattleRuntime.ts` now composes session state, scene, input tap, and runtime handle without owning low-level Phaser config or DOM cleanup details.
- `FRONTEND-BATTLE-RENDERER-BOOT-SNAPSHOT-FACTORY-01`: moved boot snapshot construction from renderer `createBattleRuntime.ts` into `renderer/runtime/BattleRuntimeBootSnapshotFactory.ts`; the factory composes local initial snapshots, session startup rules, authoritative frame building, and the renderer snapshot applier while the root runtime creator only requests a boot snapshot.
- `FRONTEND-BATTLE-RENDERER-AUTHORITATIVE-FRAME-FOLDER-01`: moved `AuthoritativeFrameSceneBridge` implementation into `renderer/authoritative/BattleAuthoritativeFrameSceneBridge.ts`; the old renderer root file is now a compatibility re-export, while frame application orchestration is grouped under the authoritative renderer sub-boundary.
- `FRONTEND-BATTLE-RENDERER-AUTHORITATIVE-CORRECTION-FOLDER-01`: moved `LocalAuthoritativeHeroCorrectionController` implementation into `renderer/authoritative/BattleLocalAuthoritativeHeroCorrectionController.ts`; the renderer root correction file is now a compatibility re-export, while correction orchestration sits beside the authoritative frame bridge.
- `FRONTEND-BATTLE-RENDERER-AUTHORITATIVE-REPLAY-FOLDER-01`: moved renderer authoritative local hero replay target resolution into `renderer/authoritative/BattleAuthoritativeLocalHeroReplay.ts`; the renderer root replay file is now a compatibility re-export while session replay core and local skill/movement dependencies remain unchanged.
- `FRONTEND-BATTLE-RENDERER-AUTHORITATIVE-MOTION-PIPELINE-FOLDER-01`: moved the authoritative render pipeline frame builder and local hero display-motion adapter into `renderer/authoritative/BattleAuthoritativeRenderPipeline.ts` and `BattleAuthoritativeLocalHeroMotion.ts`; the renderer root files are compatibility re-exports and `BattleAuthoritativeFrameSceneBridge` now imports the local authoritative implementations directly.
- `FRONTEND-BATTLE-RENDERER-INPUT-BRIDGE-FOLDER-01`: moved game scene input bridge implementation into `renderer/input/BattleGameSceneInputBridge.ts`; the root `gameSceneInputBridge.ts` is now a compatibility re-export while input capture remains aligned with local input and prepared-skill input rule boundaries.
- `FRONTEND-BATTLE-RENDERER-SCENE-BRIDGE-FOLDERS-01`: moved game scene camera bridge implementation into `renderer/camera/BattleGameSceneCameraBridge.ts`, and moved player actor plus hero displacement bridge implementations into `renderer/entities/BattleGameSceneHeroActorBridge.ts` and `BattleGameSceneHeroDisplacementBridge.ts`; the root bridge files are now compatibility re-exports.
- `FRONTEND-BATTLE-RENDERER-ASSETS-FOLDER-01`: moved Phaser renderer asset preloading and weapon/projectile raster atlas references into `renderer/assets/BattleAssetPreloader.ts`, `BattleWeaponRasterAtlas.ts`, and `BattleProjectileRasterAtlas.ts`; root asset/atlas files now provide compatibility re-exports while texture keys, atlas paths, and frame names are unchanged.
- `FRONTEND-BATTLE-RENDERER-LOCAL-HERO-DISPLAY-ENTITY-01`: moved `LocalHeroDisplay` and local hero display pose store types into `renderer/entities/BattleLocalHeroDisplay.ts`; root `localHeroDisplayPose.ts` is now a compatibility re-export while local hero display data remains typed against backend actor/session objects.
- `FRONTEND-BATTLE-RENDERER-RUNTIME-AUTHORITATIVE-ROOT-COMPAT-01`: moved `createBattleRuntime` implementation into `renderer/runtime/BattleRuntimeFactory.ts` and moved authoritative frame-to-snapshot application into `renderer/authoritative/BattleAuthoritativeFrameSnapshotApplier.ts`; the root `createBattleRuntime.ts` and `authoritativeFrameSnapshotApplier.ts` files now provide compatibility re-exports.
- `FRONTEND-BATTLE-GAMESCENE-DIRECT-SUBFOLDER-IMPORTS-01`: updated `GameScene.ts` to import directly from renderer subfolders for assets, authoritative frame bridge, local hero display, presentation, entity actor/displacement, camera, input, and feedback factories; deleted the now-unused internal renderer root facades for those bridges.
- `FRONTEND-BATTLE-REMAINING-INTERNAL-FACADE-RETIRE-01`: updated remaining renderer-internal imports to target concrete subfolder modules for authoritative replay types, local hero display, and weapon/projectile atlas references; deleted the remaining unused internal root facades, leaving only the public `createBattleRuntime.ts` entry in renderer root.
- `FRONTEND-BATTLE-GAMESCENE-RUNTIME-BRIDGES-SPLIT-01`: moved `GameScene.create()` runtime bridge construction into `game/scenes/functions/createGameSceneRuntimeBridges.ts` with the returned bridge set typed by `game/scenes/objects/GameSceneRuntimeBridgeSet.ts`; `GameScene.ts` now owns scene lifecycle, input/HUD/render loop calls, and delegates bridge wiring to the scene-level factory.
- `FRONTEND-BATTLE-GAMESCENE-INPUT-LIFECYCLE-SPLIT-01`: moved `GameScene` pointer/wheel/window input listener setup and shutdown cleanup into `game/scenes/functions/installGameSceneInputLifecycle.ts`, with callback shape typed by `game/scenes/objects/GameSceneInputLifecycleHandlers.ts`.
- `FRONTEND-STRUCTURE-AUDIT-SAMPLE-ALIGN-01`: updated `scripts/audit-frontend-domain-structure.mjs` so `npm run audit:frontend-structure` validates the current sample-style frontend shape: `apis` instead of old `api`, page-local `components/hooks/functions/objects`, battle nested matching/settlement layers, scene-level `functions/objects`, and frontend battle object microservice directories aligned with backend `services/battle/microservices`.
- `FRONTEND-DOMAIN-ALIGNMENT-AUDIT-CURRENT-ARCH-01`: updated `scripts/audit-frontend-backend-domain-alignment.mjs` from the obsolete `slaydemo/backend` plus `frontend/src/domains` layout to the current backend `services/*` and frontend `apis/*`/`objects/*` architecture; it now verifies service names across backend/frontend and checks battle object microservices against backend battle microservice directories.
- `FRONTEND-BATTLE-FLAT-CONTRACT-ARTIFACT-RETIRE-01`: deleted unused flat battle compatibility files `objects/battle/types.ts` and `objects/battle/contracts/*`; tightened the frontend structure audit so those flat contract/type paths are forbidden and battle callers must use microservice-owned API/object modules.
- `FRONTEND-BATTLE-CORE-RULES-PATH-ALIGN-01`: moved active frontend battle core constants from flat `objects/battle/battleRules.ts` to backend-aligned `objects/battle/objects/core/BattleCoreRules.ts`, updated page/runtime callers, removed the empty legacy `runtime/battle/authoritative` directory, and tightened the structure audit to forbid both old paths.
- `FRONTEND-BATTLE-MODE-LABEL-CORE-PATH-ALIGN-01`: moved battle mode display label constants/resolver from root `runtime/battle/battleModeDisplayLabels.ts` to `objects/battle/objects/core/BattleModeDisplayLabels.ts`, changed the resolver to depend on core `BattleModeId` rather than a queue API DTO alias, updated world/matchmaking callers, and added an audit guard for the old runtime-root file.
- `FRONTEND-BATTLE-GAME-PROJECTILE-BIRTH-FUNCTION-01`: moved the pure projectile birth position helper from root `runtime/battle/game/projectileBirth.ts` into `runtime/battle/game/functions/BattleProjectileBirthPosition.ts`, updated local/renderer callers, and tightened the frontend structure audit to forbid the old root file.
- `FRONTEND-BATTLE-GAME-SPAWN-FUNCTIONS-01`: moved initial hero/pickup spawn and hero visual resolution helpers from root `runtime/battle/game/spawn.ts` into `runtime/battle/game/functions/BattleSpawnFactory.ts`, updated local session, renderer entity, and replay viewer callers, and tightened the structure audit to forbid the old root file.
- `FRONTEND-BATTLE-GAME-CONSTANTS-OBJECTS-01`: moved shared game constants from root `runtime/battle/game/constants.ts` into `runtime/battle/game/objects/BattleGameConstants.ts`, updated game/local/bots/replay callers, and tightened the frontend structure audit so the old root constants file is forbidden.
- `FRONTEND-BATTLE-COMBAT-WEAPON-RULES-ALIGN-01`: moved frontend weapon rule definitions into `objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions.ts`, moved local weapon inventory helpers into `runtime/battle/microservices/combat/functions/BattleWeaponInventoryRules.ts`, updated local/game/bot callers, and deleted the old `runtime/battle/game/weapons` bucket.
- `FRONTEND-BATTLE-ABILITIES-SKILL-RULES-ALIGN-01`: moved frontend skill rule definitions into `objects/battle/microservices/abilities/objects/abilities/BattleAbilityRuleDefinitions.ts`, moved default skill state and skill lookup helpers into `runtime/battle/microservices/abilities/functions/BattleSkillStateRules.ts`, updated local/game renderer callers, deleted the old `runtime/battle/game/skills` bucket, and modernized `audit:battle-content` to read current frontend microservice object files plus backend dynamic rule defaults.
- `FRONTEND-BATTLE-WORLD-CONTENT-CATALOG-ALIGN-01`: split the old mixed `runtime/battle/game/assets/battleContentCatalog.ts` bucket so world-derived hero/pickup layout exports live in `runtime/battle/microservices/world/functions/BattleWorldInitialLayout.ts` and hero visual skin tables live in `runtime/battle/game/objects/BattleHeroVisualCatalog.ts`; deleted unused `runtime/battle/game/assets` and `runtime/battle/game/maps` buckets and tightened the structure audit to forbid them.
- `FRONTEND-BATTLE-ABILITIES-SLOW-FIELD-RULES-ALIGN-01`: moved pure slow-field runtime helpers from `runtime/battle/local/skills/freezeFieldController.ts` into `runtime/battle/microservices/abilities/functions/BattleSlowFieldRuntimeRules.ts`, updated bot/local/renderer callers, and tightened the structure audit to forbid the old local controller file.
- `FRONTEND-BATTLE-ABILITIES-PICKUP-RULES-ALIGN-01`: moved automatic pickup equip/refill/heal/consume rules from `runtime/battle/local/pickups/pickupController.ts` into `runtime/battle/microservices/abilities/functions/BattlePickupRules.ts`, kept scene presentation in `automaticPickupSceneHandler.ts`, updated bot callers to use the abilities rule path, and tightened the structure audit to forbid the old local controller file.
- `FRONTEND-BATTLE-ABILITIES-PICKUP-LIFECYCLE-RULES-ALIGN-01`: moved pickup respawn lifecycle advancement and nearby pickup search into `runtime/battle/microservices/abilities/functions/BattlePickupRules.ts`, updated HUD to query abilities rules directly, and kept `local/pickups/pickupLifecycle.ts` as a spawn-placement adapter for scene obstacle/occludable context.
- `FRONTEND-BATTLE-WORLD-PICKUP-SPAWN-RULES-ALIGN-01`: moved pickup spawn-point selection and geometry availability rules from `runtime/battle/local/pickups/pickupSpawnResolver.ts` into `runtime/battle/microservices/world/functions/BattlePickupSpawnPointRules.ts`, deleted the local resolver, and made randomness an explicit local adapter input instead of a world-rule default.
- `FRONTEND-BATTLE-COMBAT-CURRENT-WEAPON-RULE-ALIGN-01`: moved current weapon lookup from `runtime/battle/local/weapons/weaponActionController.ts` into `runtime/battle/microservices/combat/functions/BattleWeaponInventoryRules.ts`, updated HUD, bot, and weapon scene bridge callers, and tightened the structure audit so the local action controller cannot re-export that inventory rule.
- `FRONTEND-BATTLE-COMBAT-WEAPON-SWITCH-RULES-ALIGN-01`: moved weapon switch request, index-switch request, switch transaction planning, reload cancellation, and depleted disposable weapon pruning from `runtime/battle/local/weapons/weaponController.ts` into `runtime/battle/microservices/combat/functions/BattleWeaponSwitchRules.ts`; `WeaponSwitchStateBridge` now only owns local pending-switch timer state.
- `FRONTEND-BATTLE-COMBAT-WEAPON-FIRE-DECISION-RULES-ALIGN-01`: moved weapon reload eligibility, ammo-mode resolution, trigger/fire gating, magazine ammo consumption, heat accumulation, overheat state, cooldown mutation, and fire block result types from `runtime/battle/local/weapons/weaponController.ts` into `runtime/battle/microservices/combat/functions/BattleWeaponFireDecisionRules.ts`; deleted the local weapon controller while keeping projectile creation, muzzle VFX, recoil presentation, and floating text in `weaponActionController.ts`.
- `FRONTEND-BATTLE-COMBAT-WEAPON-TIMER-RULES-ALIGN-01`: moved weapon cooldown/reload/heat timer advancement and reload completion from `runtime/battle/local/timers/heroWeaponSkillTimers.ts` into `runtime/battle/microservices/combat/functions/BattleWeaponTimerRules.ts`; the local hero timer adapter now only loops heroes/weapons and handles skills, pickup notices, stamina, and switch timer state.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-COUNT-FACTORY-ALIGN-01`: renamed the frontend combat weapon rule field from `pellets` to backend-aligned `projectileCount`, moved projectile angle/count/spread planning into `runtime/battle/microservices/combat/functions/BattleProjectileFactoryRules.ts`, and removed projectile spawn-plan data from local weapon runtime profiles.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-STATE-FACTORY-ALIGN-01`: moved projectile birth position and projectile state construction into `runtime/battle/microservices/combat/functions/BattleProjectileFactoryRules.ts`; deleted the old game projectile-birth helper and local projectile factory while renderer/local callers import combat rules directly.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-TARGETING-RULES-ALIGN-01`: moved projectile hit attempt validation, path hit timing, hero path targeting, rocket splash target selection, and shooter advantage radius from `runtime/battle/local/projectiles/hitResolver.ts` into `runtime/battle/microservices/combat/functions/BattleProjectileTargetingRules.ts`; deleted the local hit resolver.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-RUNTIME-RULES-ALIGN-01`: moved projectile motion advancement, lifetime/world/obstacle/hero terminal route planning, rocket trail timing, and rocket hero impact position resolution from `runtime/battle/local/projectiles/projectileController.ts` into `runtime/battle/microservices/combat/functions/BattleProjectileRuntimeRules.ts`; deleted the local projectile controller.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-IMPACT-RULES-ALIGN-01`: moved projectile damage application, no-damage reasons, kill scoring, death-state mutation, and kill event planning from `runtime/battle/local/projectiles/damageResolver.ts` into `runtime/battle/microservices/combat/functions/BattleProjectileImpactRules.ts`; deleted the local damage resolver.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-EFFECT-PLAN-ALIGN-01`: moved projectile frame impact/explosion/hit effect planning and `CombatProjectileEffect` DTO ownership from `runtime/battle/local/combat/combatFrameController.ts` into `runtime/battle/microservices/combat/functions/BattleProjectileImpactRules.ts`; local combat frame controller is now only the respawn scene adapter stub.
- `FRONTEND-BATTLE-LOCAL-RESPAWN-STUB-RETIRE-01`: deleted the empty local respawn adapter path (`runtime/battle/local/combat/combatFrameController.ts` and `runtime/battle/local/session/respawnSceneBridge.ts`) and removed it from GameScene bridge wiring because it always returned no effects.
- `FRONTEND-BATTLE-RUNTIME-FINISH-RULES-ALIGN-01`: moved battle completion, alive hero count, and local-player eliminated predicates from `runtime/battle/local/session/battleCompletion.ts` into `runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules.ts`; callers now import the runtime microservice rule path.
- `FRONTEND-BATTLE-PROJECTIONS-FINALIZATION-REPLAY-RULES-ALIGN-01`: moved bot-only closure snapshot planning and final replay-frame completion from `runtime/battle/local/session/{botOnlyBattleClosure,battleFinalizationReplay}.ts` into `runtime/battle/microservices/projections/functions/{BattleBotOnlyClosureReplayRules,BattleFinalizationReplayRules}.ts`; callers now import the projections microservice rule path and the structure audit forbids the old local session files.
- `FRONTEND-BATTLE-SESSION-INITIAL-SNAPSHOT-RULES-ALIGN-01`: moved initial participant seat/config objects into `objects/battle/microservices/session/objects/state/BattleInitialParticipants.ts` and moved seat sorting, hero config planning, participant display-name application, player hero resolution, and snapshot state assembly into `runtime/battle/microservices/session/functions/BattleInitialSnapshotRules.ts`; `local/session/initialBattleSnapshot.ts` is now a local adapter for active map lookup, extraction initialization, game spawn factory calls, and bot registry display-name lookup.
- `FRONTEND-BATTLE-LOCAL-FRAME-BRIDGE-RULES-ALIGN-01`: moved the local frame bridge's skill-input primary-fire suppression rule into `runtime/battle/microservices/actors/functions/BattlePlayerInputRules.ts`, matching the backend actors input-rule ownership; `local/session/localBattleFrameSceneBridge.ts` now delegates command arbitration while keeping scene bridge orchestration local.
- `FRONTEND-BATTLE-ACTORS-MOVEMENT-RULES-ALIGN-01`: moved pure movement velocity/stamina/last-direction advancement from `runtime/battle/local/movement/movementController.ts` into `runtime/battle/microservices/actors/functions/BattlePlayerMovementRules.ts`; local player frame orchestration and bot controller callers now import the actors microservice rule, and the structure audit forbids the old local movement controller.
- `FRONTEND-BATTLE-TIMER-RULES-OWNERSHIP-ALIGN-01`: moved skill cooldown/active-duration advancement from `runtime/battle/local/timers/heroWeaponSkillTimers.ts` into `runtime/battle/microservices/abilities/functions/BattleSkillStateRules.ts` as `advanceBattleSkillTimer`; the local timer loop now applies the abilities rule and no longer writes skill timer fields directly.
- `FRONTEND-BATTLE-ACTORS-RUNTIME-TIMER-RULES-ALIGN-01`: moved jump cooldown advancement, dead hero runtime clearance, and non-local hero stamina recovery from `runtime/battle/local/timers/heroWeaponSkillTimers.ts` into `runtime/battle/microservices/actors/functions/BattlePlayerRuntimeRules.ts`; the local timer loop now delegates actor runtime rules while keeping pickup notice cooldowns and weapon switch bridge state local.
- `FRONTEND-BATTLE-COMBAT-WEAPON-SWITCH-TIMER-RULES-ALIGN-01`: moved weapon-switch timer state transition from `runtime/battle/local/timers/heroWeaponSkillTimers.ts` into `runtime/battle/microservices/combat/functions/BattleWeaponSwitchRules.ts` as `advanceWeaponSwitchTimerState`; the local timer adapter now only applies the completed weapon index and returns bridge state.
- `FRONTEND-BATTLE-ACTORS-RESPAWN-RULES-ALIGN-01`: moved respawn timer advancement and hero respawn state construction from `runtime/battle/local/session/respawnController.ts` into `runtime/battle/microservices/actors/functions/BattlePlayerRespawnRules.ts`; the new actors rule returns explicit respawn results/new hero state instead of mutating local session state, and the structure audit forbids the old local session file.
- `FRONTEND-BATTLE-ABILITY-MOTION-RULES-ALIGN-01`: moved skill readiness, activation state updates, skill-state replacement, and prepared-skill toggle rules from `runtime/battle/local/movement/playerMotionAbilityHandler.ts` into `runtime/battle/microservices/abilities/functions/BattleSkillStateRules.ts`; the local handler keeps target geometry, VFX, and local motion orchestration.
- `FRONTEND-BATTLE-WORLD-MOTION-RULES-ALIGN-01`: moved world occupancy, obstacle collision, motion-target validation, vector normalization, and stepped motion destination rules from `runtime/battle/local/movement/motionController.ts` into `runtime/battle/microservices/world/functions/{BattleArenaCollision,BattleMotionRules}.ts`; local movement, authoritative prediction/replay, and bot steering now import the world microservice rules, and the structure audit forbids the old local motion rule file.
- `FRONTEND-BATTLE-COMBAT-DISPLACEMENT-RULES-ALIGN-01`: moved recoil/knockback destination planning from `runtime/battle/local/geometry/displacementResolver.ts` into `runtime/battle/microservices/combat/functions/BattleCombatDisplacementRules.ts`; the local hero displacement adapter now only supplies hero/world inputs and applies the resolved position, and the structure audit forbids the old local resolver.
- `FRONTEND-BATTLE-COMBAT-WEAPON-ACTION-RULES-ALIGN-01`: moved weapon fire/reload action planning and weapon runtime profiles from `runtime/battle/local/weapons/{weaponActionController,weaponRuntimeProfiles}.ts` into `runtime/battle/microservices/combat/functions/{BattleWeaponActionRules,BattleWeaponRuntimeProfiles}.ts`; renderer and bot callers now import the combat microservice action rule, and the structure audit forbids the old local action/profile files.
- `FRONTEND-BATTLE-ABILITY-PREDICTION-RULES-ALIGN-01`: moved skill runtime profiles, prepared-target command/profile helpers, shared authoritative target validity, and Blink/Dash authoritative-local prediction rules from `runtime/battle/local/skills` into `runtime/battle/microservices/abilities/functions/{BattleSkillRuntimeProfiles,BattleSkillTargetValidityRules,BattleAuthoritativeSkillPredictionRules}.ts`; local skill code now keeps only the stateful prediction tracker, and the structure audit forbids the old local rule files.
- `FRONTEND-BATTLE-ABILITY-PREDICTION-TRACKER-RULES-ALIGN-01`: moved pure pending-prediction creation, expiration, mismatch-distance pruning, and local cooldown reconciliation from `runtime/battle/local/skills/BattleAuthoritativeLocalSkillPredictionTracker.ts` into `runtime/battle/microservices/abilities/functions/BattleAuthoritativeSkillPredictionTrackerRules.ts`; the local tracker now keeps renderer-owned mutable prediction state and delegates deterministic decisions.
- `FRONTEND-BATTLE-SHARED-AUTHORITATIVE-FEEDBACK-RULES-ALIGN-01`: moved shared-authoritative local weapon feedback planning into `runtime/battle/microservices/combat/functions/BattleWeaponFeedbackRules.ts` and skill feedback planning into `runtime/battle/microservices/abilities/functions/BattleSkillFeedbackRules.ts`; the renderer bridge now only consumes typed plans and executes VFX/diagnostics callbacks.
- `FRONTEND-BATTLE-COMBAT-PROJECTILE-FEEDBACK-RULES-ALIGN-01`: moved projectile terminal/birth feedback state, tracer option planning, authoritative terminal queue decisions, terminal VFX strategy, and remote birth position planning from `runtime/battle/game/renderer/effects/projectileTerminalFeedbackPolicy.ts` into `runtime/battle/microservices/combat/functions/BattleProjectileFeedbackRules.ts`; renderer effect modules now import the combat rule file directly, and the structure audit forbids the old renderer policy path.
- `FRONTEND-BATTLE-HERO-PICKUP-FEEDBACK-RULES-ALIGN-01`: moved hero feedback state/delta planning into `runtime/battle/microservices/actors/functions/BattleHeroFeedbackRules.ts` and pickup consumed feedback state/planning into `runtime/battle/microservices/abilities/functions/BattlePickupFeedbackRules.ts`; `heroAndPickupFeedbackPresenter.ts` now only executes typed feedback plans through renderer callbacks.
- `FRONTEND-BATTLE-PROJECTILE-FEEDBACK-QUEUE-RULES-ALIGN-01`: moved deterministic projectile feedback queue and lifecycle rules into `runtime/battle/microservices/combat/functions/BattleProjectileFeedbackQueueRules.ts`; `battleFeedbackSceneBridge.ts` still owns mutable renderer caches and VFX execution, while bounded key memory, ready-terminal selection, freshness baseline resolution, live-projectile id collection, and local terminal tracer eligibility are combat rules.
- `FRONTEND-BATTLE-PROJECTILE-PRESENTATION-PLAN-RULES-ALIGN-01`: moved projectile terminal and remote-birth VFX presentation planning into `runtime/battle/microservices/combat/functions/BattleProjectileFeedbackPresentationRules.ts`; renderer presenters now execute typed effect plans and keep diagnostics/VFX callback side effects.
- `FRONTEND-BATTLE-PROJECTILE-DIAGNOSTIC-PLAN-RULES-ALIGN-01`: moved projectile terminal diagnostic payload planning into `runtime/battle/microservices/combat/functions/BattleProjectileFeedbackDiagnosticRules.ts`; renderer diagnostics now only gates diagnostics, snapshots display positions, and publishes the combat-planned diagnostic record.
- `FRONTEND-BATTLE-REMOTE-VIEW-DIAGNOSTICS-SPLIT-01`: split remote view diagnostics into local renderer diagnostics `objects/RemoteViewDiagnosticsObjects.ts` and `functions/RemoteViewDiagnosticsRules.ts`; `remoteViewDiagnostics.ts` now keeps diagnostics gates, sample-window mutation, time reads, and global diagnostics publishing.
- `FRONTEND-BATTLE-LOCAL-FEEDBACK-DIAGNOSTICS-SPLIT-01`: split local feedback diagnostics into local renderer diagnostics `objects/LocalFeedbackDiagnosticsObjects.ts` and `functions/LocalFeedbackDiagnosticsRules.ts`; `localFeedbackDiagnostics.ts` now keeps diagnostics gates, counters, sample-window mutation, time reads, and global diagnostics publishing.
- `FRONTEND-BATTLE-LOCAL-HERO-CORRECTION-DIAGNOSTICS-SPLIT-01`: split local hero correction diagnostics into local renderer diagnostics `objects/LocalHeroCorrectionDiagnosticsObjects.ts` and `functions/LocalHeroCorrectionDiagnosticsRules.ts`; `localHeroCorrectionDiagnostics.ts` now keeps diagnostics gates, counters, sample-window mutation, time reads, and global diagnostics publishing.
- `FRONTEND-BATTLE-VISION-DIAGNOSTICS-SPLIT-01`: split battle vision diagnostics into local renderer diagnostics `objects/VisionDiagnosticsObjects.ts` and `functions/VisionDiagnosticsRules.ts`; `visionDiagnostics.ts` now keeps diagnostics gates, Phaser camera reads, DOM viewport reads, time reads, and global diagnostics publishing.
- Verification: `npm run build` passed; `rg "battlePageTypes|objects/battle/types|game-screen|non-game|battleDrawerPresenter" frontend/src -n` returned no matches; `rg "apis/battle/loadoutGateway|loadoutGateway|battleApiMessageClient|battleResultsApi" frontend/src -n` returned no matches; focused snapshot-applier old local-replay helper scan returned no matches other than expected skill-state sync calls; `rg "BattleAuthoritativeSessionResponseNormalizers" frontend/src -g "*.ts"` returned no imports; focused state-normalizer scans confirmed root delegates DTO-specific normalizers; focused replay-core dependency scan found no concrete renderer/local imports; focused create-runtime old-helper scan returned no matches; focused diagnostics old-root import scan returned no matches; focused scene-bridge prediction-state scan returned no matches; focused correction-controller pure-rule scan showed rules live under local movement; focused display-motion old-rule scan showed movement constants, skill prediction, freeze speed, collision, and vector normalization live under local movement; focused presentation scan shows `GameScene` imports directly from `renderer/presentation`; focused feedback factory scan shows `GameScene` imports directly from `renderer/effects/factories`; focused prepared-skill input scan shows no old renderer target-validity imports and no input rule import from `authoritativeFrameSceneBridge`; focused hero snapshot sync scan shows hero authoritative field updates live under session functions and the renderer applier delegates to `syncBattleRuntimeAuthoritativeHeroes`; focused startup-rule scan shows old renderer-local helper names are gone and createBattleRuntime imports the session startup rules; focused runtime lifecycle scan shows Phaser config and DOM cleanup now live under `renderer/runtime`; focused boot snapshot scan shows initial snapshot creation and initial authoritative frame application live under `renderer/runtime/BattleRuntimeBootSnapshotFactory.ts`; focused authoritative folder scan shows frame bridge, correction controller, replay resolver, render pipeline, display-motion adapter, and frame snapshot applier implementations live under `renderer/authoritative`; focused remaining-internal-facade scan shows no imports from retired root files and renderer root contains only `createBattleRuntime.ts`; focused `git diff --check` passed with LF/CRLF warnings only.
- Latest verification after `FRONTEND-BATTLE-GAME-CONSTANTS-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run build`, `npm run audit:frontend-structure`, and `npm run audit:domain-alignment` passed. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, and `npm run build` passed. Focused `frontend/src` plus `scripts` scan for `game/weapons` returns only the audit guard. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITIES-SKILL-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused `frontend/src` plus `scripts` scan for `game/skills` returns only the audit guard. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-WORLD-CONTENT-CATALOG-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused `frontend/src` plus `scripts` scan for old `battleContentCatalog`, `battleMapCatalog`, `game/assets`, and `game/maps` returns only audit guards. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITIES-SLOW-FIELD-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused `frontend/src` plus `scripts` scan for `freezeFieldController` returns only the audit guard. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITIES-PICKUP-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused `frontend/src` plus `scripts` scan for `pickupController` returns only the audit guard. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITIES-PICKUP-LIFECYCLE-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `findNearbyPickup` is gone, HUD imports nearby pickup lookup from `BattlePickupRules`, and `local/pickups/pickupLifecycle.ts` no longer contains nearby-search or respawn mutation rules. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-WORLD-PICKUP-SPAWN-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old `pickupSpawnResolver` path only in the audit guard, `BattlePickupSpawnPointRules` is required by the structure audit, and the world rule file has no implicit `Math.random`. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-CURRENT-WEAPON-RULE-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `getCurrentWeapon` imports now come from `BattleWeaponInventoryRules`; the old local export only remains as an audit guard. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-SWITCH-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows weapon switch rule names only in `BattleWeaponSwitchRules.ts`, the local switch state bridge, and audit guards; `weaponController.ts` is reduced to reload/fire decisions. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-FIRE-DECISION-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `requestWeaponReload`, `resolveWeaponFire`, and `resolveWeaponAmmoMode` live in `BattleWeaponFireDecisionRules.ts`; local `weaponActionController.ts` imports them from combat and old `weaponController.ts` is deleted/forbidden. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-TIMER-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows weapon cooldown/reload/heat timer fields live in `BattleWeaponTimerRules.ts` plus audit guards, while `heroWeaponSkillTimers.ts` imports `advanceWeaponTimers` from combat. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-COUNT-FACTORY-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows no frontend `pellets`, `projectileSpawnPlan`, random spread helper, or local projectile spawn-plan type outside the new audit guards; projectile count now uses the backend field name `projectileCount`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-STATE-FACTORY-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows no imports from old `BattleProjectileBirthPosition` or local `projectileFactory`; projectile birth position, forward clearance, angle plan, and projectile spawn construction now live in `BattleProjectileFactoryRules.ts`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-TARGETING-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old `hitResolver` path only in the audit guard; targeting callers now import `BattleProjectileTargetingRules.ts`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-RUNTIME-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old `projectileController` path only in the audit guard; projectile runtime route planning callers now import `BattleProjectileRuntimeRules.ts`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-IMPACT-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old `damageResolver` path only in the audit guard; projectile impact callers now import `BattleProjectileImpactRules.ts`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-EFFECT-PLAN-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows projectile effect planning names live in `BattleProjectileImpactRules.ts` plus renderer presentation callers; local `combatFrameController.ts` no longer owns projectile effect planning. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-LOCAL-RESPAWN-STUB-RETIRE-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows respawn stub/bridge names and old local combat frame path only in audit guards; GameScene bridge wiring no longer constructs or stores the empty respawn bridge. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-RUNTIME-FINISH-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `battleCompletion` only in the audit guard; completion callers import `BattleRuntimeFinishRules.ts`. `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-PROJECTIONS-FINALIZATION-REPLAY-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `botOnlyBattleClosure` and `battleFinalizationReplay` paths only in the audit guards; finalization callers import projections rule files. Focused `git diff --check` passed with LF/CRLF warnings only, and the new projection files have no trailing whitespace. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-SESSION-INITIAL-SNAPSHOT-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows no old `InitialBattleParticipantsConfig` imports from local session; only `GameScene` and the runtime boot snapshot factory import the local adapter function. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-LOCAL-FRAME-BRIDGE-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old local suppression helper names only in the audit guard, and local frame bridge imports `BattlePlayerInputRules.ts`. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ACTORS-MOVEMENT-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `movementController` only in the audit guard, while bot/local frame callers import `BattlePlayerMovementRules.ts`. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-TIMER-RULES-OWNERSHIP-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows skill timer advancement is delegated to `advanceBattleSkillTimer`, with no direct `skill.cooldownMs` or `skill.activeMs` assignment in local timers. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ACTORS-RUNTIME-TIMER-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows jump cooldown, dead runtime clearance, and non-local stamina recovery are delegated to `BattlePlayerRuntimeRules.ts`; local timer no longer owns those formulas directly. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-SWITCH-TIMER-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `advanceWeaponSwitchTimerState` lives in combat switch rules and old local helper names only remain in the audit guard. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ACTORS-RESPAWN-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `respawnController` only in the audit guard and respawn exports live in `BattlePlayerRespawnRules.ts`. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITY-MOTION-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows ability activation state helpers live in `BattleSkillStateRules.ts`, while `playerMotionAbilityHandler.ts` delegates readiness, activation, and prepared-skill toggle rules. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-WORLD-MOTION-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `local/movement/motionController` appears only in the audit guard, while motion/collision callers import `BattleMotionRules.ts` or route through the local scene adapter. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-DISPLACEMENT-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old `displacementResolver` only in the audit guard, while recoil/knockback destination planning lives in `BattleCombatDisplacementRules.ts` and the local adapter only applies returned positions. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-WEAPON-ACTION-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old local weapon action/profile files only in audit and historical worklog references; current renderer and bot callers import `BattleWeaponActionRules.ts`, and the structure audit requires `BattleWeaponRuntimeProfiles.ts`. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITY-PREDICTION-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows old local skill prediction/profile/target-validity files only in audit and historical worklog references; current local, renderer, and authoritative callers import abilities microservice rules. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-ABILITY-PREDICTION-TRACKER-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows pending prediction TTL, blink mismatch window, prediction match distance, distance calculation, vector cloning, and predicted cooldown helpers live in `BattleAuthoritativeSkillPredictionTrackerRules.ts`; `BattleAuthoritativeLocalSkillPredictionTracker.ts` only imports and applies the rule results. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-SHARED-AUTHORITATIVE-FEEDBACK-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old feedback helper/style names no longer exist in `sharedAuthoritativeLocalFeedbackSceneBridge.ts`; the new combat/abilities feedback rule files are required by the structure audit. Focused `git diff --check` passed with LF/CRLF warnings only. The only build note is Vite's existing large chunk warning for the Phaser/vendor battle chunks.
- Latest verification after `FRONTEND-BATTLE-COMBAT-PROJECTILE-FEEDBACK-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan over `frontend/src` plus `scripts` shows `projectileTerminalFeedbackPolicy` remains only in the audit guard; renderer effect modules import `BattleProjectileFeedbackRules.ts`. Focused `git diff --check` passed with LF/CRLF warnings only. The old moved file contained invalid UTF-8 comments, so import rewrites in the moved file and renderer callers used binary-safe ASCII replacements after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-HERO-PICKUP-FEEDBACK-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows hero/pickup feedback state builders, ammo-total calculation, and health/ammo delta planning no longer live in `heroAndPickupFeedbackPresenter.ts`; the new actors/abilities feedback rule files are required by the structure audit. Focused `git diff --check` passed with LF/CRLF warnings only. `heroAndPickupFeedbackPresenter.ts` contained invalid UTF-8 comments, so it was rewritten with Node after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-PROJECTILE-FEEDBACK-QUEUE-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the bridge no longer contains the old played-terminal projectile lookup, freshness formula, scratch live-projectile set, startsWith terminal-key scan, bounded-memory length formulas, or local terminal tracer predicate; the new queue rule file is required by the structure audit. Focused `git diff --check` passed with LF/CRLF warnings only.
- Latest verification after `FRONTEND-BATTLE-PROJECTILE-PRESENTATION-PLAN-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scans show the projectile terminal and remote-birth presenters no longer import or contain direct VFX planning helpers/constants; `BattleProjectileFeedbackPresentationRules.ts` is required by the structure audit. Focused `git diff --check` passed with LF/CRLF warnings only. The old presenter files contained invalid UTF-8 comments, so they were rewritten with a binary-safe script after `apply_patch` could not read them.
- Latest verification after `FRONTEND-BATTLE-PROJECTILE-DIAGNOSTIC-PLAN-RULES-ALIGN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileTerminalDiagnosticsRecorder.ts` no longer imports terminal diagnostic state/nearest-hero helpers or constructs terminal diagnostic payload formulas directly; the new diagnostic rule file is required by the structure audit. The old recorder file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script.
- Latest verification after `FRONTEND-BATTLE-REMOTE-VIEW-DIAGNOSTICS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `remoteViewDiagnostics.ts` no longer declares remote diagnostics object interfaces or local pure clone/metric helper functions; the new local diagnostics objects/functions files are required by the structure audit. The old diagnostics file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script while preserving exported function names and type re-exports.
- Latest verification after `FRONTEND-BATTLE-LOCAL-FEEDBACK-DIAGNOSTICS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `localFeedbackDiagnostics.ts` no longer declares local feedback diagnostics object interfaces or local pure clone/distance/channel helper functions; the new local feedback diagnostics objects/functions files are required by the structure audit. The old diagnostics file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script while preserving exported function names and type re-exports.
- Latest verification after `FRONTEND-BATTLE-LOCAL-HERO-CORRECTION-DIAGNOSTICS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `localHeroCorrectionDiagnostics.ts` no longer declares local hero correction diagnostics object interfaces or local pure distance/summary helper functions; the new local hero correction diagnostics objects/functions files are required by the structure audit. The old diagnostics file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script while preserving exported function names and type re-exports.
- Latest verification after `FRONTEND-BATTLE-VISION-DIAGNOSTICS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `visionDiagnostics.ts` no longer declares battle vision diagnostics object interfaces or local pure Vec2/geometry helper functions; the new vision diagnostics objects/functions files are required by the structure audit. The old diagnostics file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script while preserving exported function names and type re-exports.
- Latest verification after `FRONTEND-BATTLE-AUTH-LOCAL-REPLAY-DIAGNOSTICS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `authoritativeLocalHeroReplayDiagnostics.ts` no longer declares authoritative local replay diagnostics object contracts or local pure field-normalization/snapshot helper functions; the new authoritative local replay diagnostics objects/functions files are required by the structure audit. The old diagnostics file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-RENDERER-RUNTIME-OBJECTS-FUNCTIONS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows renderer runtime factory/boot/DOM/Phaser adapters no longer declare runtime object contracts, and `BattlePhaserGameFactory.ts` no longer inlines viewport fallback sizing; the new renderer runtime objects/functions files are required by the structure audit.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-CORRECTION-RUNTIME-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleLocalAuthoritativeHeroCorrectionController.ts` no longer declares the pending correction object or local distance/smoothing update rules; the new renderer authoritative correction objects/functions files are required by the structure audit.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-RENDER-PIPELINE-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleAuthoritativeRenderPipeline.ts` no longer declares pipeline input/frame object contracts; `BattleAuthoritativeRenderPipelineObjects.ts` imports the backend-aligned session/world microservice types directly.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-SCENE-BRIDGE-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleAuthoritativeFrameSceneBridge.ts` no longer declares scene bridge frame/options/input object contracts; `BattleAuthoritativeFrameSceneBridgeObjects.ts` imports backend-aligned actors/session/world microservice types directly while the bridge keeps the existing re-export path for `GameScene`.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-LOCAL-HERO-MOTION-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleAuthoritativeLocalHeroMotion.ts` no longer declares motion input/result contracts; `BattleAuthoritativeLocalHeroMotionObjects.ts` imports backend-aligned actors/session/world/core types and the local display pose port directly.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-SNAPSHOT-APPLIER-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleAuthoritativeFrameSnapshotApplier.ts` no longer declares local player correction/replay aliases or the apply input contract; `BattleAuthoritativeFrameSnapshotApplierObjects.ts` imports backend-aligned session snapshot/frame sync types directly while the applier keeps snapshot mutation and local replay/correction composition.
- Latest verification after `FRONTEND-BATTLE-AUTHORITATIVE-LOCAL-HERO-REPLAY-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleAuthoritativeLocalHeroReplay.ts` no longer declares the renderer replay target input contract; `BattleAuthoritativeLocalHeroReplayObjects.ts` imports backend-aligned abilities/actors/session/world/core types directly while the adapter keeps replay config and renderer dependency injection.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-LOCAL-HERO-DISPLAY-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleLocalHeroDisplay.ts` no longer declares local hero display pose/store/actor contracts; `BattleLocalHeroDisplayObjects.ts` imports the backend-aligned core Vec2 type while the adapter keeps Phaser actor read/write mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-ACTOR-HANDLE-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleGameSceneHeroActorBridge.ts` no longer declares the player actor handle contract; `BattleGameSceneHeroActorObjects.ts` owns the handle object while the bridge keeps Phaser actor creation and flash tween side effects.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-DISPLACEMENT-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleGameSceneHeroDisplacementBridge.ts` no longer declares displacement bridge options/handle contracts; `BattleGameSceneHeroDisplacementObjects.ts` owns those objects while the bridge keeps local geometry displacement side-effect wiring.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-READABILITY-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer declares readability view/health view contracts or the weapon cue style catalog; `HeroReadabilityViewObjects.ts` owns those renderer entity objects while the adapter keeps Phaser visual creation and synchronization. The old file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-READABILITY-RULES-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer defines readability radius, weapon style lookup, slow-field membership, health ratio, or finite-vector helper rules; `HeroReadabilityViewRules.ts` owns those pure renderer entity rules while the adapter keeps Phaser visual mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-REMOTE-HERO-INTERPOLATION-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `remoteHeroInterpolationView.ts` no longer declares remote hero interpolation object contracts or local interpolation helper rules; `RemoteHeroInterpolationObjects.ts` owns the renderer entity contracts and `RemoteHeroInterpolationRules.ts` owns sampling, buffer recording, interpolation, and smoothing rules while the adapter keeps scene time reads and view-state map mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-INTERPOLATION-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileAndFieldViewPresentation.ts` no longer declares projectile/slow-field view contracts, projectile interpolation buffers, sync context objects, or local projectile interpolation helper rules; `ProjectileAndFieldViewObjects.ts` owns those renderer entity contracts and `ProjectileInterpolationRules.ts` owns sampling, buffer recording, interpolation, and smoothing rules while the adapter keeps Phaser view pooling, culling, scene-time reads, and visual mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-FIELD-PRESENTATION-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows projectile cull padding/bounds checks, projectile lifetime alpha, and slow-field TTL alpha formulas no longer live in `projectileAndFieldViewPresentation.ts`; `ProjectileAndFieldPresentationRules.ts` owns those pure renderer entity rules while the adapter keeps camera reads and Phaser object lifecycle.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-LOCAL-HERO-MOTION-STREAK-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `localHeroMotionStreakView.ts` no longer declares the motion streak view object or local finite-vector, distance, speed, decay, angle, and render-plan formulas; `LocalHeroMotionStreakObjects.ts` owns the renderer entity contracts and `LocalHeroMotionStreakRules.ts` owns the pure update/render planning rules while the adapter keeps Phaser rectangle creation and mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PREPARED-SKILL-INDICATOR-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `preparedSkillIndicatorViewSync.ts` no longer declares prepared-skill indicator contracts or local target-validity/radius/color rules; `PreparedSkillIndicatorObjects.ts` owns the renderer entity contracts and `PreparedSkillIndicatorRules.ts` owns the pure visible/hidden indicator plan while the adapter keeps Phaser Arc mutation.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PICKUP-PRESENTATION-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `pickupViewPresentation.ts` no longer declares pickup view/style/input contracts, pickup readability style catalogs, or local bob/pulse/glint helper rules; `PickupViewPresentationObjects.ts` owns those renderer entity contracts/catalogs and `PickupViewPresentationRules.ts` owns pure weapon/item pickup motion planning while the adapter keeps Phaser pickup object creation and mutation. The old file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PICKUP-SYNC-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `pickupViewSync.ts` no longer declares pickup sync state/context contracts or inline live-id/hidden-view planning loops; `PickupViewSyncObjects.ts` owns the renderer entity sync contracts and `PickupViewSyncRules.ts` owns pure live-id and hidden-id planning while the adapter keeps scratch set replacement, Map lookup, visibility mutation, and pickup view syncing. The old file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-FACTORY-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer declares hero/world view contracts or inline weapon switch/reload action-progress formulas; `WorldViewFactoryObjects.ts` owns the renderer world contracts and `WorldViewFactoryRules.ts` owns the pure action progress ADT while the factory keeps Phaser scene object creation and view synchronization. The old file contained invalid UTF-8 comments, so it was rewritten with a binary-safe script after `apply_patch` could not read it.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-HERO-SYNC-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer owns hero alive/display-state selection or weapon action-progress formulas; `WorldViewFactoryObjects.ts` owns `HeroDisplayStatePlan` and `HeroVisibilityPlan`, while `WorldViewFactoryRules.ts` owns local-player, visibility, display-state, and action-progress rules. The factory still owns Phaser view creation, remote interpolation invocation, diagnostics, and visual mutation. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-CREATION-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer directly creates hero Phaser sprite/text/rectangle subviews or imports hero readability/weapon-overlay/motion-streak creation helpers; `heroWorldViewFactory.ts` owns the side-effectful hero view adapter, `HeroWorldViewFactoryObjects.ts` owns creation input/plan objects, and `WorldViewFactoryRules.ts` owns the pure hero creation presentation plan. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-STATE-FACTORY-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer directly initializes world-view Map/Set collections, scratch live-id sets, pickup view creation, hero view creation, or range/target Phaser indicators; `worldViewStateFactory.ts` owns the side-effectful state creation adapter, `WorldViewStateFactoryObjects.ts` owns indicator style/plan/view objects, and `WorldViewStateFactoryRules.ts` owns the pure indicator presentation plan. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-VISIBILITY-SYNC-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer directly applies hero base show/hide visibility mutations, weapon overlay hiding, or local motion streak hiding; `heroWorldViewVisibilitySync.ts` owns those renderer side effects and consumes the existing typed `HeroVisibilityPlan`. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-FRAME-SYNC-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer directly mutates hero sprite/name/health/action-bar/marker frame state or imports hero readability/local motion/action-progress sync helpers; `heroWorldViewSync.ts` owns those renderer side effects and `HeroWorldViewSyncObjects.ts` owns the frame-sync input contract typed against backend-aligned hero and snapshot objects. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-REMOTE-DISPLAY-SYNC-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer directly calls remote hero interpolation, records remote hero diagnostics, or branches on the remote-authoritative display plan; `heroWorldViewRemoteDisplaySync.ts` owns the renderer side-effect adapter and `HeroWorldViewRemoteDisplayObjects.ts` owns the typed input contracts. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEWS-SYNC-LOOP-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer owns hero loop cleanup/traversal or visibility/display/frame/remote diagnostics orchestration; `heroWorldViewsSync.ts` owns the side-effectful hero loop adapter and `HeroWorldViewsSyncObjects.ts` owns typed input contracts. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-DISPLAY-READERS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer reads `heroViews` or directly imports projectile display-position readers; `worldViewDisplayPositionReader.ts` owns the renderer world-view display-position query adapter while the factory keeps public export aliases. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-INDICATOR-SYNC-FACADE-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewFactory.ts` no longer unpacks prepared-skill indicator sync fields or imports `syncPreparedSkillIndicatorViews`; `worldViewIndicatorSync.ts` owns the world-view-to-prepared-indicator sync adapter while the factory keeps public export aliases and world-view composition. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-SLOW-FIELD-VIEW-SYNC-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileAndFieldViewPresentation.ts` no longer owns slow-field view sync, slow-field Phaser Arc creation, or slow-field live-id cleanup; `slowFieldViewSync.ts` owns that renderer entity adapter while reusing the existing backend-aligned slow-field state and shared projectile/field view objects. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-DISPLAY-READER-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileAndFieldViewPresentation.ts` no longer owns projectile display-position reads; `projectileDisplayPositionReader.ts` owns that renderer entity query adapter and `worldViewDisplayPositionReader.ts` delegates to it. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-VIEW-LIFECYCLE-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileAndFieldViewPresentation.ts` no longer owns projectile texture lookup, view creation/configuration/destruction, pool push/pop, or lifetime-alpha visual mutation; `projectileViewLifecycle.ts` owns that renderer entity lifecycle adapter while preserving backend-aligned projectile state fields. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-DISPLAY-STATE-SYNC-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileAndFieldViewPresentation.ts` no longer owns projectile interpolation sampling, buffer acquisition, fallback/interpolated display-state composition, or render-time reads; `projectileDisplayStateSync.ts` owns that renderer display-state adapter while reusing existing typed projectile view objects and interpolation rules. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-VIEW-SYNC-RENAME-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows frontend code no longer imports the old mixed `projectileAndFieldViewPresentation.ts` path; the file is deleted, `projectileViewSync.ts` now owns the public projectile sync loop, and `worldViewFactory.ts` imports that precise adapter. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-SLOW-FIELD-VIEW-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `ProjectileAndFieldViewObjects.ts` no longer declares slow-field view/state/sync context fields and `slowFieldViewSync.ts` no longer imports the projectile aggregate context; `SlowFieldViewObjects.ts` owns the slow-field renderer view object contracts while backend-aligned snapshot and slow-field state fields remain unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-VIEW-OBJECTS-RENAME-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows runtime frontend code no longer imports `ProjectileAndFieldViewObjects.ts`; the old mixed objects filename is retired, `ProjectileViewObjects.ts` owns projectile renderer view/state/sync contracts, and `WorldViewFactoryObjects.ts` imports projectile and slow-field view contracts from their separate files. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PRESENTATION-RULES-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old mixed `ProjectileAndFieldPresentationRules.ts` path is retired; `ProjectilePresentationRules.ts` owns projectile cull and lifetime-alpha rules, while `SlowFieldPresentationRules.ts` owns slow-field alpha rules. Gameplay constants and backend-aligned projectile/slow-field field names were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-REMOTE-HERO-INTERPOLATION-ADAPTER-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the old mixed `remoteHeroInterpolationView.ts` path is retired; `remoteHeroDisplayStateSync.ts` owns remote hero authoritative display-state orchestration, while `remoteHeroInterpolationBufferSync.ts` owns buffer cleanup. Remote hero interpolation rules, backend-aligned hero/snapshot fields, and gameplay constants were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WEAPON-OVERLAY-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWeaponOverlayView.ts` no longer declares overlay object contracts or local Math/offset/scale helper rules; `HeroWeaponOverlayObjects.ts` owns renderer overlay contracts, and `HeroWeaponOverlayRules.ts` owns pure layout/scale planning. WeaponKind, weapon raster atlas lookup, backend-aligned hero fields, and overlay gameplay constants were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-PRESENTATION-SCALE-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows the root-level pure `heroPresentationScale.ts` path is retired; `HeroPresentationScaleRules.ts` owns `getHeroBasePresentationScale` under `entities/functions`, and `GameScene` imports the rule from that functions boundary. Scale values and `BattleGameConstants` usage were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-ACTOR-TYPE-BOUNDARY-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleGameSceneHeroActorBridge.ts` no longer imports `HeroView` from the `worldViewFactory` facade; it now depends directly on `objects/WorldViewFactoryObjects.ts` for the renderer object contract. Actor creation, flash behavior, backend-aligned hero fields, and scene-level public facade imports were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-SLOW-FIELD-LIFECYCLE-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `slowFieldViewSync.ts` no longer owns slow-field Phaser circle creation, visual mutation, alpha application, or view destruction; `slowFieldViewLifecycle.ts` owns create/release/sync visuals while the sync loop keeps live-id and map traversal. Backend-aligned slow-field fields and alpha rules were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PREPARED-INDICATOR-VISUAL-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `preparedSkillIndicatorViewSync.ts` no longer owns prepared-skill indicator Phaser Arc visibility, position, radius, fill, or stroke mutation; `preparedSkillIndicatorViewVisualSync.ts` owns those visual side effects while the sync file keeps plan resolution. Backend-aligned prepared-skill fields, target validity, radius/color rules, and gameplay values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-HEALTH-VISUAL-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer owns hero health threshold constants, danger pulse math, background alpha clamping, or fixed width calculation; `HeroHealthVisualPlan` lives in `HeroReadabilityViewObjects.ts` and `resolveHeroHealthVisualPlan` lives in `HeroReadabilityViewRules.ts`, while the adapter only applies the plan to Phaser rectangles. Backend-aligned hero fields and gameplay health values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-FRAME-LAYOUT-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWorldViewSync.ts` no longer owns hero frame label/health/action offsets, direct `50 * progress` action width math, or direct display-position layout writes; `HeroWorldViewFrameLayoutPlan` lives in `HeroWorldViewSyncObjects.ts` and `HeroWorldViewFrameLayoutRules.ts` owns the pure layout plan. Backend-aligned hero/snapshot/weapon fields and existing action-progress rules were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-CREATION-PLAN-DETAILS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWorldViewFactory.ts` no longer owns label/health/action subview creation offsets, sizes, colors, origins, or stroke parameters; `HeroWorldViewFactoryObjects.ts` owns typed text/rectangle/stroke creation plans and `WorldViewFactoryRules.ts` owns the pure creation detail plan. Backend-aligned hero fields, microservice object paths, and gameplay action-progress rules were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-READABILITY-SYNC-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer owns readability sync radius/slow-field/status-ring/weapon-overlay planning; `HeroReadabilityViewObjects.ts` owns the typed readability visual plan contracts and `HeroReadabilityViewRules.ts` owns `resolveHeroReadabilityVisualPlan`. Backend-aligned hero, slow-field, and weapon fields were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-READABILITY-CREATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer owns readability creation radius/color/depth/origin/stroke details, status-ring creation values, marker creation values, or legacy weapon cue creation planning; `HeroReadabilityViewObjects.ts` owns the typed readability creation plan contracts and `HeroReadabilityViewRules.ts` owns `resolveHeroReadabilityCreationPlan`. Backend-aligned hero, slow-field, and weapon fields were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-HEALTH-TINT-RULE-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer imports `resolveHeroVisual` or passes a base tint into `resolveHeroHealthVisualPlan`; `HeroReadabilityViewRules.ts` now owns hero visual tint lookup for the health visual plan. Backend-aligned hero fields and health gameplay values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-SLOW-FIELD-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `slowFieldViewLifecycle.ts` no longer owns slow-field tint/depth/stroke constants, ttl/duration alpha planning, or fill/rim alpha composition; `SlowFieldViewObjects.ts` owns typed creation/visual plan contracts and `SlowFieldPresentationRules.ts` owns `resolveSlowFieldViewCreationPlan` plus `resolveSlowFieldViewVisualPlan`. Backend-aligned slow-field fields and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PICKUP-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `pickupViewPresentation.ts` no longer owns pickup depth constants, label/glint offsets, inner-ring scale, plate sizing, text style, pulse alpha composition, or direct item sprite tinting; `PickupViewPresentationObjects.ts` owns typed creation/visual plan contracts and `PickupViewPresentationRules.ts` owns `resolvePickupViewCreationPlan` plus `resolvePickupViewVisualPlan`. Backend-aligned pickup and weapon fields, texture keys, display-label sources, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-LOCAL-HERO-MOTION-STREAK-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `localHeroMotionStreakView.ts` no longer owns streak count/depth/tint, initial streak dimensions/origin, or hidden fill planning; `LocalHeroMotionStreakObjects.ts` owns typed creation/hidden/fill-color plan contracts and `LocalHeroMotionStreakRules.ts` owns `resolveLocalHeroMotionStreakCreationPlans`, `resolveLocalHeroMotionStreakRenderPlan`, and `resolveLocalHeroMotionStreakHiddenPlan`. Backend-aligned hero fields and motion gameplay rules were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-VIEW-LIFECYCLE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileViewLifecycle.ts` no longer owns projectile pool limit, sprite depth/origin, atlas lookup, texture scale/tint planning, or lifetime alpha planning; `ProjectileViewObjects.ts` owns typed creation/texture/visual/release plan contracts and `ProjectilePresentationRules.ts` owns `resolveProjectileViewCreationPlan`, `resolveProjectileViewTexturePlan`, `resolveProjectileViewVisualPlan`, and `resolveProjectileViewReleasePlan`. Backend-aligned projectile fields, atlas definitions, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-ACTOR-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `BattleGameSceneHeroActorBridge.ts` no longer imports `resolveHeroVisual`, `BASE_MOVE_SPEED`, or `SPRINT_MULTIPLIER`, and no longer owns actor texture, max-velocity/body-size, flash tint, restore tint, or restore delay planning; `BattleGameSceneHeroActorObjects.ts` owns typed actor creation/flash plans and `BattleGameSceneHeroActorRules.ts` owns `resolveGameSceneHeroActorCreationPlan` plus `resolveGameSceneHeroFlashPlan`. Backend-aligned hero fields, movement constants, flash timing semantics, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WEAPON-OVERLAY-CREATION-TEXTURE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWeaponOverlayView.ts` no longer owns overlay default weapon texture lookup, default `Pistol` literal, creation depth/origin/hidden-state planning, or direct weapon-world atlas lookup; `HeroWeaponOverlayObjects.ts` owns typed creation/texture plan contracts and `HeroWeaponOverlayRules.ts` owns `resolveHeroWeaponOverlayCreationPlan` plus `resolveHeroWeaponOverlayTexturePlan`. `WeaponKind`, raster atlas texture/frame fields, backend-aligned hero fields, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WORLD-VIEW-VISIBILITY-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWorldViewVisibilitySync.ts` no longer hardcodes direct `true`/`false` visibility mutations for hero subviews, weapon overlay hiding, or local motion streak reset from `HeroVisibilityPlan`; `HeroWorldViewsSyncObjects.ts` owns typed visibility mutation plans and `HeroWorldViewVisibilityRules.ts` owns hidden/base-visible plan resolution. Backend-aligned hero fields, visibility semantics, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-WEAPON-OVERLAY-SYNC-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWeaponOverlayView.ts` no longer calls texture/layout sub-rules directly for sync and no longer owns sync-time `visible: true` or alpha planning; `HeroWeaponOverlayObjects.ts` owns the typed visual plan contract and `HeroWeaponOverlayRules.ts` owns `resolveHeroWeaponOverlayVisualPlan`. `WeaponKind`, raster atlas texture/frame fields, backend-aligned hero fields, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-LOCAL-HERO-MOTION-STREAK-VISIBILITY-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `localHeroMotionStreakView.ts` no longer hardcodes render-time `streak.setVisible(true)`; `LocalHeroMotionStreakObjects.ts` includes render visibility in the typed render plan and `LocalHeroMotionStreakRules.ts` owns the render-visible decision. Backend-aligned hero fields, motion streak gameplay values, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PREPARED-INDICATOR-VISUAL-MUTATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `preparedSkillIndicatorViewVisualSync.ts` no longer owns direct hidden range/target indicator planning or hardcoded visible circle planning; `PreparedSkillIndicatorObjects.ts` owns typed visual mutation plan contracts and `PreparedSkillIndicatorRules.ts` owns `resolvePreparedSkillIndicatorVisualMutationPlan`. Backend-aligned hero/skill fields, prepared-skill semantics, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-PROJECTILE-VIEW-ACTIVATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileViewLifecycle.ts` no longer hardcodes acquire/release `setActive(...).setVisible(...)` lifecycle states; `ProjectileViewObjects.ts` owns typed activation/release plan contracts and `ProjectilePresentationRules.ts` owns acquire plus release active/visible planning. Backend-aligned projectile fields, projectile gameplay values, texture refs, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-ACTION-BAR-VISUAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroWorldViewSync.ts` no longer hardcodes action bar `setVisible(true)` frame mutations; `HeroWorldViewSyncObjects.ts` owns the typed action-bar visibility plan and `HeroWorldViewFrameLayoutRules.ts` owns the action-bar background/fill visibility planning. Backend-aligned hero, snapshot, and weapon fields, action-progress semantics, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-HERO-READABILITY-LEGACY-CUE-VISIBILITY-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `heroReadabilityView.ts` no longer hardcodes legacy weapon stock/cue/muzzle `setVisible(false)` mutations; `HeroReadabilityViewObjects.ts` owns the typed legacy weapon cue visibility plan and `HeroReadabilityViewRules.ts` owns the hidden legacy cue planning while weapon overlay remains the active weapon readability display. Backend-aligned hero, slow-field, and weapon fields, readability numeric rules, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-WORLD-VIEW-INDICATOR-CREATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `worldViewStateFactory.ts` no longer hardcodes indicator `scene.add.circle(0, 0, ...)` or `setVisible(false)` creation details; `WorldViewStateFactoryObjects.ts` owns indicator position/visibility in the typed creation plan and `WorldViewStateFactoryRules.ts` owns the initial hidden indicator planning. Backend-aligned Vec2 usage, indicator style values, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ENTITIES-SLOW-FIELD-RELEASE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `slowFieldViewLifecycle.ts` no longer directly calls `view.fill.destroy()` or `view.rim.destroy()` from the release entrypoint; `SlowFieldViewObjects.ts` owns typed release plan contracts and `SlowFieldPresentationRules.ts` owns the fill/rim destroy release plan while the lifecycle adapter applies Phaser destruction. Backend-aligned slow-field fields, slow-field visual values, sync-loop semantics, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BUILDER-OBJECTS-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBuilder.ts` no longer exports `ObstacleBounds`, `OccludableView`, `OccludableTrigger`, `OccludableSprite`, `OccludableMode`, or `ArenaBuilderContext`; `arena/objects/ArenaBuilderObjects.ts` owns those renderer arena object contracts and type-only callers now import from that objects boundary. `arenaDecorationPresenter.ts` had existing invalid UTF-8 comments, so its import was updated with a byte-preserving replacement after `apply_patch` could not read it. Backend-aligned world collision shape fields, arena behavior, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BUILDER-RULES-SPLIT-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBuilder.ts` no longer defines pure building wall, collision shape/size, occlusion trigger, decorative-kind, theme texture/background, or obstacle-depth helper rules; `arena/functions/ArenaBuilderRules.ts` owns those local arena rules while `arenaBuilder.ts` keeps Phaser construction and registration side effects. Backend-aligned world collision shape fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-OCCLUSION-ALPHA-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `occlusionAlphaController.ts` no longer declares occlusion alpha input objects or owns probe/fade/trigger pure rules; `arena/objects/OcclusionAlphaObjects.ts` owns typed occlusion alpha contracts and `arena/functions/OcclusionAlphaRules.ts` owns probe, fade-target, overlap, and alpha plan rules while the controller applies Phaser `setAlpha`. Backend-aligned hero fields, occludable object fields, arena behavior, and map data were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-OBSTACLE-SKIN-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `obstacleSkinPresenter.ts` no longer owns obstacle skin constants, border detection, footprint, corner, or sizing rules; `arena/objects/ObstacleSkinObjects.ts` owns the rectangle/stroke plan objects and `arena/functions/ObstacleSkinRules.ts` owns static obstacle metal-skin rectangle planning while the presenter applies Phaser rectangle and stroke rendering. `obstacleSkinPresenter.ts` had existing invalid UTF-8 comments, so the adapter file was rewritten as ASCII after `apply_patch` could not read it. Backend-aligned obstacle fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-NATURAL-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns natural map palette objects, terrain depth rules, or hex color parsing; `arena/objects/ArenaBackgroundObjects.ts` owns the natural map presentation palette contract and `arena/functions/ArenaBackgroundRules.ts` owns palette selection, terrain depth, and color parsing while the presenter keeps Phaser drawing. `arenaBackgroundPresenter.ts` had existing invalid UTF-8 comments, so its targeted text replacement rewrote the file as valid UTF-8 after `apply_patch` could not read it. Backend-aligned map fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-BOUNDARY-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns boundary readability shadow, warning, energy, hero-center-limit, or tick planning helpers; `arena/objects/ArenaBackgroundObjects.ts` owns background rectangle/stroke plan objects and `arena/functions/ArenaBackgroundRules.ts` owns boundary readability rectangle planning while the presenter only renders those plans with Phaser rectangles and strokes. Backend-aligned world/map fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-OUT-OF-BOUNDS-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns out-of-bounds haze, edge-band, or rail/glint planning; `arena/functions/ArenaBackgroundRules.ts` owns out-of-bounds rectangle planning while the presenter only renders those plans with Phaser rectangles. Backend-aligned world/map fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-NATURAL-TEXTURE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns natural ground speck, edge-accent, or source-crop frame/buffer geometry planning; `arena/objects/ArenaBackgroundObjects.ts` owns ellipse and natural-ground texture plan objects, and `arena/functions/ArenaBackgroundRules.ts` owns the pure natural texture/crop rectangle planning. Backend-aligned source crop/map fields, arena behavior, map data, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-METAL-ACCENT-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns metal panel seam, rivet, central-panel, light-strip, or corner-shadow helper planning; `arena/functions/ArenaBackgroundRules.ts` owns those metal accent rectangle plans while the presenter keeps tileSprite/pattern rendering and rectangle application. Backend-aligned world/map fields, arena behavior, map data, texture keys, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-METAL-FLOOR-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns metal floor base rectangle or stroke planning, including the old `BORDER_ENERGY_COLOR`; `arena/functions/ArenaBackgroundRules.ts` owns metal floor rectangle planning while the presenter keeps tileSprite/pattern rendering and rectangle application. Backend-aligned world/map fields, arena behavior, map data, texture keys, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-NATURAL-TERRAIN-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns natural outer/playable background rectangle planning or terrain patch shape/color/depth/rotation planning; `arena/objects/ArenaBackgroundObjects.ts` owns terrain patch plan objects and rectangle rotation, while `arena/functions/ArenaBackgroundRules.ts` owns terrain/background planning from backend-aligned `TerrainPatchDefinition` fields. Backend-aligned map fields, arena behavior, map data, texture keys, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-BACKGROUND-PATTERN-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaBackgroundPresenter.ts` no longer owns non-natural shell or metal floor tile/pattern layout, tint, alpha, or depth planning; `arena/objects/ArenaBackgroundObjects.ts` owns pattern plans and texture roles, while `arena/functions/ArenaBackgroundRules.ts` owns pattern layout planning. The presenter only maps texture roles to existing texture key constants and applies Phaser tileSprites. Backend-aligned world/map fields, arena behavior, map data, texture key values, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-DECORATION-PICKUP-PAD-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaDecorationPresenter.ts` no longer owns pickup-pad palette, shape, stroke, or tile-pattern planning; `arena/objects/ArenaDecorationObjects.ts` owns decoration pickup-pad plan objects and texture roles, while `arena/functions/ArenaDecorationRules.ts` owns pickup-pad planning from backend-aligned `PickupSpawnPoint` fields. `arenaDecorationPresenter.ts` was converted from existing invalid UTF-8 comments to UTF-8 during the targeted pickup-pad edit after `apply_patch` could not read it. Backend-aligned pickup spawn point fields, world-map fields, arena behavior, map data, texture key values, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-ARENA-DECORATION-INDUSTRIAL-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `arenaDecorationPresenter.ts` no longer owns industrial pylon, machinery, or low-deck plate position lists, direct industrial image creation, pylon/machine local occludable variables, or industrial visual numeric planning; `arena/objects/ArenaDecorationObjects.ts` owns image/element/presentation plan contracts and expanded renderer texture roles, while `arena/functions/ArenaDecorationRules.ts` owns industrial decoration element planning. Winter set-piece code, backend-aligned fields, map data, texture key values, and visual numeric values were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-CAMERA-DIRECTOR-RULES-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `battleCameraDirector.ts` no longer owns camera input object declarations, camera configuration constants, pointer readiness resolution, clamp, or lerp/look-ahead planning; `renderer/camera/objects/BattleCameraObjects.ts` owns typed camera input and plan contracts, while `renderer/camera/functions/BattleCameraRules.ts` owns pure bounds, deadzone, look-ahead, clamp, and linear planning. `battleCameraDirector.ts` was converted from existing invalid UTF-8 comments to ASCII/UTF-8 during the targeted rewrite after `apply_patch` could not read it. Backend-aligned Vec2 usage, camera behavior values, diagnostics field meanings, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-FLOATING-TEXT-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `floatingTextVfxPresenter.ts` no longer owns floating tone/dependency object declarations, palette, font/stroke/depth/origin values, text offset values, or tween duration/ease planning; `renderer/effects/objects/FloatingTextVfxObjects.ts` owns typed floating text VFX contracts and `renderer/effects/functions/FloatingTextVfxRules.ts` owns tone color, creation style, layout, and tween plans. Backend-aligned Vec2 usage, floating text colors/timing/layout behavior, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-WEAPON-ACTION-PRESENTATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `weaponActionPlanPresenter.ts` no longer owns callback object declarations, canFire branching, muzzle/pulse/impact/recoil presentation planning, or aim normalization math; `renderer/effects/objects/WeaponActionPlanPresenterObjects.ts` owns typed callback/input/presentation plan contracts and `renderer/effects/functions/WeaponActionPlanPresentationRules.ts` owns pure presentation planning from the combat microservice `WeaponActionPlan`. Backend-aligned combat fields, callback order, aim vector math, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-COMBAT-PROJECTILE-EFFECT-PRESENTATION-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `combatProjectileEffectPresenter.ts` no longer owns callback object declarations, effect type branching, visual constants, snapshot hero lookup, local-player killed branching, or knockback vector math; `renderer/effects/objects/CombatProjectileEffectPresenterObjects.ts` owns typed callback/input/action plan contracts and `renderer/effects/functions/CombatProjectileEffectPresentationRules.ts` owns pure presentation planning from the combat microservice `CombatProjectileEffect`. Backend-aligned combat/session fields, callback order, knockback direction normalization, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-TRACER-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `projectileTracerVfxRenderer.ts` no longer owns tracer object declarations, default tracer constants, direction/perpendicular helpers, deterministic geometry calculations, alpha-scale clamping, or tween timing/scale planning; `renderer/effects/objects/ProjectileTracerVfxObjects.ts` owns typed dependency/options/shape/tween plan contracts and aliases the combat microservice `ProjectileTracerFeedbackOptions`, while `renderer/effects/functions/ProjectileTracerVfxRules.ts` owns pure tracer geometry and tween planning. Phaser creation/tracking/tween/destruction and random glint side selection remain in the renderer adapter. Backend-aligned combat feedback fields, tracer visual values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-MUZZLE-HIT-SHOCKWAVE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `muzzleAndHitVfxPresenter.ts` no longer owns transient dependency aliases, the presenter dependency object, or direct shockwave circle/stroke/scale/tween planning; `renderer/effects/objects/MuzzleAndHitVfxObjects.ts` owns typed presenter dependency and shockwave plan contracts while `renderer/effects/functions/MuzzleAndHitVfxRules.ts` owns pure shockwave shape/tween planning. Phaser creation, stroke application, transient tracking, tween scheduling, and destruction remain in the presenter adapter. Backend-aligned combat feedback fields, VFX values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-MUZZLE-HIT-IMPACT-SPARK-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `muzzleAndHitVfxPresenter.ts` no longer hardcodes impact spark burst/spark count, radial angle layout, random sampling ranges, spark shape constants, or impact-spark tween values; `renderer/effects/objects/MuzzleAndHitVfxObjects.ts` owns typed impact spark sample/shape/tween plan contracts while `renderer/effects/functions/MuzzleAndHitVfxRules.ts` owns impact spark sampling config plus pure burst/spark shape and tween planning. Phaser random sampling, creation, tracking, tween scheduling, and destruction remain in the presenter adapter, preserving separate x/y travel-distance samples. Backend-aligned combat feedback fields, VFX values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-MUZZLE-HIT-PROJECTILE-DISSIPATE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `muzzleAndHitVfxPresenter.ts` no longer hardcodes projectile dissipate ring/mote circle radii, alpha/depth/stroke values, or tween values; `renderer/effects/objects/MuzzleAndHitVfxObjects.ts` owns typed projectile dissipate plan contracts while `renderer/effects/functions/MuzzleAndHitVfxRules.ts` owns pure ring/mote shape and tween planning. Phaser creation, tracking, tween scheduling, and destruction remain in the presenter adapter. Backend-aligned combat feedback fields, VFX values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-MUZZLE-HIT-HIT-CONFIRM-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `muzzleAndHitVfxPresenter.ts` no longer hardcodes hit-confirm graphics line/fill/circle/crosshair values or tween values; `renderer/effects/objects/MuzzleAndHitVfxObjects.ts` owns typed graphics command and hit-confirm plan contracts while `renderer/effects/functions/MuzzleAndHitVfxRules.ts` owns the hit-confirm graphics command sequence and tween planning. Phaser graphics creation, command application, tracking, tween scheduling, and destruction remain in the presenter adapter. Backend-aligned combat feedback fields, VFX values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-MUZZLE-HIT-MUZZLE-BURST-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `muzzleAndHitVfxPresenter.ts` no longer hardcodes muzzle-burst max spark count, direction/perpendicular normalization math, core/flash/spark shape constants, spread/distance/lateral-drift sampling ranges, or muzzle-burst tween values; `renderer/effects/objects/MuzzleAndHitVfxObjects.ts` owns typed muzzle-burst sample/shape/tween/ring-pulse plan contracts while `renderer/effects/functions/MuzzleAndHitVfxRules.ts` owns pure direction, sampling config, core/flash/spark shape, and tween planning. Phaser random sampling, ring-pulse callback invocation, creation, tracking, tween scheduling, and destruction remain in the presenter adapter, preserving spread/distance/lateral-drift/length/duration random sampling order. Backend-aligned combat feedback fields, VFX values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SKILL-DASH-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `skillFeedbackVfxPresenter.ts` no longer hardcodes dash ring graphics values, dash direction/perpendicular/rotation math, dash streak offsets/lengths/positions/alpha/depth values, or dash tween values; `renderer/effects/objects/SkillFeedbackVfxObjects.ts` owns typed skill-feedback dependency, graphics command, rectangle shape, tween, and dash plan contracts while `renderer/effects/functions/SkillFeedbackVfxRules.ts` owns pure dash direction, ring command, streak shape, and tween planning. Phaser graphics creation, command application, rectangle creation, transient tracking, tween scheduling, and destruction remain in the presenter adapter, preserving the dash ring command sequence and `[-8, 0, 8]` streak order. Backend-aligned skill feedback intent, ability microservice fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SKILL-BLINK-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `skillFeedbackVfxPresenter.ts` no longer hardcodes blink radius/depth/scale/colors, direction/perpendicular normalization math, diamond/circle/line/fill command values, or blink tween values; `renderer/effects/objects/SkillFeedbackVfxObjects.ts` owns typed blink plan contracts and expanded graphics command contracts while `renderer/effects/functions/SkillFeedbackVfxRules.ts` owns pure blink direction, marker command, and tween planning. Phaser graphics creation, command application, transient tracking, tween scheduling, and destruction remain in the presenter adapter, preserving the blink marker command sequence and release/prepare tween values. Backend-aligned skill feedback intent, ability microservice fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SKILL-FREEZE-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `skillFeedbackVfxPresenter.ts` no longer hardcodes freeze radius/depth/scale/colors, shard count, random radius sampling ranges, circle/shard command values, trig shard geometry, or freeze tween values; `renderer/effects/objects/SkillFeedbackVfxObjects.ts` owns typed freeze random sampling and marker plan contracts while `renderer/effects/functions/SkillFeedbackVfxRules.ts` owns pure Freeze radius, sampling config, marker command, shard geometry, and tween planning. Phaser random sampling, graphics creation, command application, transient tracking, tween scheduling, and destruction remain in the presenter adapter, preserving per-shard inner-then-outer sampling and the base circle/shard command sequence. Backend-aligned skill feedback intent, ability microservice fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SKILL-REJECTION-VFX-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `skillFeedbackVfxPresenter.ts` no longer hardcodes rejection size/depth/scale/color values, cross/scratch/circle command values, or rejection tween values; `renderer/effects/objects/SkillFeedbackVfxObjects.ts` owns typed rejection plan contracts while `renderer/effects/functions/SkillFeedbackVfxRules.ts` owns pure rejection size, cross/scratch/circle command, and tween planning. Phaser graphics creation, command application, transient tracking, tween scheduling, and destruction remain in the presenter adapter, preserving the rejection cross, highlight scratch, circle, and tween sequence. Backend-aligned skill feedback intent, ability microservice fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SCENE-RING-PULSE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `sceneVfxController.ts` no longer hardcodes ring pulse fill alpha, depth, stroke width/alpha, TTL, scale growth, or alpha fade values; `renderer/effects/objects/SceneVfxObjects.ts` owns typed ring pulse effect, shape, lifetime, and update plan contracts while `renderer/effects/functions/SceneVfxRules.ts` owns pure ring pulse creation and TTL update planning. Phaser circle creation, transient tracking, diagnostics publishing, state array compaction, and destruction remain in `SceneVfxController`, preserving 220ms TTL, 0.18 fill/fade alpha, 0.42 scale growth, depth 45, and stroke style. Backend-aligned feedback contracts, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PLAYER-MOTION-AFTERIMAGE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, and `npm run build` passed. Focused scan shows `playerMotionTweenController.ts` no longer hardcodes motion trail delay, motion tint/alpha choices, jump sprite tween scale/ease, motion tween ease choice, jump arc scale amplitude, completion pulse values, afterimage depth/scale fade/duration values, or the local `MotionType` alias; `renderer/effects/objects/PlayerMotionTweenObjects.ts` owns typed motion type, trail feedback, completion pulse, sprite tween, and afterimage plan contracts while `renderer/effects/functions/PlayerMotionTweenRules.ts` owns pure motion feedback timing/visual planning. Phaser timer creation, tween scheduling, player actor/sprite mutation, local hero position mutation, afterimage object creation/destruction, and pulse callback invocation remain in `PlayerMotionTweenController`. Backend-aligned command/hero fields, local movement command semantics, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-TRANSIENT-VFX-LIFECYCLE-PLAN-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `transientVfxLifecycle.ts` no longer declares transient lifecycle object contracts, hardcodes capacity/compaction constants, owns `Math.max` peak calculation, or manually constructs the diagnostics snapshot object; `renderer/effects/objects/TransientVfxLifecycleObjects.ts` owns lifecycle diagnostics/contracts and `renderer/effects/functions/TransientVfxLifecycleRules.ts` owns pure capacity, compaction, peak, and diagnostics snapshot rules. Phaser object destruction, Map/array lifecycle mutation, diagnostics root mutation, and `sceneVfxController.ts` type re-export wiring remain in effects adapters. Backend-aligned fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-HERO-PICKUP-PRESENTATION-ACTIONS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `heroAndPickupFeedbackPresenter.ts` no longer declares local presentation option/action types or maps backend hero/pickup feedback plans directly; `renderer/effects/objects/HeroAndPickupFeedbackPresenterObjects.ts` owns typed callbacks, options, and action ADTs while `renderer/effects/functions/HeroAndPickupFeedbackPresentationRules.ts` owns pure plan-to-action mapping. The presenter adapter still calls the actors/abilities microservice feedback rules and executes floating text, pulse, flash, spark, hit-confirm, and camera-shake callbacks in the same order. Backend-aligned hero/pickup fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-COMBAT-PROJECTILE-SCENE-BRIDGE-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `combatProjectileEffectSceneBridge.ts` no longer declares `CombatProjectileEffectSceneBridgeOptions` or directly performs `snapshot.heroes.find` / `target.alive` lookup; `renderer/effects/objects/CombatProjectileEffectSceneBridgeObjects.ts` owns the scene-bridge options and lookup input contracts while `renderer/effects/functions/CombatProjectileEffectSceneBridgeRules.ts` owns pure alive-target lookup. Scene-side callbacks and actual knockback application remain in the scene bridge, preserving the missing/dead hero skip behavior. Backend-aligned combat effect fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-WEAPON-ACTION-SCENE-BRIDGE-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `weaponActionSceneBridge.ts` no longer declares `WeaponActionSceneBridgeOptions` or owns the direct `!player.alive / preparedSkill / motion / switch` fire-readiness condition; `renderer/effects/objects/WeaponActionSceneBridgeObjects.ts` owns typed scene-bridge options/readiness input contracts while `renderer/effects/functions/WeaponActionSceneBridgeRules.ts` owns the pure readiness rule. Projectile sequence mutation, projectile callback, VFX callbacks, floating text, and recoil application remain in the scene bridge/presenter adapter layer. Backend-aligned combat weapon fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PLAYER-ABILITY-SCENE-BRIDGE-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `playerAbilitySceneBridge.ts` no longer declares `PlayerAbilitySceneBridgeOptions` or directly resolves `getHeroViews().get(...).sprite.texture.key ?? "hero-player"`; `renderer/effects/objects/PlayerAbilitySceneBridgeObjects.ts` owns typed scene-bridge options/texture-key input contracts while `renderer/effects/functions/PlayerAbilitySceneBridgeRules.ts` owns the pure texture-key resolver and existing `hero-player` fallback. Player motion, afterimage, pulse, floating text, and freeze-field callbacks remain in the scene bridge/local handler adapter layer. Backend-aligned command/hero fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-FRAME-SCENE-BRIDGE-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `projectileFrameSceneBridge.ts` no longer declares `ProjectileFrameSceneBridgeOptions` or directly owns the `obstacleCollision === null || obstacleBoundsRef !== obstacleBounds` cache-refresh condition; `renderer/effects/objects/ProjectileFrameSceneBridgeObjects.ts` owns typed scene-bridge options/cache contracts while `renderer/effects/functions/ProjectileFrameSceneBridgeRules.ts` owns the pure reference-based cache refresh rule. Obstacle-collision adapter creation, effect presentation, and `snapshot.projectiles` mutation remain in `ProjectileFrameSceneBridge`. Backend-aligned projectile/frame fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-FEEDBACK-EFFECT-PRESENTER-ACTIONS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `projectileFeedbackEffectPlanPresenter.ts` no longer declares `BattleProjectileFeedbackEffectPresenterCallbacks` or switches directly on `effect.effect`; `renderer/effects/objects/ProjectileFeedbackEffectPlanPresenterObjects.ts` owns callback/action contracts while `renderer/effects/functions/ProjectileFeedbackEffectPlanPresentationRules.ts` owns pure backend projectile feedback effect-to-action mapping. Callback execution remains in the presenter adapter and preserves the one-action-per-effect order. Backend-aligned combat feedback effect fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-TERMINAL-DIAGNOSTICS-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `projectileTerminalDiagnosticsRecorder.ts` no longer declares projectile terminal diagnostics input interfaces or owns `collectHeroDisplayPositions`; `renderer/effects/objects/ProjectileTerminalDiagnosticsRecorderObjects.ts` owns recorder input/display-position contracts while `renderer/effects/functions/ProjectileTerminalDiagnosticsRecorderRules.ts` owns pure cloned hero display-position map collection. Remote diagnostics gate and recording side effects remain in the recorder adapter. Backend-aligned combat/session diagnostic fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-REMOTE-PROJECTILE-BIRTH-PRESENTER-ACTIONS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `remoteProjectileBirthFeedbackPresenter.ts` no longer declares remote birth presenter interfaces or directly reads backend plan fields (`projectile`, `ownerDisplayName`, `position`, `effects`); `renderer/effects/objects/RemoteProjectileBirthFeedbackPresenterObjects.ts` owns callbacks/input/action contracts while `renderer/effects/functions/RemoteProjectileBirthFeedbackPresentationRules.ts` owns pure backend remote birth plan-to-action mapping. Diagnostics recording still runs before effect presentation for each plan in the presenter adapter. Backend-aligned combat feedback fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-TERMINAL-VFX-PRESENTER-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `projectileTerminalVfxPresenter.ts` no longer declares terminal VFX presenter callback or presentation input contracts; `renderer/effects/objects/ProjectileTerminalVfxPresenterObjects.ts` owns those contracts while the presenter keeps direct backend combat planner calls plus effect callback execution. Type-only export compatibility for `BattleFeedbackSceneBridge` was preserved. Backend-aligned combat planner names and fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-BATTLE-FEEDBACK-SCENE-BRIDGE-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `battleFeedbackSceneBridge.ts` no longer declares `BattleFeedbackSceneBridgeOptions`; `renderer/effects/objects/BattleFeedbackSceneBridgeObjects.ts` owns the scene bridge options contract while the class remains the side-effect adapter. Constructor compatibility for the game-scene feedback bridge factory was preserved. Backend-aligned session snapshot/vector fields, combat feedback callback shape, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PLAYER-MOTION-TWEEN-OPTIONS-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `playerMotionTweenController.ts` no longer declares `PlayerMotionTweenControllerOptions`; `renderer/effects/objects/PlayerMotionTweenObjects.ts` owns the controller options contract plus existing motion plans while the controller remains the Phaser tween/hero-position mutation adapter. Backend-aligned hero/vector contracts, renderer `HeroView` shape, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SHARED-AUTH-LOCAL-FEEDBACK-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `sharedAuthoritativeLocalFeedbackSceneBridge.ts` no longer declares `LocalProjectileTracerFeedback` or `SharedAuthoritativeLocalFeedbackSceneBridgeOptions`; `renderer/effects/objects/SharedAuthoritativeLocalFeedbackSceneBridgeObjects.ts` owns the tracer alias and options contract while the scene bridge remains the adapter that executes VFX callbacks and local muzzle diagnostics. Backend-aligned player command/hero/vector and combat/ability feedback contracts, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-FACTORY-INPUT-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scans show `effects/factories` no longer declares `CreateGameSceneBattleFeedbackBridgeInput` or `CreateGameSceneSharedAuthoritativeLocalFeedbackBridgeInput`; `renderer/effects/objects/GameSceneBattleFeedbackBridgeFactoryObjects.ts` and `renderer/effects/objects/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactoryObjects.ts` own those input contracts while the factories remain callback-wiring adapters. Backend-aligned session snapshot, hero/vector, scene VFX contracts, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-SCENE-VFX-TYPE-EXPORT-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `sceneVfxController.ts` no longer imports or re-exports `SkillFeedbackIntent` and `FloatingTone` through presenter adapters; those public type exports now point at `renderer/effects/objects/SkillFeedbackVfxObjects.ts` and `renderer/effects/objects/FloatingTextVfxObjects.ts` while presenter value imports and runtime VFX orchestration remain unchanged. Backend-aligned skill feedback intent, local VFX tone contract, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PRESENTER-TYPE-FACADE-RETIRE-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `combatProjectileEffectPresenter.ts`, `floatingTextVfxPresenter.ts`, `remoteProjectileBirthFeedbackPresenter.ts`, `skillFeedbackVfxPresenter.ts`, and `weaponActionPlanPresenter.ts` no longer re-export object-owned callback/tone/intent types; those contracts remain owned by `renderer/effects/objects` while presenters stay runtime Phaser/VFX adapters. Backend-aligned combat/skill/VFX contracts, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-REMAINING-TYPE-FACADE-RETIRE-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows the remaining targeted effects adapters and factories no longer re-export object-owned types from `effects/objects`; `projectileFeedbackEffectPlanPresenter.ts`, `projectileTerminalVfxPresenter.ts`, `projectileTracerVfxRenderer.ts`, `sceneVfxController.ts`, `sharedAuthoritativeLocalFeedbackSceneBridge.ts`, and the two game-scene effects factories now keep their type contracts owned by objects while runtime functions/classes remain exported. Backend-aligned projectile, scene VFX, shared-authoritative feedback, factory input contracts, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-EFFECTS-PROJECTILE-DIAGNOSTICS-GATE-ADAPTER-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scans show `BattleFeedbackSceneBridge` no longer imports or calls `shouldRecordProjectileTerminalDiagnostics`, and `projectileTerminalDiagnosticsRecorder.ts` no longer exports that gate. Skipped authoritative terminal diagnostics now use a recorder entry point that checks diagnostics enablement before lazily reading the snapshot. Backend-aligned projectile diagnostic fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-RENDERER-HUD-SCENE-BRIDGE-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `battleHudSceneBridge.ts` no longer declares `BattleHudSceneBridgeContext`; `renderer/hud/objects/BattleHudSceneBridgeObjects.ts` owns the HUD scene bridge context while the bridge remains the DOM/HUD rendering adapter. Backend-aligned session snapshot/vector fields, HUD presenter obstacle bounds, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-RENDERER-PRESENTATION-INPUT-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `BattleGameSceneWorldViewPresentation.ts`, `BattleGameSceneOcclusionPresentation.ts`, and `BattleGameSceneHudPresentation.ts` no longer declare their input interfaces; `renderer/presentation/objects` owns `SyncGameSceneWorldViewsInput`, `UpdateGameSceneOcclusionInput`, and `RenderGameSceneHudInput` while presentation files remain scene/HUD/world-view adapter functions. Backend-aligned snapshot/hero/vector/player-command fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-RENDERER-INPUT-BRIDGE-OBJECTS-RULES-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `BattleGameSceneInputBridge.ts` no longer declares `ReadGameScenePlayerCommandInput` or the local `suppressUnreadyAuthoritativePreparedToggle`/`isAuthoritativeSkillReady` helpers; `renderer/input/objects/BattleGameSceneInputBridgeObjects.ts` owns the input contract and `renderer/input/functions/BattleGameSceneInputBridgeRules.ts` owns the pure prepared-toggle suppression rule. Backend-aligned player command/hero/skill/vector/session snapshot fields, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.
- Latest verification after `FRONTEND-BATTLE-RENDERER-ASSET-ATLAS-OBJECTS-01`: `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:frontend-structure`, focused inline atlas interface scan, `npm run audit:domain-alignment`, `npm run audit:battle-content`, `npm run build`, scoped `git diff --check`, and trailing-whitespace scan passed. Focused scan shows `BattleWeaponRasterAtlas.ts` and `BattleProjectileRasterAtlas.ts` no longer export inline texture ref interfaces; `renderer/assets/objects` owns weapon/projectile texture ref contracts and entity object plans type against those contracts. Backend-aligned combat kind fields, atlas frame values, map data, and winter map/set-piece work were unchanged. Vite still reports the existing large chunk warning for `runtime-battle` and `vendor-phaser`.

Next frontend ticket:

- Continue scanning frontend runtime battle renderer for the next non-winter mixed planning or inline object boundary; avoid winter map/set-piece work while winter map expansion is active.

## Current Goal

Refactor `backend/src/main/scala/services/battle` toward the requested battle-level core plus domain microservices shape, and continue the active IO-monadization wave for backend battle microservices:

```text
services/battle/{api,objects,routes,database}
services/battle/microservices/{domain}/{api,objects,routes,database}
```

The user reset the objective: move battle-internal Objects and API logic into the matching microservice; top-level battle should keep only core/shared battle-level logic.

Current IO objective:

- Service/runtime boundaries that read time, mutable state, rule books, repositories, ports, or thread-local map context should return `IO`.
- In-process state should use cats-effect allocation (`Ref`, then `Deferred`/`Queue`/`Resource` where the flow needs them), not `var + synchronized` or ad hoc process state.
- `apiTypes` should stay encode/decode only; orchestration belongs in API planners or services.

Current IO plan:

```text
problem/backend-battle-io-monadization-plan.md
```

Recent IO progress:

- `BE-BATTLE-IO-STATE-REF-01`: replaced `InMemoryBattleStateService` lock/var state with `Ref[IO, Map[BattleId, StoredBattle]]`; production runtime construction now composes in `IO`/`Resource`.
- `BE-BATTLE-IO-QUEUE-REF-01`: replaced `InMemoryBattleQueueService` `AtomicReference` runtime state with `Ref[IO, QueueRuntimeState]`.
- `BE-BATTLE-IO-RULEBOOK-READS-01`: moved combat, runtime, bot, pickup, skill, and world rule-book reads behind `IO` and propagated call sites.
- `BE-BATTLE-IO-ARENA-CONTEXT-01`: removed battle runtime `ThreadLocal` map context by introducing explicit `BattleArenaContext`; collision, motion, player, bot, weapon fire, projectile, command, and extraction rules now receive map/world context through `IO`.
- `BE-BATTLE-IO-RULEBOOK-RESOURCE-01`: introduced `BattleDynamicRuleBook`, backed by `Ref[IO, BattleDynamicRules]`, and passed it from `BackendRuntime` through the session/runtime simulation path. Production startup now loads dynamic rules into this explicit dependency instead of mutating global `*RuleBook` objects.
- `BE-BATTLE-IO-RULEBOOK-LEGACY-CLEANUP-01`: deleted the remaining global `*RuleBook` `AtomicReference` objects and removed the test runner `.install()` path. Tests now build runtime defaults as explicit `BattleDynamicRules`.
- `BE-BATTLE-IO-ABILITY-COMMAND-01`: converted blink/dash/freeze command application to return `IO[CommandApplication]` and sequenced skill command composition in `BattleCommandApplicationRules` with monadic folding.

## Current Decision Gate

Decision file:

```text
problem/battle-four-layer-decision-gate.md
```

Detailed report:

```text
problem/battle-architecture-full-report.md
```

Four-layer rationality report:

```text
problem/battle-four-layer-refactor-rationality.md
```

Four-layer migration plan:

```text
problem/battle-four-layer-migration-plan.md
```

Four-layer decision request:

```text
problem/battle-four-layer-decision-request.md
```

Five-layer microservices decision:

```text
problem/battle-five-layer-microservices-decision.md
```

Decision:

```text
Strict battle-level api/objects/routes/database plus fifth microservices layer.
Each microservice should recursively keep api/objects/routes/database, plus transitional services while the service-layer migration is still incomplete.
Shared battle/objects stay for battle-core ADTs and value objects.
Domain-local objects should move under microservices/{domain}/objects.
Top-level battle/api should stay empty unless a future battle-level API surface cannot be cleanly owned by a domain microservice.
Current ownership rule: command/state belong to microservices/session/api; queue/room belong to microservices/queue/api; results belongs to microservices/results/api.
```

Rejected:

```text
runtime/
application/
engine/
services/
```

Reason:

- User first chose strict four layers, then explicitly allowed a fifth `microservices` layer.
- User wants the heavy business logic decomposed into microservices.
- Existing `microservices/*/services` is transitional and should be recursively reshaped into microservice-local api/objects/routes/database.
- Existing domain-local `battle/objects/*` should move into microservice-local objects when it is not shared.
- Public APIMessage planners still belong to battle-level `api`; they may call microservice services and use microservice-owned objects/apiTypes.

## Current Evidence

Latest inspected `services/battle` state:

| Folder | Scala files | Meaning |
| --- | ---: | --- |
| `api` | 0 | no top-level battle API surface remains; current battle APIs are owned by microservices |
| `objects` | 7 | battle-core ADTs/value objects/aggregate state; `package.scala` now exports only core objects |
| `routes` | 2 | battle API registry and runtime context |
| `database` | 16 | PostgreSQL tables plus transitional rule books |
| `microservices` | 144 | recursive domain-local api/objects/routes/database/services logic |

Known high-risk areas:

- `microservices/session/services/BattleStateService.scala`: mutable `var battles`.
- `microservices/queue/services/BattleQueueService.scala`: `AtomicReference` and `synchronized` runtime state remains an application-service shell.
- `microservices/projections/services/BattleReplayFramesJsonRenderer.scala`: typed Circe render logic still under `microservices`.
- `database/*/Battle*RuleBook.scala`: process-local caches in the database package.
- `database/world/BattleWorldRuleTable.scala`: table access and map JSON conversion are mixed.
- `objects/core/BattleAggregateState.scala`: top-level battle aggregate is still a composition root that imports microservice-owned state types.
- Moving IDs used inside microservice-owned state types that are also composed by `BattleAggregateState` can trigger Scala package/import cycles; `RoomId` and `ProjectileId` should stay core until the aggregate composition boundary is redesigned.

## Last Completed Tickets

- `BE-BATTLE-ARCH-REPORT-01`: replaced `problem/battle-architecture-full-report.md` with current-state architecture report.
- `BE-BATTLE-DECISION-GATE-01`: added `problem/battle-four-layer-decision-gate.md`.
- `BE-BATTLE-STRICT-OBJECTS-RETENTION-01`: removed standalone `objects/runtime/BattleRetentionRules.scala` and inlined retention helpers into current callers.
- `BE-BATTLE-STRICT-OBJECTS-AGGREGATE-UPDATE-02`: removed standalone `objects/runtime/BattleAggregateUpdateRules.scala` and inlined `replacePlayer` into current callers.
- `BE-BATTLE-FOUR-LAYER-RATIONALITY-01`: added a documentation-only analysis of the requested strict four-layer battle route and decision points.
- `BE-BATTLE-FOUR-LAYER-MIGRATION-PLAN-01`: added a documentation-only migration sequence, invariants, and decision matrix for strict battle four-layer refactor.
- `BE-BATTLE-FOUR-LAYER-DECISION-REQUEST-01`: added the final A/B/C decision request needed before Scala migration continues.
- `BE-BATTLE-FIVE-LAYER-MICROSERVICES-DECISION-01`: recorded the user's revised target: battle keeps api/objects/routes/database and additionally owns recursive microservices.
- `BE-BATTLE-MICROSERVICES-RESULTS-01`: migrated results API, result objects, result apiTypes, and result database table files into `services/battle/microservices/results`.
- `BE-BATTLE-MICROSERVICES-ABILITIES-OBJECTS-01`: migrated ability rule/config objects from `services/battle/objects/abilities` into `services/battle/microservices/abilities/objects/abilities`.
- `BE-BATTLE-MICROSERVICES-ACTORS-OBJECTS-01`: migrated actor rule/input/lifecycle objects from `services/battle/objects/actors` into `services/battle/microservices/actors/objects/actors`.
- `BE-BATTLE-MICROSERVICES-COMBAT-OBJECTS-01`: migrated combat, weapon, and projectile objects from `services/battle/objects/{combat,weapon,projectile}` into `services/battle/microservices/combat/objects/{combat,weapon,projectile}`.
- `BE-BATTLE-MICROSERVICES-QUEUE-OBJECTS-01`: migrated queue state/runtime model/use-case command/id allocator objects from `services/battle/objects/queue` into `services/battle/microservices/queue/objects/queue`.
- `BE-BATTLE-MICROSERVICES-WORLD-OBJECTS-01`: migrated world geometry and map/movement rule ADTs from `services/battle/objects/world` into `services/battle/microservices/world/objects/world`.
- `BE-BATTLE-MICROSERVICES-ABILITIES-DOMAIN-OBJECTS-02`: migrated pickup and skill ADTs from `services/battle/objects/{pickup,skill}` into `services/battle/microservices/abilities/objects/{pickup,skill}`.
- `BE-BATTLE-MICROSERVICES-ACTORS-PLAYER-OBJECTS-02`: migrated player state/lifecycle/survival ADTs from `services/battle/objects/player` into `services/battle/microservices/actors/objects/player`.
- `BE-BATTLE-MICROSERVICES-PROJECTIONS-REPLAY-OBJECTS-01`: migrated battle replay frame ADTs from `services/battle/objects/replay` into `services/battle/microservices/projections/objects/replay`.
- `BE-BATTLE-MICROSERVICES-RUNTIME-OBJECTS-01`: migrated runtime rule/time/event factory objects and battle event ADTs from `services/battle/objects/{runtime,event}` into `services/battle/microservices/runtime/objects/{runtime,event}`.
- `BE-BATTLE-MICROSERVICES-SESSION-COMMAND-OBJECTS-01`: migrated battle command request/accepted/outcome ADTs from `services/battle/objects/command` into `services/battle/microservices/session/objects/command`.
- `BE-BATTLE-MICROSERVICES-APITYPES-01`: migrated command/state API codecs into session microservice and queue/room/shared API codecs into queue microservice.
- `BE-BATTLE-API-PUBLIC-BOUNDARY-RESTORE-01`: restored seven public battle APIMessage planners, including `BattleCommandAPIMessage`, under `services/battle/api/{command,queue,room,state}` after an over-aggressive microservice downshift.
- `BE-REPLAY-APIMESSAGE-01`: added missing replay APIMessage endpoints outside battle.
- `BE-BATTLE-COMBAT-PROJECTILE-FACTORY-PURE-RULE-15`: moved projectile factory pure rule into `objects/combat`.
- `BE-BATTLE-APITYPES-BOUNDARY-02`: moved battle apiTypes out of objects into the owning API boundary: command/state at battle API, queue/room/shared at queue microservice API, results at results microservice API.
- `BE-BATTLE-ABILITIES-RULES-SERVICES-01`: moved ability execution rules from abilities objects into abilities services; config/value ADTs stayed in objects.
- `BE-BATTLE-ACTORS-RULES-SERVICES-01`: moved input/lifecycle execution rules from actors objects into actors services; player state ADTs stayed in objects.
- `BE-BATTLE-COMBAT-RULES-SERVICES-01`: moved projectile execution rules from combat objects into combat services; weapon/projectile state ADTs stayed in objects.
- `BE-BATTLE-RUNTIME-RULES-SERVICES-01`: moved event factory, replay frame recorder, and time rules from runtime objects into runtime services; event/replay ADTs stayed in objects.
- `BE-BATTLE-WORLD-GEOMETRY-SERVICES-01`: moved geometry execution helper from world objects into world services; world rule config stayed in objects.
- `BE-BATTLE-QUEUE-RUNTIME-MODEL-SERVICES-01`: moved queue runtime state, room lifecycle, ticket record, snapshots, and ID allocator from queue objects into queue services; queue state/use-case command objects stayed in objects.
- `BE-BATTLE-RESULTS-API-DATABASE-LEAK-01`: added a results service boundary and changed result APIMessage planners to call service instead of `BattleResultTable` directly.
- `BE-BATTLE-STATE-QUERY-API-BOUNDARY-01`: moved `BattleStateReadQuery` from top-level battle objects into the state API boundary; top-level battle objects now keep six shared/core files.
- `BE-BATTLE-COMMAND-DECODE-ERROR-API-BOUNDARY-01`: moved command request field/decode error ADT into `services/battle/api/command` and removed command-only error branches from shared `BattleAPIRequestError`.
- `BE-BATTLE-STATE-DECODE-ERROR-API-BOUNDARY-01`: moved state read decode error ADT into `services/battle/api/state` and removed state-specific message mapping from shared `BattleAPIRequestError`.
- `BE-BATTLE-RESULTS-DECODE-ERROR-API-BOUNDARY-01`: moved result record decode error ADT into `microservices/results/api/results` and removed results-only error branches from shared `BattleAPIRequestError`.
- `BE-BATTLE-QUEUE-DECODE-ERROR-API-BOUNDARY-01`: moved remaining queue/room request decode error ADT into `microservices/queue/api/shared` and deleted top-level `BattleAPIRequestError.scala`.
- `BE-BATTLE-COMBAT-ENUMS-OWNERSHIP-01`: moved combat-owned enums `WeaponKind`, `ProjectileKind`, and `ProjectileTerminalReason` from top-level `BattleEnums.scala` into `microservices/combat/objects`.
- `BE-BATTLE-ABILITIES-ENUMS-OWNERSHIP-01`: moved ability-owned enums `SkillKind`, `SkillOutcomeStatus`, `SkillOutcomeReason`, and `PickupKind` from top-level `BattleEnums.scala` into `microservices/abilities/objects`.
- `BE-BATTLE-SESSION-COMMAND-ENUMS-OWNERSHIP-01`: moved session command result enums `BattleCommandStatus` and `BattleCommandReason` into `microservices/session/objects/command`; removed their top-level package-object re-export to avoid a Scala package export cycle.
- `BE-BATTLE-RUNTIME-EVENT-ENUMS-OWNERSHIP-01`: moved runtime event enum `BattleEventKind` into `microservices/runtime/objects/event`; removed runtime event state re-exports that caused a package-object compile cycle.
- `BE-BATTLE-QUEUE-PHASE-ENUMS-OWNERSHIP-01`: moved queue-owned `MatchmakingRoomPhase` into `microservices/queue/objects/queue` and tightened queue API/service/test imports.
- `BE-BATTLE-CORE-ENUMS-01`: split remaining battle-core enums `BattleMode`, `BattlePhase`, and `BattleArtifactStatus` into `objects/core` and deleted top-level `BattleEnums.scala`.
- `BE-BATTLE-SESSION-API-SURFACE-01`: moved command/state APIMessage planners, codecs, decode errors, and state query DTO from top-level `services/battle/api` into `microservices/session/api`.
- `BE-BATTLE-PACKAGE-EXPORT-QUEUE-01`: removed queue object re-exports from `services/battle/objects/package.scala` and changed main/test callers to import queue-owned objects directly.
- `BE-BATTLE-PACKAGE-EXPORT-COMBAT-01`: removed combat object re-exports from `services/battle/objects/package.scala` and changed runtime/test callers to import combat-owned weapon/projectile objects directly.
- `BE-BATTLE-PACKAGE-EXPORT-ACTORS-01`: removed actor object re-exports from `services/battle/objects/package.scala` and changed replay/projection/runtime/test callers to import actor-owned player/participant objects directly.
- `BE-BATTLE-PACKAGE-EXPORT-ABILITIES-01`: removed ability object re-exports from `services/battle/objects/package.scala` and changed runtime/world/test callers to import ability-owned pickup/skill objects directly.
- `BE-BATTLE-PACKAGE-EXPORT-PROJECTIONS-01`: removed projection replay-frame object re-exports from `services/battle/objects/package.scala` and changed runtime callers to import projection-owned replay frame objects directly.
- `BE-BATTLE-PACKAGE-EXPORT-RESULTS-01`: removed result object re-exports from `services/battle/objects/package.scala` and changed projection/test callers to import result-owned record/projection objects directly.
- `BE-BATTLE-TOPLEVEL-CORE-AUDIT-01`: audited top-level battle API/objects after export cleanup; `api` is empty, `objects` has only `package.scala` plus `core/*`, and no package-level microservice re-exports remain.
- `BE-BATTLE-QUEUE-IDS-OWNERSHIP-01`: moved queue-owned `TicketId` and `QueueRequestId` from battle core into `microservices/queue/objects/queue`; kept `RoomId` in battle core because `BattleAggregateState` uses it as a battle-level aggregate key and moving it into queue creates a core/queue compile cycle.
- `BE-BATTLE-RESULT-IDS-OWNERSHIP-01`: moved result-owned `BattleResultId` and `BattleResultListLimit` from battle core into `microservices/results/objects/result`; updated results API/object imports.
- `BE-BATTLE-RUNTIME-EVENT-ID-OWNERSHIP-01`: moved runtime-owned `BattleEventId` from battle core into `microservices/runtime/objects/event`; updated runtime event factory/state and contract test imports.
- `BE-BATTLE-PICKUP-ID-OWNERSHIP-01`: moved ability pickup-owned `PickupId` from battle core into `microservices/abilities/objects/pickup`; updated world database, replay projection, and test imports.
- `BE-BATTLE-SLOW-FIELD-ID-OWNERSHIP-01`: moved ability skill-owned `SlowFieldId` from battle core into `microservices/abilities/objects/skill`; updated slow-field state and skill command imports.
- `BE-BATTLE-COMBAT-WEAPON-SCALARS-01`: moved combat weapon-owned `AmmoCount`, `BattleWeaponHeat`, and `BattleWeaponHeatRatePerSecond` from battle core into `microservices/combat/objects/weapon`; updated combat, actor runtime, session state API, database, and test imports.
- `BE-BATTLE-ACTOR-APPEARANCE-KEYS-OWNERSHIP-01`: moved actor/player-owned `BattleAvatarKey` and `BattleSkinKey` from battle core into `microservices/actors/objects/player`; updated queue API/object/service and contract test imports.
- `BE-BATTLE-RESULT-PRESENTATION-VALUES-OWNERSHIP-01`: moved result/projection-owned `BattlePlacement`, `RatingDelta`, `BattleResultLabel`, `BattleHighlightLine`, `BattlePlayersLine`, and `BattleTimelineHint` from battle core into `microservices/results/objects/result`; updated results, projections, replay, and contract test imports.
- `BE-BATTLE-ACTOR-STATS-OWNERSHIP-01`: moved actor/player-owned `Score` and `KillCount` from battle core into `microservices/actors/objects/player`; updated combat impact, session bootstrap, projection, result, replay, and contract test imports.
- `BE-BATTLE-ACTOR-VITALS-OWNERSHIP-01`: moved actor/player-owned `HitPoints` and `Stamina` from battle core into `microservices/actors/objects/player`; updated runtime rules/tables, abilities healing, combat projectile terminals, replay frames, and runtime contract tests.
- `BE-BATTLE-COMBAT-DAMAGE-OWNERSHIP-01`: moved combat-owned `Damage` from battle core into `microservices/combat/objects/combat`; updated combat rule definitions, projectile state, combat table, and runtime contract test imports.
- `BE-BATTLE-QUEUE-CAPACITY-OWNERSHIP-01`: moved queue-owned `BattleCapacity` from battle core into `microservices/queue/objects/queue`; updated queue room/session bootstrap imports.

## Verification History

Documentation-only checks:

```text
git diff --check -- problem\battle-four-layer-decision-gate.md problem\battle-architecture-full-report.md
```

Result:

```text
passed with CRLF warning only
```

Latest backend verification before the documentation-only decision work:

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed
```

Latest backend verification after `BE-BATTLE-STRICT-OBJECTS-RETENTION-01`:

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed
```

Latest backend verification after `BE-BATTLE-QUEUE-DECODE-ERROR-API-BOUNDARY-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed
```

Latest backend verification after `BE-BATTLE-ABILITIES-ENUMS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-SESSION-COMMAND-ENUMS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-RUNTIME-EVENT-ENUMS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-QUEUE-PHASE-ENUMS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-SESSION-API-SURFACE-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-QUEUE-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-COMBAT-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-ACTORS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-ABILITIES-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-PROJECTIONS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-PACKAGE-EXPORT-RESULTS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-QUEUE-IDS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Follow-up verification after the aborted `ProjectileId` downshift attempt:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; clean compile was needed after incremental compile reported a stale cyclic import error.
```

Latest backend verification after `BE-BATTLE-RESULT-IDS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-RUNTIME-EVENT-ID-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; clean compile was used because incremental compile again reported the stale pickup import cycle.
```

Latest backend verification after `BE-BATTLE-PICKUP-ID-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-SLOW-FIELD-ID-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-COMBAT-WEAPON-SCALARS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed; sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning
```

Latest backend verification after `BE-BATTLE-ACTOR-APPEARANCE-KEYS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
passed; initial sbt invocation from repository root failed because build.sbt is under backend/; rerun from backend/ passed. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-RESULT-PRESENTATION-VALUES-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
passed; diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-ACTOR-STATS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
passed; diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-ACTOR-VITALS-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
passed; diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-COMBAT-DAMAGE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
passed; diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-QUEUE-CAPACITY-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
```

Result:

```text
initial clean compile failed because BattleQueueRuntimeModel used an explicit import list missing the moved BattleCapacity; fixed in-scope. Rerun passed. Contract runner passed. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-ACTOR-RATING-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.objects(\.core)?\.Rating|export _root_\.services\.battle\.objects\.core\.Rating|final case class Rating" backend/src/main/scala/services/battle backend/src/main/scala/services/replay backend/src/test/scala -n
```

Result:

```text
passed. Rating is now declared only in services.battle.microservices.actors.objects.player; RatingDelta remains in results. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-ACTORS-DATABASE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.database\.actors|package services\.battle\.database\.actors" backend/src/main/scala backend/src/test/scala -n
```

Result:

```text
passed. Actor/bot rule persistence moved from services.battle.database.actors to services.battle.microservices.actors.database. Old actor database package references are gone. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-ABILITIES-DATABASE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.database\.abilities|package services\.battle\.database\.abilities" backend/src/main/scala backend/src/test/scala -n
```

Result:

```text
passed. Skill and pickup rule persistence moved from services.battle.database.abilities to services.battle.microservices.abilities.database. Old abilities database package references are gone. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-COMBAT-DATABASE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.database\.combat|package services\.battle\.database\.combat" backend/src/main/scala backend/src/test/scala -n
```

Result:

```text
passed. Weapon/projectile rule persistence moved from services.battle.database.combat to services.battle.microservices.combat.database. Old combat database package references are gone. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-RUNTIME-DATABASE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" clean compile
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.database\.runtime|package services\.battle\.database\.runtime" backend/src/main/scala backend/src/test/scala -n
```

Result:

```text
runtime rule persistence moved from services.battle.database.runtime to services.battle.microservices.runtime.database. Old runtime database package references are gone. clean compile timed out while two existing runMain route.BackendHttp4sApp Java processes were active; non-clean compile passed. Contract runner passed. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-WORLD-DATABASE-OWNERSHIP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check -- focused changed files
rg "services\.battle\.database\.|package services\.battle\.database\." backend/src/main/scala backend/src/test/scala -n
Get-ChildItem -Recurse -File -Path backend/src/main/scala/services/battle/database
```

Result:

```text
passed. World/map/collision rule persistence moved from services.battle.database.world to services.battle.microservices.world.database. No services.battle.database.* imports/packages remain, and top-level battle/database has no source files. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Implementation note:

```text
BattleResultRecord.scala, BattleResultCommands.scala, and some projection files contain invalid UTF-8 bytes in legacy comments; import cleanup in those files was done as byte-preserving ASCII import replacement instead of apply_patch because apply_patch cannot parse the files.
```

Latest backend verification after `BE-BATTLE-IO-PROJECTION-FLOW-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/projections/services backend/src/main/scala/services/battle/microservices/results/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/projections backend/src/main/scala/services/battle/microservices/results -g "*.scala"
```

Result:

```text
passed. Finish projection planning/replay/mail/result validation now composes through IO. diff check passed with LF/CRLF warnings only. The effect-boundary scan only found the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-ACTOR-RULES-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services -g "*.scala"
rg -n "unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|AtomicReference|ThreadLocal|withMapIO|inMapIO" backend/src/main/scala -g "*.scala"
```

Result:

```text
passed. Actor input normalization, bot control decisions, player movement/stamina, and lifecycle winner selection now compose through IO. diff check passed with LF/CRLF warnings only. Focused and broad unsafe/global-state scans returned no matches. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-EXTRACTION-RULES-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
rg -n "clearDeadPlayerRuntimeValue|var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/extraction/services backend/src/main/scala/services/battle/microservices/runtime/services -g "*.scala"
rg -n "unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|AtomicReference|ThreadLocal|withMapIO|inMapIO" backend/src/main/scala -g "*.scala"
```

Result:

```text
passed. Extraction initialization and runtime objective flow now compose through IO, including gas damage, loot cache progress/scoring, extraction status updates, and gas-death lifecycle cleanup. Focused and broad unsafe/global-state scans returned no matches. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-RUNTIME-REPLAY-SLOW-PICKUP-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala -g "*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Slow field advancement, pickup respawn advancement, and replay frame update/append/capture now return IO and are sequenced directly by runtime step/finalization/finish/session creation. Focused scan over changed files returned no matches. Battle microservices scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-EVENT-TIME-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/combat/services backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/extraction/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Runtime event factory, pickup/combat event call sites, BattleTimeRules, actor timer/stamina/heat advancement, extraction gas damage, slow fields, pickup respawn, and projectile TTL now compose through IO. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-COMBAT-WEAPON-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/extraction/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Current weapon lookup, fire predicates, runtime fire command seq, weapon update/reload finish, weapon index clamp, and heat-resource helper now return IO and are composed by primary fire, requested reload, held fire, actor timer, and bot weapon checks. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PROJECTILE-FACTORY-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala
rg "BattleProjectileFactoryRules\\.(weaponProjectiles|resolvePistolShot)" backend/src/main/scala backend/src/test/scala
```

Result:

```text
passed. Projectile factory state/id/birth-position construction now returns IO and is composed by pistol, rocket, shotgun, and gatling weapon fire. Non-pistol projectile append now uses the post-recoil replaced state. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PROJECTILE-MOTION-TARGETING-TERMINAL-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Projectile motion, player targeting, terminal construction, terminal append, and terminal retention now compose through IO in projectile runtime/impact. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-WEAPON-FIRE-PRIVATE-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala
```

Result:

```text
passed. Weapon fire recoil, heat charge, and projectile birth offset helpers now return IO and are bound by magazine/heat fire paths. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-SKILL-COMMAND-PRIVATE-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala
```

Result:

```text
passed. Skill availability, outcome construction, skill runtime updates, blink/dash helpers, dash motion destination, and command-local player replacement now compose through IO. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PICKUP-COLLECTION-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala
```

Result:

```text
passed. Pickup contact resolution, player update, event kind/message selection, pickup consumption, player/pickup replacement, and event retention now compose through IO inside collectPickups. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-SESSION-COMMAND-ACCEPTANCE-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/session/services/BattleCommandAcceptanceFactory.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionPreparationRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleAdvanceRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala
```

Result:

```text
passed. Ignored command acceptance, ignored reason, battle-not-found read/submission wrappers, successful state reads, command submission updates, projection preparation, and stored-battle advance result assembly now compose through IO. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-SESSION-FINISH-PROJECTION-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionStatusRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionCompletionRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala
```

Result:

```text
passed. Session finish-projection artifact status, finish-projection status, ready-or-failed selection, and stored-battle completion now return IO. completeProjectionIO computes completion outside Ref.modify and commits with compare-and-retry. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-SESSION-INITIALIZATION-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/session/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices/session/services -g "*.scala"
```

Result:

```text
passed. Battle map selection, started-at selection, bootstrap seats, command ownership map creation, and stored-battle construction now compose through IO in session initialization. diff check passed with LF/CRLF warnings only. Focused session unsafe/global-state scan returned no matches. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-QUEUE-JOIN-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueJoinRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRequestReuseRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Queue join normalization, queue request reuse, room selection, join draft construction, queue-request update, room/ticket/player ID allocation, and join state assembly now compose through IO. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-QUEUE-LEAVE-HEARTBEAT-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Queue leave transition, heartbeat room lookup/match/touch/update, participant normalization, room lifecycle start/finish, session bootstrap, queue/room snapshot conversion, ticket snapshot lookup, and active battle session lookup now compose through IO. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-QUEUE-RUNTIME-MODEL-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Queue runtime state update helpers, queue room lifecycle accessors, queue room lifecycle finish transition, queue snapshot accessors, and queue ID allocator methods now return IO and are bound by queue service/runtime helper call sites. diff check passed with LF/CRLF warnings only. Battle microservices unsafe/global-state scan only reports the pre-existing JDBC bind index var in BattleResultTable.scala. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-RESULTS-DATABASE-BINDING-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/results/database/BattleResultTable.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. The result table list-query JDBC binding no longer uses a mutable bind index; it now derives immutable indexed SQL bindings. The battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-SESSION-FAILURE-FORMATTER-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/results/database/BattleResultTable.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFailureMessageFormatter.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Projection failure message formatting now returns IO and is bound by BattleStateService failure recovery. The first parallel Test/compile returned success but printed a transient file-in-use message, so Test/compile was rerun alone and passed cleanly. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-WORLD-MOTION-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerRuntimeRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala
rg -n "IO\\.pure\\(BattleMotionRules\\.normalizeMovement|IO\\.pure\\([^\\r\\n]*findMotionDestination|normalizeMovement: BattleVector2 => BattleVector2" backend/src/main/scala/services/battle/microservices -g "*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleMotionRules.normalizeMovement, findMotionDestination, and stepped motion resolution now return IO; actor movement, bot movement probes, weapon recoil, projectile factory/motion, and command-application call sites bind those helpers directly. The old pure normalizeMovement adapter signatures and IO.pure(BattleMotionRules.normalizeMovement(...)) wrappers are gone. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-WORLD-COLLISION-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala
rg -n "IO\\.pure\\(BattleArenaCollision\\.|isInWorld: BattleVector2 => Boolean|isInWorldWithRadius: \\(BattleVector2, Radius\\) => Boolean|collidesWithArenaObstacles: \\(BattleVector2, Radius\\) => Boolean|isBlockedPoint: BattleVector2 => Boolean|if hasArenaLineOfSight|if canPlayerOccupy|firstSegmentObstacleEnterT\\([^\\r\\n]*\\)\\.isEmpty" backend/src/main/scala/services/battle/microservices -g "*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleArenaCollision world-exit, obstacle-enter, AABB, circle-hit, world-boundary, line-of-sight, occupancy, obstacle collision, and clamp helpers now return IO. Motion stepping, skill command environments, bot visibility/cover checks, and projectile hit/block detection bind those helpers directly. Old pure collision adapter signatures and IO.pure(BattleArenaCollision...) wrappers are gone. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-WORLD-GEOMETRY-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleGeometry.scala backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerRuntimeRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala backend/src/main/scala/services/battle/microservices/extraction/services/BattleExtractionRuntimeRules.scala
rg -n "IO\\.pure\\([^\\r\\n]*(distanceBetween|vectorLength|pointAtSegmentT|add\\(|scale\\(|subtract\\(|perpendicular\\(|clampDouble)|filter\\([^\\r\\n]*(distanceBetween|vectorLength)|sortBy\\([^\\r\\n]*distanceBetween|minByOption\\([^\\r\\n]*distanceBetween|if vectorLength|if distanceBetween" backend/src/main/scala/services/battle/microservices -g "*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleGeometry vector helpers now return IO. World motion, actor/player/bot movement, skill validation, pickup selection, projectile factory/motion/targeting/impact, weapon recoil, and extraction objective checks now bind geometry helpers directly. Old pure geometry use in filters/sorts/conditionals and IO.pure(geometry(...)) wrappers are gone. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-MICROSERVICES-SCALAR-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala backend/src/main/scala/services/battle/microservices/results/services/BattleResultService.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionTimeRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFrameTimelineRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionMailFactory.scala
rg -n "targetScore\\([^\\r\\n]*\\): Double|orbitDirection\\([^\\r\\n]*\\): Double|rotate\\([^\\r\\n]*\\): BattleVector2|battleInputEnvironment\\([^\\r\\n]*\\): BattleInputEnvironment|failureMessage\\([^\\r\\n]*\\): String|nonEmpty\\([^\\r\\n]*\\): Option\\[String\\]|clampElapsed\\([^\\r\\n]*\\): Long|replaySourcePath\\([^\\r\\n]*\\): String|urlEncode\\([^\\r\\n]*\\): String|signed\\([^\\r\\n]*\\): String" backend/src/main/scala/services/battle/microservices -g "**/services/*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Bot target scoring/orbit/rotation, runtime command input environment construction, result loadout normalization, projection failure-message formatting, projection elapsed clamping, replay source path encoding, URL encoding, and signed rating text helpers now return IO and are bound at their service call sites. Focused old-signature scan returns no matches. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PROJECTION-REPLAY-RENDERER-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFramesJsonRenderer.scala
rg -n "heroFramePayload\\([^\\r\\n]*\\): BattleReplayHeroPayload|projectileFramePayload\\([^\\r\\n]*\\): BattleReplayProjectilePayload|pickupFramePayload\\([^\\r\\n]*\\): BattleReplayPickupPayload|eventMessages\\([^\\r\\n]*\\): Vector\\[String\\]|replayDisplayName\\([^\\r\\n]*\\): String|replayPickupKind\\([^\\r\\n]*\\): String|vectorPayload\\([^\\r\\n]*\\): BattleReplayVectorPayload|\\.map\\(heroFramePayload\\)|\\.map\\(projectileFramePayload\\)|\\.map\\(pickupFramePayload\\)" backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFramesJsonRenderer.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleReplayFramesJsonRenderer.render and its private replay payload/string helpers now return IO and are bound with traverse/flatMap. The focused old-signature scan returns no matches. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PROJECTION-PLANNER-VALUE-HELPERS-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionReplayRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala
rg -n "def toVector: Vector\\[BattleSettlement\\]|def map\\[[^\\r\\n]*\\]: Vector|def foreach\\[[^\\r\\n]*\\]: Unit|def find\\([^\\r\\n]*\\): Option\\[BattleSettlement\\]|def ratingBefore\\([^\\r\\n]*\\): Rating|def fromRatings\\([^\\r\\n]*\\): BattlePreviousRatings|settlements\\.toVector\\.foldLeft|val ratingBefore = previousRatings\\.ratingBefore|\\.map\\(BattlePreviousRatings\\.fromRatings\\)" backend/src/main/scala/services/battle/microservices/projections/services -g "*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleSettlements collection wrappers and BattlePreviousRatings rating lookup/construction now return IO; replay owner selection, replay settlement rendering, artifact writing, and previous-rating loading bind them explicitly. The focused old-signature/direct-sync-call scan returns no matches. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-PASSIVE-FACTORY-AUDIT-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerLifecycleRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala
rg -n --pcre2 "\\)\\s*:\\s*(?!IO\\[|Resource\\[|Unit\\b|Nothing\\b)[A-Za-z_][A-Za-z0-9_\\[\\],\\. ]*\\s*(?:=|$)" backend/src/main/scala/services/battle/microservices -g "**/services/*.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. Removed the last non-factory service workflow helpers found by the precise return-type scan: actor lifecycle dead-player cleanup and projection artifact outcome combination now compose through IO. The only remaining non-IO service definitions reported by the scan are pure apply constructors. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Latest backend verification after `BE-BATTLE-IO-API-DECODE-BOUNDARY-AUDIT-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/results/api/results/BattleResultApiTypes.scala backend/src/main/scala/services/battle/microservices/results/api/results/BattleResultResponseMapping.scala backend/src/main/scala/services/battle/microservices/results/api/BattleResultListAPIMessage.scala backend/src/main/scala/services/battle/microservices/results/api/BattleResultRecordAPIMessage.scala
rg -n "def (fromRecord|fromList|fromRecords)|BattleResultRecordResponse\\.fromRecord|BattleResultListResponse\\.from(List|Records)" backend/src/main/scala/services/battle/microservices/results/api/results/BattleResultApiTypes.scala backend/src/main/scala/services/battle/microservices/results/api/BattleResultListAPIMessage.scala backend/src/main/scala/services/battle/microservices/results/api/BattleResultRecordAPIMessage.scala
rg -n "Service|Table|withConnection|IO\\[|IO\\.|plan\\(|Planner|Mapping" backend/src/main/scala/services/battle/microservices -g "**/*ApiTypes.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

Result:

```text
passed. BattleResultApiTypes no longer owns result response construction; it now contains response DTOs and Circe encoders only. BattleResultResponseMapping performs result response construction as IO at the API boundary, and result API messages bind it explicitly. Focused ApiTypes service/planning scan returns no matches. Battle microservices unsafe/global-state scan returns no matches. diff check passed with LF/CRLF warnings only. sbt/Scala runtime still emits the existing terminally deprecated sun.misc.Unsafe warning.
```

Completion audit after `BE-BATTLE-IO-FINAL-COMPLETION-AUDIT-01`:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
rg -n --pcre2 "\\)\\s*:\\s*(?!IO\\[|Resource\\[|Unit\\b|Nothing\\b)[A-Za-z_][A-Za-z0-9_\\[\\],\\. ]*\\s*(?:=|$)|def\\s+\\w+\\s*:\\s*(?!IO\\[|Resource\\[|Unit\\b|Nothing\\b)[A-Za-z_][A-Za-z0-9_\\[\\],\\. ]*\\s*=" backend/src/main/scala/services/battle/microservices -g "**/services/*.scala"
rg -n "Service|Table|withConnection|IO\\[|IO\\.|plan\\(|Planner|Mapping" backend/src/main/scala/services/battle/microservices -g "**/*ApiTypes.scala"
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
rg -n "Kafka|kafka|fs2|Queue\\[|Deferred\\[|Ref\\[|Resource\\[" backend/src/main/scala/services/battle backend/src/main/scala/BackendRuntime.scala backend/src/main/scala/route/BackendHttp4sApp.scala
```

Result:

```text
passed. compile and Test/compile passed; Test/compile was rerun alone after a parallel file-use warning and then passed cleanly. The precise non-IO service return scan reports only pure apply constructors. ApiTypes service/planning scan returns no matches. Battle microservices unsafe/global-state scan returns no matches. Runtime allocation evidence shows Ref/Resource in BackendRuntime, BackendHttp4sApp, BattleDynamicRuleBook, BattleStateService, and BattleQueueService. Kafka/fs2 were not introduced; distributed event adapters remain documented as optional separate work.
```

## Next Ticket

```text
No required next IO-monadization ticket remains for the active backend battle microservices objective. Optional future work is a separate distributed event adapter ticket if Kafka becomes a product requirement.
```

Verification:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" Test/compile
git diff --check -- backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala
rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"
```

## Latest Frontend Battle Ticket

Ticket `ZM-SKILL-HUD-STABILITY`:

- Fixed Critical input retention in `frontend/src/runtime/battle/local/input/BattleAuthoritativePlayerCommandTap.ts`; Ctrl and selected Q/E/R Critical now survive the scene/uplink command tap.
- Extended `scripts/battle-zombie-browser-dual-client-smoke.ps1` to press Ctrl in a real browser session and assert Critical stamina spend plus active/cooldown backend state; it also retries Dash on the E slot and checks the HUD cooldown/readbar DOM state.
- Verified `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run demo:zombie-browser`, and `npm run audit:battle-content`.
- Gas final shrink currently parses as `winter-hunt-v1: 220 -> 0` and `fall-hunt-v1: 180 -> 0`.

Ticket `ZM-MULTIPLAYER-STABILITY-AUDIT-01`:

- Confirmed the main world orange gas overlay is already in `frontend/src/runtime/battle/microservices/extraction/components/BattleExtractionObjectiveOverlay/functions/renderBattleExtractionObjectiveOverlay.ts`; active gas outside-circle fill uses alpha `0.5`.
- Verified `npm run demo:zombie-multiplayer`: 2 humans enter the same winter room, capacity 12, 10 bot seats, elapsed/tick advance, and at least one zombie bot moves.
- Verified backend contracts from `backend/` with `sbt "-Dsbt.server.forcestart=true" "Test / runMain route.contract.BackendContractTestRunner"`; contracts cover winter/autumn 12-player capacity, winter boss/plain zombie loadouts, zombie contact elimination, gas damage, held-fire projectiles, timer/tick progress, and bot movement.
- Frontend typecheck `npx tsc -p frontend/tsconfig.json --noEmit` passed.
- Plain root-level `sbt ...` still fails because the repo root has no `build.sbt`; backend checks must run from `backend/`.

Ticket `ZM-BROWSER-SOAK-SMOKE-01`:

- Added and verified a 20-second dual-browser zombie soak path through `scripts/battle-zombie-browser-dual-client-smoke.ps1` and `npm run demo:zombie-soak`.
- Soak evidence: both browser clients entered the same battle, total players stayed at 12 with 10 bots, HUD timers changed, backend elapsed/tick advanced, left-click fire produced projectile evidence, Critical consumed stamina and entered active/cooldown, Dash HUD cooldown/readbar was observed, gas radius shrank, and at least one zombie bot kept moving.
- Current map evidence: `winter-hunt-v1` parses as world `12288x12288`, 12 hero spawns, 84 terrain patches, 476 obstacles, and 82 buildings; no runtime reference to winter `mapview` was found. `fall-hunt-v1` parses as world `8192x8192` with 12 hero spawns.
- Current verification: `npm run demo:zombie-soak`, backend `sbt "-Dsbt.server.forcestart=true" "Test / runMain route.contract.BackendContractTestRunner"` from `backend/`, `npx tsc -p frontend/tsconfig.json --noEmit`, `npm run audit:battle-content`, and focused `git diff --check` passed. Diff check still reports LF/CRLF warnings only.
