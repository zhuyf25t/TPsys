# Agent State

## Current Goal

Refactor `backend/src/main/scala/services/battle` toward the requested battle-level core plus domain microservices shape:

```text
services/battle/{api,objects,routes,database}
services/battle/microservices/{domain}/{api,objects,routes,database}
```

The user reset the objective: move battle-internal Objects and API logic into the matching microservice; top-level battle should keep only core/shared battle-level logic.

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

## Next Ticket

Ticket:

```text
BE-BATTLE-ROOT-RESIDUAL-AUDIT-02
```

Goal:

- Audit remaining top-level battle directories after object/api/database migration.
- Confirm which top-level `routes` files are legitimate battle-level HTTP composition and which can be simplified.
- Confirm whether empty top-level `api` and `database` directories should remain physically present or be removed by git cleanup.

Allowed boundary:

```text
backend/src/main/scala/services/battle/{api,database,objects,routes}
backend/src/main/scala/services/battle/microservices
read-only audit first; edits only after one clear residual boundary is chosen
```

Verification:

```text
sbt "-Dsbt.server.forcestart=true" compile
sbt "-Dsbt.server.forcestart=true" "Test/runMain route.contract.BackendContractTestRunner"
git diff --check
```
