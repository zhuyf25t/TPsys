# Backend battle IO monadization plan

## Current decision

The battle backend migration should not keep pure-looking service functions that hide state, time, repositories, rule-book mutation, or thread-local context. Service/runtime boundaries should return `IO[...]`, and long-lived process state should be allocated through cats-effect primitives.

`apiTypes` should stay a wire boundary only: encode, decode, and small DTO conversion needed for HTTP/API contracts. It should not own runtime planning, persistence, simulation rules, or service orchestration. API planners belong in the API layer only when they compose request decode, service calls, and response encode. Business/service flow belongs in service/runtime/projection boundaries.

Kafka or another distributed log should not be introduced inside this migration wave. If needed, it should be a separate adapter ticket with dependency review, producer/consumer resource lifecycle, retry semantics, and contract tests.

## Invariants

- No `var + synchronized` for battle service state.
- Use `Ref[IO, A]` for in-process mutable state, `Deferred[IO, A]` for one-shot coordination, `Queue[IO, A]` for event flow/backpressure, and `Resource[IO, A]` for runtime acquisition/release.
- No `unsafeRunSync` in `backend/src/main/scala`.
- Keep pure domain value transforms pure only when they are local, deterministic, and have no hidden time/state/context access.
- Any function that touches time, random IDs, repositories, rule-book mutation, port calls, state caches, thread-local map context, or artifact publication returns `IO`.
- The production runtime should allocate effectful services in `IO`/`Resource`; tests may bridge to sync only at the test runner edge.

## Ticket BE-BATTLE-IO-STATE-REF-01

Goal: replace `InMemoryBattleStateService` lock/var state with `Ref[IO, Map[BattleId, StoredBattle]]`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`
- `backend/src/main/scala/BackendRuntime.scala`
- `backend/src/main/scala/route/BackendHttp4sApp.scala`
- focused contract helper construction in `BattleRuntimeContractSuites.scala`

Expected change:

- `currentState` and `acceptCommand` keep IO contracts.
- Battle initialization, commit, and projection completion use `Ref.get`/`Ref.modify`.
- Production runtime construction returns `IO[BackendRuntime]` and exposes `Resource[IO, BackendRuntime]`.
- Remove production `unsafeRunSync`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile`
- `rg -n "unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala`
- `rg -n "synchronized|private var|var battles" backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`

## Next waves

1. Queue state: replace `InMemoryBattleQueueService` `AtomicReference` CAS loop with `Ref[IO, QueueRuntimeState]`. Done in the current wave; remaining queue work is to keep extracting pure transition helpers while the service shell stays effectful.
2. Combat weapon rules: ensure weapon rule-book reads and downstream fire/reload/pickup equipment flow return `IO`. Done in the current wave for `BattleWeaponRules`, `BattleWeaponFireRules`, held fire, input switch, and pickup equipment.
3. Runtime, bot, pickup, and skill rule-book reads: getter calls now return `IO` and their main service callers compose the values explicitly. Done for `BattleRuntimeRuleBook`, `BattleBotRuleBook`, `BattlePickupRuleBook`, and `BattleSkillRuleBook`.
4. Rule-book resources: replace process-global `AtomicReference` rule books with allocated runtime resources or explicit rule-set dependencies passed into services. Done for the battle runtime path: `BackendRuntime` loads `BattleDynamicRules`, allocates `BattleDynamicRuleBook` through `Ref[IO, BattleDynamicRules]`, and passes it through the session/runtime simulation path. The legacy `*RuleBook` objects were removed.
5. World rule-book IO access: add `BattleWorldRuleBook.worldIO`, `movementIO`, and `loadedMapIO`, then migrate runtime-facing direct reads first. Done for initial layout, extraction initial state, player movement, projectile speed factors, and runtime arena constants.
6. Runtime map context: remove `ThreadLocal` map context from world/runtime rules. Done for the battle runtime surface by introducing `BattleArenaContext` and `BattleArenaCatalog.contextFor(mapId): IO[BattleArenaContext]`; collision, motion, player, bot, weapon fire, projectile, command, and extraction rules now receive the context explicitly.
7. Lower runtime rules: continue converting ability, actor, extraction, and finish rules to `IO` only where they read effectful context or compose effectful subrules. Done for ability skill command application: blink/dash/freeze command application now returns `IO[CommandApplication]` and is sequenced by `BattleCommandApplicationRules` through `foldLeft(IO.pure(...))`. Actor runtime orchestration is also done for input normalization, bot control decisions, movement/stamina advancement, lifecycle winner selection, and command input environment propagation. Extraction runtime orchestration is done for initial gas state, gas zone advancement, gas damage, loot cache progress/scoring, extraction channel status, and objective predicates.
8. Lower runtime scan: continue auditing remaining non-IO service functions and keep pure local transforms pure when they do not read time/state/context/ports.
9. Projection flow: model finish projection as an `IO` program that can later publish to `Queue`/stream adapters. Done for finish projection planning, player/rating/result settlement construction, replay record/frame rendering, mail artifact creation, and result record validation/building; artifact persistence and publishing remain explicit ports/effects.
10. Runtime replay/slow/pickup helpers: remove runtime-step `IO.pure(sync service helper)` wrappers where the helper updates aggregate runtime state. Done for slow field advancement, pickup respawn advancement, and replay frame update/append/capture.
11. Runtime event/time helpers: convert runtime event creation and time/timer helpers into `IO` where they are service-level workflow. Done for event factory battle/pickup event construction, pickup/combat event call sites, elapsed time calculations, rate deltas, and timer decrement helpers.
12. Combat weapon helpers: convert remaining runtime-facing weapon service predicates, current weapon lookup, generated runtime fire command sequence, reload finishing, current weapon update, and weapon index/resource helpers into `IO`. Done for the main combat/actor/held-fire call sites.
13. Combat projectile factory helpers: convert projectile state/id/birth-position construction into `IO` and propagate through weapon fire. Done for pistol, rocket, shotgun, and gatling projectile creation paths.
14. Combat projectile runtime helpers: convert projectile motion, targeting, terminal construction, and terminal retention helpers into `IO` and propagate through projectile runtime/impact. Done for the projectile advance and impact paths.
15. Combat weapon-fire private helpers: convert recoil, heat charging, and projectile birth offset workflow helpers into `IO`. Done inside `BattleWeaponFireRules`.
16. Ability command helpers: convert skill availability, command outcomes, skill runtime updates, blink/dash helpers, and command-state player replacement into `IO`. Done for skill command application and runtime command orchestration.
17. Pickup collection helpers: split pickup contact, player update, event kind/message, pickup consumption, player/pickup replacement, and event retention into `IO` helpers. Done inside `BattlePickupRules`.
18. Session command/read helpers: convert command acceptance, state read/submission wrappers, stored-battle advance result assembly, projection preparation, projection completion/status, and session initialization helpers into `IO`. Done for the session service path.
19. Queue join helpers: convert queue join normalization, request reuse, room selection, join draft construction, queue-request update, and local ID allocation helpers into `IO`. Done for the queue join path.
20. Queue leave/heartbeat/session helpers: convert queue leave transition, heartbeat matching/touching, room lifecycle transitions, room/session snapshots, and active session lookup into `IO`. Done for queue leave/status/room/heartbeat/session paths.
21. Queue runtime model helpers: convert queue runtime state update helpers, queue room lifecycle accessors, queue room lifecycle transitions, and ID allocator helpers into `IO`. Done inside the queue service boundary.
22. Results database binding helpers: remove mutable JDBC bind index state from result persistence. Done by replacing `var index` with immutable indexed bindings.
23. Session failure formatting helper: convert projection error message formatting to `IO`. Done in `BattleFailureMessageFormatter`.
24. World motion helpers: convert motion normalization and stepped destination resolution to `IO`, then propagate through actor, bot, weapon fire, projectile factory, projectile motion, and command-application call sites. Done for `BattleMotionRules`.
25. World collision helpers: convert collision, world-boundary, line-of-sight, and obstacle predicates to `IO`, then propagate through motion stepping, skill command environments, bot visibility/cover checks, and projectile block detection. Done for `BattleArenaCollision`.
26. World geometry helpers: convert vector math helpers to `IO`, then propagate through world motion, actor/bot movement, skill commands, pickups, projectile factory/motion/targeting/impact, and extraction objective checks. Done for `BattleGeometry`.
27. Residual scalar service helpers: convert remaining small synchronous service helpers in actor bot scoring/direction, runtime command environment construction, result loadout normalization, projection failure messages, projection time clamping, and projection mail path/sign formatting. Done in the current cleanup wave.
28. Replay renderer payload helpers: convert replay frame payload construction, display-name selection, pickup-kind labels, vector payloads, and event-message selection into `IO`. Done for `BattleReplayFramesJsonRenderer`.
29. Projection planner value helpers: audit the remaining projection planner collection/rating wrappers and either keep documented passive value accessors or convert service-bound wrappers to `IO`. Done for `BattleSettlements` and `BattlePreviousRatings`.
30. Remaining passive/factory audit: pure `apply` constructors, API encode/decode helpers, case-class field accessors, and database row/bind adapters are not service workflow and should stay outside this IO wave unless they allocate resources or perform external effects. Done for the service-layer scan; the only remaining non-IO service definitions are pure `apply` constructors.
31. API decode/encode boundary audit: keep `apiTypes` focused on DTOs plus Circe encoders/decoders and move response construction/planning helpers out to API boundary programs. Done for results response mapping.
32. Distributed event adapter: if Kafka is required, add it as a separate adapter after the in-process IO graph is stable.

## Completed ticket BE-BATTLE-IO-PROJECTION-FLOW-01

Goal: convert the finish projection/result service flow from synchronous service helpers into an `IO` program without introducing Kafka or a new stream dependency.

Boundary:

- `backend/src/main/scala/services/battle/microservices/projections/services`
- `backend/src/main/scala/services/battle/microservices/results/services`

Result:

- `DefaultBattleFinishProjector.project` now sequences human-player selection, previous-rating reads, projection planning, and artifact writes as `IO`.
- Finish projection planner, label/player/time/scoring/replay/timeline/render/mail factory service functions now expose `IO[...]` at the service boundary.
- `BattleResultService.record` validates and builds the result record in `IO` before the transaction save.
- Local value accessors and DTO payload constructors remain pure only inside already-effectful service methods.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/projections/services backend/src/main/scala/services/battle/microservices/results/services`: passed with LF/CRLF warnings only.
- `rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/projections backend/src/main/scala/services/battle/microservices/results -g "*.scala"`: only the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-ACTOR-RULES-01

Goal: continue the actor microservice IO wave for runtime-facing player input, bot decisions, movement, and lifecycle helpers.

Boundary:

- `backend/src/main/scala/services/battle/microservices/actors/services`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`

Result:

- `BattleInputEnvironment.normalizeMovement` is now `BattleVector2 => IO[BattleVector2]`.
- `BattleInputRules.applyCommandToPlayer` sequences aim normalization, movement normalization, command sequence update, and weapon switching in `IO`.
- `BattleInputRules.normalizeAim` and command sequence max are `IO` service helpers.
- `BattleBotRules.applyBotControl` now sequences target selection, movement choice, fire/reload decisions, and aim update in `IO`.
- Bot decision helpers for target selection, combat movement, retreat/cover/flank/open movement, fire range/window checks, and aim calculation now return `IO` where they participate in bot runtime orchestration.
- `BattlePlayerRuntimeRules.movePlayer` now sequences stamina advancement and movement destination in `IO`.
- `BattlePlayerLifecycleRules.winnerFor` no longer delegates through a synchronous value helper.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`: passed with LF/CRLF warnings only.
- `rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services -g "*.scala"`: no matches.
- `rg -n "unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|AtomicReference|ThreadLocal|withMapIO|inMapIO" backend/src/main/scala -g "*.scala"`: no matches.

## Completed ticket BE-BATTLE-IO-EXTRACTION-RULES-01

Goal: continue the lower runtime IO wave for extraction initialization and extraction runtime state transitions.

Boundary:

- `backend/src/main/scala/services/battle/microservices/extraction/services`
- lifecycle use through `BattlePlayerLifecycleRules.clearDeadPlayerRuntime`

Result:

- `BattleExtractionInitialState.initialGasZone` now returns `IO[Option[BattleGasZoneState]]`.
- `BattleExtractionRuntimeRules.advanceObjectives` now composes arena lookup, gas zone advancement, gas damage, loot cache advancement, and extraction status advancement through `IO`.
- Gas zone stage selection, gas damage application, dead-player cleanup, loot cache search/scoring, extraction channel status, zone/cache predicates, and interruption construction now return `IO` in the extraction service boundary.
- Extraction gas death no longer calls `clearDeadPlayerRuntimeValue`; it uses `BattlePlayerLifecycleRules.clearDeadPlayerRuntime`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `rg -n "clearDeadPlayerRuntimeValue|var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe" backend/src/main/scala/services/battle/microservices/extraction/services backend/src/main/scala/services/battle/microservices/runtime/services -g "*.scala"`: no matches.
- `rg -n "unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|AtomicReference|ThreadLocal|withMapIO|inMapIO" backend/src/main/scala -g "*.scala"`: no matches.

## Completed ticket BE-BATTLE-IO-RUNTIME-REPLAY-SLOW-PICKUP-01

Goal: remove remaining runtime-step `IO.pure(sync service helper)` wrappers for lower runtime helpers that update aggregate runtime state.

Boundary:

- `backend/src/main/scala/services/battle/microservices/abilities/services/BattleSlowFieldRuntimeRules.scala`
- `backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleReplayFrameRecorder.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeStepRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeFinalizationRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeFinishRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleEngine.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala`

Result:

- `BattleSlowFieldRuntimeRules.advanceSlowFields` now returns `IO[BattleAggregateState]`.
- `BattlePickupRules.advancePickups` now returns `IO[BattleAggregateState]`.
- `BattleReplayFrameRecorder.updateFrames`, `appendFrame`, `captureFrame`, interval decision, and retention now return `IO`.
- Runtime step, finalization, finish, engine capture, and initial session state creation now sequence those helpers directly instead of wrapping synchronous service calls in `IO.pure`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala`: passed with LF/CRLF warnings only.
- Focused unsafe/global-state scan over the changed battle runtime/ability/session files: no matches.
- Battle microservices unsafe/global-state scan has only the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-EVENT-TIME-HELPERS-01

Goal: continue the runtime-service IO audit by converting event creation and time/timer service helpers to `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleEventFactory.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleTimeRules.scala`
- affected ability, actor, combat, extraction, and runtime call sites

Result:

- `BattleEventFactory.battleEvent`, `pickupEventId`, `weaponPickupEventMessage`, event participant construction, and event message construction now return `IO`.
- Pickup collection and projectile impact now sequence event creation in `IO`.
- `BattleProjectileImpactRules.applyProjectileImpact` now returns `IO[BattleAggregateState]`; projectile runtime folds projectile impacts through `IO`.
- `BattleTimeRules.elapsedAt`, rate delta helpers, and decrement helpers now return `IO`.
- Runtime step, actor timers/stamina/heat, extraction gas damage, slow fields, pickup respawn, and projectile TTL now sequence time helpers through `IO`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/combat/services backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/extraction/services`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-COMBAT-WEAPON-HELPERS-01

Goal: continue the combat service IO audit by converting runtime-facing weapon predicates and command helper values to `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleHeldFireRuntimeRules.scala`
- affected actor runtime/bot call sites

Result:

- `BattleWeaponRules.currentWeapon`, `updateCurrentWeapon`, `canFireMagazineWeapon`, `canFireHeatWeapon`, `finishReload`, `clampWeaponIndex`, and internal heat-resource helper now return `IO`.
- Weapon state creation/refill now sequences heat-resource checks in `IO`.
- `BattleWeaponFireRules.runtimeFireCommandSeq` now returns `IO[ClientCommandSeq]`.
- Primary-fire, reload resolution, held-fire, actor weapon timers, and bot weapon checks now compose those helpers through `IO`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services backend/src/main/scala/services/battle/microservices/actors/services backend/src/main/scala/services/battle/microservices/runtime/services backend/src/main/scala/services/battle/microservices/abilities/services backend/src/main/scala/services/battle/microservices/extraction/services`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-PROJECTILE-FACTORY-HELPERS-01

Goal: continue the combat service IO audit by converting projectile factory helpers that create projectile runtime state into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala`

Result:

- `BattleProjectileFactoryRules.weaponProjectiles` and `resolvePistolShot` now return `IO`.
- Projectile ID creation, spread direction, rotation, projectile construction, pistol projectile construction, and projectile birth-position construction are sequenced as `IO` helpers inside the factory.
- Weapon fire now binds projectile creation through `IO` for pistol, rocket, shotgun, and gatling paths.
- Non-pistol and heat-fire projectile append now uses the post-recoil `replacedState.projectiles` value instead of the earlier input state.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala`: passed with LF/CRLF warnings only.
- `rg "BattleProjectileFactoryRules\\.(weaponProjectiles|resolvePistolShot)" backend/src/main/scala backend/src/test/scala`: only the expected `IO` call sites in `BattleWeaponFireRules.scala`.

## Completed ticket BE-BATTLE-IO-PROJECTILE-MOTION-TARGETING-TERMINAL-01

Goal: continue the combat service IO audit by converting projectile runtime helper services into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala`
- `backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala`

Result:

- Projectile motion resolution now returns `IO[ProjectileMotionResult]` and accepts an effectful projectile-block callback.
- Projectile player targeting now returns `IO[Option[ProjectilePlayerHit]]` and sequences segment hit tests through an effectful callback.
- Projectile terminal construction, terminal append, and terminal retention now return `IO`.
- Projectile runtime and impact flows bind motion, targeting, terminal construction, and terminal retention directly in the existing `IO` program.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-WEAPON-FIRE-PRIVATE-HELPERS-01

Goal: finish the remaining private workflow helpers inside weapon fire by converting them to `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala`

Result:

- `applyWeaponRecoil`, `chargeHeatWeapon`, and `projectileBirthOffset` now return `IO`.
- Magazine and heat fire paths bind recoil, heat charge, and projectile birth offset in the existing weapon fire `IO` program.
- The direct player replacement helper already returned `IO` and remains the local state update boundary.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala`: passed with LF/CRLF warnings only.

## Completed ticket BE-BATTLE-IO-SKILL-COMMAND-PRIVATE-HELPERS-01

Goal: continue the lower runtime IO audit by converting remaining skill-command and command-application workflow helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillRules.scala`
- `backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`

Result:

- Skill availability checks now return `IO[Option[SkillOutcomeReason]]`.
- Skill command outcome construction, skill runtime updates, blink destination/range checks, dash direction normalization, and command-local player replacement now return `IO`.
- Dash motion destination in the runtime skill environment now returns `IO[BattleVector2]`.
- Runtime command application now builds the base post-input state through an `IO` player replacement helper before sequencing skill applications.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`: passed with LF/CRLF warnings only.

## Completed ticket BE-BATTLE-IO-PICKUP-COLLECTION-HELPERS-01

Goal: continue the ability-service IO audit by turning the pickup collection workflow into explicit `IO` helper steps.

Boundary:

- `backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala`

Result:

- Pickup contact resolution, pickup-driven player update, event kind/message selection, pickup consumption, player/pickup replacement, and event retention now return `IO`.
- `collectPickups` now sequences those steps in the existing `IO` fold instead of mixing synchronous branch logic with effectful equipment/event calls.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala`: passed with LF/CRLF warnings only.

## Completed ticket BE-BATTLE-IO-SESSION-COMMAND-ACCEPTANCE-HELPERS-01

Goal: continue the session service IO audit by converting command acceptance, read/submission wrapper, stored-battle advance, and projection-preparation helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/session/services/BattleCommandAcceptanceFactory.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionPreparationRules.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleAdvanceRules.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`

Result:

- Ignored command acceptance and ignored-reason construction now return `IO`.
- Battle-not-found read/submission wrappers, successful state reads, command submission updates, and finish projection preparation now return `IO`.
- Stored-battle advance now sequences safe time selection, elapsed/step calculation, state advancement, and result assembly through `IO`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/session/services/BattleCommandAcceptanceFactory.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionPreparationRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleAdvanceRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`: passed with LF/CRLF warnings only.

## Completed ticket BE-BATTLE-IO-SESSION-FINISH-PROJECTION-HELPERS-01

Goal: convert session finish-projection completion/status helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionStatusRules.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionCompletionRules.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`

Result:

- Projection artifact-status merge, finish-projection status selection, and ready-or-failed selection now return `IO`.
- Finish projection completion now returns `IO[StoredBattle]`.
- `BattleStateService.completeProjectionIO` now computes completion outside `Ref.modify` and commits through a compare-and-retry loop, keeping effect execution outside the `Ref` state transition.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionStatusRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionCompletionRules.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`: passed with LF/CRLF warnings only.

## Completed ticket BE-BATTLE-IO-SESSION-INITIALIZATION-HELPERS-01

Goal: convert session initial-state and stored-battle initialization helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleInitializationRules.scala`

Result:

- Battle map selection, started-at selection, bootstrap-seat derivation, command ownership map creation, and stored-battle construction now return `IO`.
- Initial state creation now sequences those helpers alongside existing runtime/rule-book IO reads.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/session/services`: passed with LF/CRLF warnings only.
- Focused unsafe/global-state scan over `session/services`: no matches.

## Completed ticket BE-BATTLE-IO-QUEUE-JOIN-HELPERS-01

Goal: continue the queue-service IO audit by converting queue join and room-selection helper flows into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueJoinRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRequestReuseRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala`

Result:

- Queue join command normalization, join participant/entry/ticket construction, join draft construction, and queue-request update now return `IO`.
- Open waiting room selection, reusable room selection, and stale queue-request reuse now return `IO`.
- Queue service join flow now sequences room selection, ticket/player/room ID allocation, draft creation, room advancement, and queue-request update through `IO`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueJoinRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRequestReuseRules.scala backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-QUEUE-LEAVE-HEARTBEAT-HELPERS-01

Goal: continue the queue-service IO audit by converting queue leave, heartbeat, room lifecycle, snapshot, and active-session helper flows into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueLeaveRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueHeartbeatRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueParticipantRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomLifecycleRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleRoomBootstrapper.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueSessionLookupRules.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala`

Result:

- Queue leave transition, rooms-after-leave, queue-request cleanup, and transition construction now return `IO`.
- Heartbeat room lookup, participant match/touch, and heartbeat room update now return `IO`.
- Room lifecycle creation/start decision/start/mark-finished and battle session bootstrap construction now return `IO`.
- Queue and room snapshot conversion, ticket snapshot lookup, and active battle session lookup now return `IO`.
- Queue service leave, room snapshot, heartbeat, mark-finished, active-session, room advancement, and room creation paths now sequence these helpers directly.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-QUEUE-RUNTIME-MODEL-HELPERS-01

Goal: continue the queue-service IO audit by converting queue runtime model and ID allocation helper methods into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala`
- `backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueIdAllocator.scala`
- affected queue service call sites

Result:

- `QueueRuntimeState.withRooms`, `withRoom`, `withTickets`, and `withQueueRequests` now return `IO`.
- `QueueRoom.phase`, `finishedAt`, `battleSession`, `isWaiting`, and `markFinished` now return `IO`.
- `QueueRoomLifecycle.phase`, `finishedAt`, `battleSession`, and lifecycle `markFinished` now return `IO`.
- `BattleQueueIdAllocator.allocateTicketId`, `allocateRoomId`, and `allocatePlayerId` now return `IO`.
- Queue service, room selection, request reuse, leave, lifecycle, snapshots, and session lookup call sites now bind those model helpers explicitly.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/queue/services`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan still only reports the pre-existing JDBC bind index `var` in `BattleResultTable.scala`.

## Completed ticket BE-BATTLE-IO-RESULTS-DATABASE-BINDING-HELPERS-01

Goal: remove the last unsafe/global-state scan hit in battle microservices by replacing mutable JDBC bind index state.

Boundary:

- `backend/src/main/scala/services/battle/microservices/results/database/BattleResultTable.scala`

Result:

- Replaced the `var index` based list-query binding with immutable `IndexedSqlBinding` values.
- List query binding now derives parameter order with `zipWithIndex` and binds through a small typed SQL binding ADT.
- The battle microservices unsafe/global-state scan now returns no matches.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/results/database/BattleResultTable.scala`: passed with LF/CRLF warnings only.
- `rg -n "var |synchronized|AtomicReference|ThreadLocal|unsafeRunSync|unsafeToFuture|cats\\.effect\\.unsafe|withMapIO|inMapIO" backend/src/main/scala/services/battle/microservices -g "*.scala"`: no matches.

## Completed ticket BE-BATTLE-IO-SESSION-FAILURE-FORMATTER-01

Goal: convert the remaining session projection failure formatting helper into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/session/services/BattleFailureMessageFormatter.scala`
- `backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`

Result:

- `BattleFailureMessageFormatter.throwableMessage` now returns `IO[String]`.
- Projection artifact failure recovery now binds the formatted failure message before constructing `BattleFinishProjectionOutcome.Failed`.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/results/database/BattleResultTable.scala backend/src/main/scala/services/battle/microservices/session/services/BattleFailureMessageFormatter.scala backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala`: passed with LF/CRLF warnings only.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-WORLD-MOTION-HELPERS-01

Goal: convert world motion normalization and stepped destination helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala`
- Direct actor, bot, combat, projectile, and runtime command call sites that depended on `BattleMotionRules`.

Result:

- `BattleMotionRules.normalizeMovement`, `findMotionDestination`, and stepped motion resolution now return `IO`.
- Player movement, bot open-movement probes, weapon recoil, projectile factory normalization, projectile motion, command input normalization, and skill motion destination now bind the motion helpers directly.
- Removed old `IO.pure(BattleMotionRules.normalizeMovement(...))` and pure `normalizeMovement: BattleVector2 => BattleVector2` adapter signatures from the battle microservice call graph.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerRuntimeRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`: passed with LF/CRLF warnings only.
- `rg -n "IO\\.pure\\(BattleMotionRules\\.normalizeMovement|IO\\.pure\\([^\\r\\n]*findMotionDestination|normalizeMovement: BattleVector2 => BattleVector2" backend/src/main/scala/services/battle/microservices -g "*.scala"`: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-WORLD-COLLISION-HELPERS-01

Goal: convert world collision and obstacle predicate helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala`
- `backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala`
- Direct ability, actor, combat, and runtime command call sites that consume collision predicates.

Result:

- `BattleArenaCollision` world-exit, obstacle-enter, AABB interval, point/AABB, circle hit, world-boundary, line-of-sight, occupancy, obstacle collision, and clamp helpers now return `IO`.
- `BattleMotionRules.resolveSteppedMotion` now folds step-by-step through effectful occupancy checks.
- Skill command environments now expose collision predicates as `IO` functions, and blink/freeze command validation binds those predicates before selecting outcomes.
- Bot target visibility and cover scoring now bind line-of-sight and occupancy checks directly.
- Projectile player-hit and world/obstacle block detection now call effectful collision helpers without `IO.pure(...)` wrappers.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`: passed with LF/CRLF warnings only.
- `rg -n "IO\\.pure\\(BattleArenaCollision\\.|isInWorld: BattleVector2 => Boolean|isInWorldWithRadius: \\(BattleVector2, Radius\\) => Boolean|collidesWithArenaObstacles: \\(BattleVector2, Radius\\) => Boolean|isBlockedPoint: BattleVector2 => Boolean|if hasArenaLineOfSight|if canPlayerOccupy|firstSegmentObstacleEnterT\\([^\\r\\n]*\\)\\.isEmpty" backend/src/main/scala/services/battle/microservices -g "*.scala"`: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-WORLD-GEOMETRY-HELPERS-01

Goal: convert world geometry vector helper functions into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/world/services/BattleGeometry.scala`
- Direct world, ability, actor, combat, and extraction service call sites using `BattleGeometry`.

Result:

- `BattleGeometry.clampDouble`, `add`, `subtract`, `scale`, `pointAtSegmentT`, `perpendicular`, `dot`, `vectorLength`, and `distanceBetween` now return `IO`.
- World motion stepping and destination fallback scoring now bind geometry helpers.
- Actor/player/bot movement, aim, cover probing, patrol clamping, pickup selection, projectile factory/motion/targeting/impact, weapon recoil, skill validation, and extraction zone/cache checks now compose geometry through `IO`.
- Removed remaining pure geometry helper use inside filters/sorts/conditionals and old `IO.pure(geometry(...))` wrappers in battle microservices.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/world/services/BattleGeometry.scala backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerRuntimeRules.scala backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala backend/src/main/scala/services/battle/microservices/extraction/services/BattleExtractionRuntimeRules.scala`: passed with LF/CRLF warnings only.
- `rg -n "IO\\.pure\\([^\\r\\n]*(distanceBetween|vectorLength|pointAtSegmentT|add\\(|scale\\(|subtract\\(|perpendicular\\(|clampDouble)|filter\\([^\\r\\n]*(distanceBetween|vectorLength)|sortBy\\([^\\r\\n]*distanceBetween|minByOption\\([^\\r\\n]*distanceBetween|if vectorLength|if distanceBetween" backend/src/main/scala/services/battle/microservices -g "*.scala"`: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-MICROSERVICES-SCALAR-HELPERS-01

Goal: convert remaining small synchronous service/runtime helper functions discovered by the def audit.

Boundary:

- `backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala`
- `backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala`
- `backend/src/main/scala/services/battle/microservices/results/services/BattleResultService.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionTimeRules.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFrameTimelineRules.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionMailFactory.scala`

Result:

- Bot target scoring, orbit direction, and rotation helpers now return `IO`; target selection and candidate movement generation bind them explicitly.
- Runtime command input environment construction now returns `IO[BattleInputEnvironment]`.
- Result service non-empty loadout normalization now returns `IO[Option[String]]`.
- Projection failure-message formatting, replay/finish elapsed clamping, replay source path encoding, URL encoding, and signed rating text helpers now return `IO`.
- Focused old-signature scan for these helpers returns no matches.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- `git diff --check -- backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala backend/src/main/scala/services/battle/microservices/results/services/BattleResultService.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionTimeRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFrameTimelineRules.scala backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionMailFactory.scala`: passed with LF/CRLF warnings only.
- `rg -n "targetScore\\([^\\r\\n]*\\): Double|orbitDirection\\([^\\r\\n]*\\): Double|rotate\\([^\\r\\n]*\\): BattleVector2|battleInputEnvironment\\([^\\r\\n]*\\): BattleInputEnvironment|failureMessage\\([^\\r\\n]*\\): String|nonEmpty\\([^\\r\\n]*\\): Option\\[String\\]|clampElapsed\\([^\\r\\n]*\\): Long|replaySourcePath\\([^\\r\\n]*\\): String|urlEncode\\([^\\r\\n]*\\): String|signed\\([^\\r\\n]*\\): String" backend/src/main/scala/services/battle/microservices -g "**/services/*.scala"`: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-PROJECTION-REPLAY-RENDERER-HELPERS-01

Goal: convert replay renderer private payload/string helpers into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFramesJsonRenderer.scala`

Result:

- `BattleReplayFramesJsonRenderer.render` now returns `IO[BattleReplayFramesJson]`.
- Replay-frame payload construction now sequences hero, projectile, pickup, event-message, and vector payload helpers through `traverse`/`flatMap`.
- Display-name fallback and pickup-kind label selection are explicit `IO` helpers in the service boundary.
- Circe encoders remain passive encode-only boundary code.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- Focused old-signature scan for replay renderer payload/string helpers: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-PROJECTION-PLANNER-VALUE-HELPERS-01

Goal: convert remaining projection planner collection/rating wrappers that participate in service flow into `IO`.

Boundary:

- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionReplayRules.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala`

Result:

- `BattleSettlements.toVector`, `map`, `foreach`, `find`, and `fromVectorOrFallback` now return `IO`.
- `BattlePreviousRatings.ratingBefore` and `fromRatings` now return `IO`.
- Finish projection replay owner selection, replay settlement rendering, artifact writing, and previous-rating loading now bind those wrappers explicitly.
- Pure `apply` factories remain pure because they only construct objects and do not allocate runtime resources.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- Focused old-signature/direct-sync-call scan for planner wrappers: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-PASSIVE-FACTORY-AUDIT-01

Goal: audit remaining battle microservice `services` definitions after the IO wave and convert missed service workflow helpers.

Boundary:

- `backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerLifecycleRules.scala`
- `backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala`

Result:

- Removed the last actor lifecycle pure service helper by replacing `clearDeadPlayerRuntimeValue` with an `IO` private helper.
- `BattleProjectionArtifactWriteOutcome.combine` now returns `IO[BattleFinishProjectionOutcome]` and the projector binds it explicitly.
- A precise service-layer scan now only reports pure `apply` constructors, which are intentionally passive object factories and do not allocate resources or perform side effects.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- Precise non-IO service return scan reports only pure `apply` constructors.
- Battle microservices unsafe/global-state scan returns no matches.

## Completed ticket BE-BATTLE-IO-API-DECODE-BOUNDARY-AUDIT-01

Goal: keep battle microservice `apiTypes` focused on encode/decode and move result response mapping out of the DTO/codec file.

Boundary:

- `backend/src/main/scala/services/battle/microservices/results/api/results/BattleResultApiTypes.scala`
- `backend/src/main/scala/services/battle/microservices/results/api/results/BattleResultResponseMapping.scala`
- `backend/src/main/scala/services/battle/microservices/results/api/BattleResultListAPIMessage.scala`
- `backend/src/main/scala/services/battle/microservices/results/api/BattleResultRecordAPIMessage.scala`

Result:

- Removed `fromRecord`, `fromList`, and `fromRecords` from `BattleResultApiTypes`.
- Added `BattleResultResponseMapping` as an API boundary mapper whose conversion helpers return `IO`.
- Result API message planners now bind response construction explicitly after service calls.
- Focused API-type scan confirms the result API type file has no response-construction helpers or service/planning imports.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed.
- Focused old-helper/caller scan for result response helpers in `ApiTypes` and API messages: no matches.
- Battle microservices unsafe/global-state scan returns no matches.

## Completion audit BE-BATTLE-IO-FINAL-COMPLETION-AUDIT-01

Result:

- The IO monadization plan is documented in this file and separates optional Kafka/distributed adapter work from the in-process cats-effect migration.
- Battle microservice service-layer workflow helpers now return `IO` or `Resource`; the precise non-IO service return scan reports only pure `apply` constructors:
  - `DefaultBattleFinishProjector.apply`
  - `BattleResultProjectionArtifactWriter.apply`
  - `BattleReplayProjectionArtifactWriter.apply`
  - `DefaultBattleQueueJoinAuthorizationService.apply`
- `apiTypes` are DTO/codec boundaries: focused scans show no service/planning/database imports or `IO` programs in `*ApiTypes.scala`.
- Battle microservices have no remaining `var`, `synchronized`, `AtomicReference`, `ThreadLocal`, `unsafeRunSync`, `unsafeToFuture`, `cats.effect.unsafe`, `withMapIO`, or `inMapIO` scan hits.
- The runtime allocation path uses cats-effect `Ref`/`Resource` for battle state, queue state, dynamic rules, `BackendRuntime`, and the http4s app runtime.

Verification:

- `sbt "-Dsbt.server.forcestart=true" compile` from `backend`: passed.
- `sbt "-Dsbt.server.forcestart=true" Test/compile` from `backend`: passed cleanly when rerun alone.
- Precise non-IO service return scan reports only pure `apply` constructors.
- Focused `apiTypes` service/planning scan returns no matches.
- Battle microservices unsafe/global-state scan returns no matches.
