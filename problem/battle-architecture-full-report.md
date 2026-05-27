# Battle architecture full report

## Scope

This report summarizes the current battle backend implementation before the next refactor decision.

It covers:

- module implementation logic;
- type-safe ADT/value-object structure;
- Circe codec boundaries;
- Cats Effect/http4s boundaries;
- render/projection technology;
- current dependency direction;
- mismatches against the requested `api / objects / routes / database` target.

No Scala code was changed for this report.

## Current package shape

Current `services/battle` top-level folders:

| Folder | Scala files | Current meaning |
| --- | ---: | --- |
| `api` | 9 | APIMessage planners grouped by endpoint/domain |
| `database` | 89 | mixed persistence, runtime services, engine rules, projections |
| `objects` | 47 | authoritative battle ADTs/value objects plus API codecs |
| `routes` | 2 | battle API registry and runtime context |

Current shape already matches the outer four-folder direction, except the requested singular `object` is better represented as `objects` in Scala code because `object` is a Scala keyword and the repository already uses `objects`.

## Runtime request flow

The current battle request flow is:

```text
HTTP POST /api/{apiName}
  -> route/battle/BattleHttp4sRoutes
  -> system/api/APIMessageRouter
  -> services/battle/routes/BattleRoutes
  -> services/battle/api/**/XXXAPIMessage.plan(...)
  -> battle service/repository implementation
  -> objects/apiTypes Encoder
  -> JSON response
```

Key properties:

- Route matching is generic in `APIMessageRouter`.
- API names are derived from APIMessage class names by `APIMessage.apiNameFromClassName`.
- `BattleRoutes` registers typed API messages instead of manually rewriting paths.
- Identity token resolution is handled at the route/router boundary.
- API planners return `IO[Response]`.
- Synchronous service and persistence calls are wrapped with `IO.blocking`.

## Routes layer

Files:

- `services/battle/routes/BattleRoutes.scala`
- `services/battle/routes/BattleAPIRuntimeContext.scala`
- `route/battle/BattleHttp4sRoutes.scala`

Implementation logic:

- `BattleHttp4sRoutes` is the http4s integration adapter. It selects the result backend, resolves `userToken` through `IdentityService`, and delegates to `APIMessageRouter`.
- `BattleRoutes` is the battle API registry. It combines queue, room, state, command, and result API messages.
- `BattleAPIRuntimeContext` carries runtime services required by battle APIs.

Type-safety structure:

- Registered endpoints are `RegisteredAPIMessage`, not raw strings.
- Result backend selection uses `BattleResultAPIRegistration` enum.
- Service injection uses typed context values.

Cats Effect/http4s:

- http4s route returns `HttpRoutes[IO]`.
- Token resolution is `String, Connection => IO[Json]`.
- API execution is delegated to `APIMessageRouter.routes`.

Current issues:

- `BattleRoutes` imports many codec givens. This is acceptable for a registry, but it makes codec availability implicit.
- Result APIs have both connection-backed and repository-backed registration paths, which is useful but increases registry complexity.

## API layer

Current APIMessage files:

| Domain | APIMessage files |
| --- | --- |
| `command` | `BattleCommandAPIMessage.scala` |
| `queue` | `BattleQueueJoinAPIMessage.scala`, `BattleQueueStatusAPIMessage.scala`, `BattleQueueLeaveAPIMessage.scala` |
| `room` | `BattleRoomSnapshotAPIMessage.scala`, `BattleRoomHeartbeatAPIMessage.scala` |
| `state` | `BattleStateReadAPIMessage.scala` |
| `results` | `BattleResultListAPIMessage.scala`, `BattleResultRecordAPIMessage.scala` |

Implementation logic:

- Queue join authorizes a typed `BattleQueueJoinCommand`, then calls `BattleQueueService.join`.
- Queue status decodes `BattleQueueStatusQuery`, then reads a ticket snapshot.
- Queue leave decodes `BattleQueueLeaveCommand`, then calls `BattleQueueService.leave`.
- Room snapshot decodes `BattleRoomSnapshotQuery`, then calls `BattleQueueService.roomSnapshot`.
- Room heartbeat decodes `RealtimeRoomHeartbeatCommand`, then updates room heartbeat through queue service.
- State read decodes `BattleStateReadQuery`, then calls `BattleStateService.currentState`.
- Command decodes `BattleCommandRequest`, then calls `BattleStateService.acceptCommand`.
- Result list decodes `BattleResultListQuery`, then reads table or repository records and returns `BattleResultList`.
- Result record decodes `BattleResultRecordCommand`, validates handle, builds `BattleResultRecord`, and saves through table or repository.

Type-safety structure:

- API messages now hold object-layer command/query ADTs directly.
- Duplicate API request wrapper case classes have been removed.
- Service errors are finite enums such as `BattleCommandSubmitError`, `BattleQueueStatusError`, and `BattleRoomError`.
- API decode errors use `BattleAPIRequestError`.

Circe:

- Each APIMessage companion provides a `Decoder[XXXAPIMessage]`.
- APIMessage decoders inject `UserId` from the router-prepared JSON payload.
- Request shape decoding is delegated to `objects/apiTypes`.

Cats Effect:

- `plan(...)` returns `IO[Response]`.
- Blocking service/repository calls are wrapped in `IO.blocking`.
- Error mapping raises typed `APIMessageError`.

Current issues:

- API planners still know detailed service error enums. This is acceptable now, but local error mappers per API domain would make planners thinner.
- Result record API still contains validation/build/save orchestration. It is controlled, but it is the thickest planner.

## Objects layer

Key files and groups:

- `BattleEnums.scala`
- `BattleUseCaseCommands.scala`
- `BattleAPIRequestError.scala`
- `core`
- `command`
- `queue`
- `player`
- `weapon`
- `projectile`
- `pickup`
- `skill`
- `event`
- `replay`
- `result`
- `apiTypes`

Implementation logic:

- `objects` owns immutable battle data models and explicit command/query ADTs.
- `objects/apiTypes` owns wire encoding/decoding for those ADTs.
- `objects/package.scala` exports frequently used types for shorter imports.

Type-safe ADT/value-object structure:

- Identity/value objects: `TicketId`, `QueueRequestId`, `RoomId`, `BattleId`, `PlayerId`, `HeroId`, `ProjectileId`, `SlowFieldId`, `PickupId`, `BattleEventId`, `BattleResultId`.
- Time/tick/value objects: `EpochMillis`, `DurationMillis`, `ElapsedMillis`, `BattleTick`, `ClientCommandSeq`, `CooldownMillis`.
- Numeric gameplay wrappers: `BattleCapacity`, `Rating`, `RatingDelta`, `Score`, `KillCount`, `HitPoints`, `Stamina`, `AmmoCount`, `Radius`, `Damage`.
- Labels and UI-facing value objects: `BattleResultLabel`, `BattleModeLabel`, `BattleMapLabel`, `BattleHighlightLine`, `BattlePlayersLine`, `BattleTimelineHint`.
- Finite state enums: `BattleMode`, `BattlePhase`, `BattleArtifactStatus`, `MatchmakingRoomPhase`, `WeaponKind`, `ProjectileKind`, `SkillKind`, `PickupKind`, `BattleCommandStatus`, `BattleCommandReason`, `SkillOutcomeStatus`, `SkillOutcomeReason`, `ProjectileTerminalReason`, `BattleEventKind`.
- API/request error ADTs: `BattleAPIRequestError`, `BattleCommandRequestField`.
- Use-case command/query ADTs: `BattleQueueJoinCommand`, `BattleQueueStatusQuery`, `BattleQueueLeaveCommand`, `RealtimeRoomHeartbeatCommand`, `BattleRoomSnapshotQuery`, `BattleStateReadQuery`, `BattleResultListQuery`, `BattleResultRecordCommand`.

Current good state:

- Business concepts are mostly not raw strings/ints.
- Finite states are enum-based.
- API messages use object-layer commands/queries directly.
- `objects/apiTypes` does not declare duplicate battle request/response wrapper case classes for the API boundary.

Current issues:

- Some object companion methods still own wire conversion helpers such as `wireValue` and `fromWire`. This is useful, but it should stay pure and should not pull in Circe/http/database dependencies.
- Comment quality is inconsistent in some files, and some comments appear mojibake.

## API codecs in objects/apiTypes

Implementation logic:

- Request decoder namespaces decode JSON into authoritative command/query ADTs.
- Response encoder namespaces encode authoritative state/result ADTs to frontend JSON shape.

Examples:

- `BattleCommandRequestApiTypes` decodes legacy command JSON into `BattleCommandRequest`.
- `BattleQueueJoinRequest` decodes queue join JSON into `BattleQueueJoinCommand`.
- `BattleStateRootResponse` encodes `BattleAggregateState`.
- `BattleStatePlayerResponse` encodes `BattlePlayerState` and weapon/player UI fields.
- `BattleResultRecordResponse` and `BattleResultListResponse` encode result records and result list.

Circe:

- Uses `Decoder.instance` for custom validation and legacy compatibility.
- Uses `Encoder.forProductN` where stable field lists are simple.
- Uses `Encoder.instance` where nested optional or computed fields are needed.
- Uses `.asJson` and `.dropNullValues` for response shaping.

Current good state:

- Wire JSON mapping is isolated from domain objects.
- Decoders produce typed ADTs, not duplicate wrappers.
- Enum wire values are centralized through enum companion methods.

Current issues:

- `apiTypes` is under `objects`, which is acceptable as boundary code, but it should remain explicitly wire-only.
- Some response encoders include computed convenience fields for frontend compatibility. That is practical, but should be documented as wire contract, not domain state.

## Database/results persistence

Files:

- `BattleResultTable.scala`
- `BattleResultTableInitializer.scala`
- `BattleResultRepository.scala`
- `BattleResultStorage.scala`
- `PostgresBattleResultRepository.scala`
- `FileBattleResultRepository.scala`
- `InMemoryBattleResultRepository.scala`
- `BattleResultFileJsonParser.scala`
- `BattleResultFileJsonRenderer.scala`
- `BattleResultRepositoryOrderingRules.scala`

Implementation logic:

- `BattleResultTableInitializer` creates and migrates the `battle_results` table.
- `BattleResultTable` handles JDBC save/list operations.
- `BattleResultRepository` abstracts result persistence.
- `PostgresBattleResultRepository` delegates to table operations through `PostgresSupport`.
- `FileBattleResultRepository` and `InMemoryBattleResultRepository` are alternate storage adapters.

Type-safety structure:

- Persistence records are mapped to/from `BattleResultRecord`.
- IDs and labels use object-layer value objects.
- Survival state uses `BattleSurvivalOutcome`.

Side effects:

- JDBC and file IO are correctly isolated here.
- This is the only `database` subpackage that cleanly matches literal database semantics.

Current issues:

- File JSON parser/renderer is persistence compatibility logic, not core domain. It should remain isolated.

## Queue implementation

Files:

- `database/queue/*`

Implementation logic:

- Handles matchmaking queue join/status/leave.
- Manages waiting rooms, participants, heartbeats, queue request reuse, room lifecycle, and snapshots.
- Produces session bootstrap data for battle start.

Type-safety structure:

- Uses `BattleQueueJoinCommand`, `BattleQueueStatusQuery`, `BattleQueueLeaveCommand`, `BattleQueueLeaveOutcome`.
- Uses typed IDs and `BattleMode`, `MatchmakingRoomPhase`, `BattleCapacity`, `DurationMillis`, `EpochMillis`.

Side effects and state:

- `InMemoryBattleQueueService` uses `var` maps and `lock.synchronized`.
- Uses `System.currentTimeMillis` through an injected time function.

Current classification:

- Application/runtime service.
- Not literal database.

Current issues:

- Depends on session types for `BattleSessionSeed`.
- Should not be described as database if strict persistence semantics are required.

## Session implementation

Files:

- `database/session/*`

Implementation logic:

- Owns authoritative battle state service.
- Reads current battle state.
- Accepts player commands.
- Initializes battle sessions from queue bootstrap data.
- Advances stored battles through the engine.
- Triggers finish projection lifecycle.

Type-safety structure:

- Uses `BattleStateReadError`, `BattleCommandSubmitError`, `BattleCommandOwnership`, `BattleSessionSeed`.
- Uses `BattleAggregateState`, `BattleCommandRequest`, `BattleCommandAccepted`, `BattleFinishProjectionStatus`, and `BattleFinishProjectionOutcome`.

Side effects and state:

- `InMemoryBattleStateService` uses `var battles` and `lock.synchronized`.
- Finish projection may call artifact writers through `BattleFinishProjector`.

Current classification:

- Application/runtime service.
- Not literal database.

Current issues:

- Depends on `database/runtime.BattleEngine`.
- The package name hides that this is the authoritative in-memory battle runtime, not persistence.

## Runtime and engine implementation

Files:

- `database/runtime/*`

Implementation logic:

- `BattleEngine` is the engine facade.
- Runtime step advances state through players, pickups, slow fields, held fire, projectiles, weapons, events, and replay frames.
- Finalization determines finished state and room completion.
- Replay frame recorder captures snapshots for later replay rendering.

Type-safety structure:

- Uses `BattleAggregateState`, `BattlePhase`, `BattleTick`, `ElapsedMillis`, `EpochMillis`, `DurationMillis`, and command/result ADTs.

Current classification:

- Engine orchestration.
- Not literal database.

Current issues:

- Runtime imports abilities/actors/combat/world.
- Those packages also import runtime helpers, so this is not a clean one-way microservice split yet.

## World implementation

Files:

- `database/world/*`

Implementation logic:

- Defines arena catalog, map size, map-specific collision context, geometry helpers, motion rules, initial spawn layout, and map spec loading.

Type-safety structure:

- Uses `BattleMapId`, `BattleVector2`, `Radius`, `SpawnPointIndex`, and collision/movement domain values.

Side effects:

- `BattleMapSpecLoader` reads map spec files from disk.
- Geometry/collision/movement rules are pure or mostly pure.

Current classification:

- Engine world rules plus asset/file adapter.
- Not literal database.

## Combat implementation

Files:

- `database/combat/*`

Implementation logic:

- Defines weapon catalog and weapon state creation.
- Applies fire/reload/cooldown/heat rules.
- Creates projectiles.
- Advances projectile movement.
- Checks collision/impact/targeting.
- Produces terminal projectile records and damage effects.

Type-safety structure:

- Uses `WeaponKind`, `ProjectileKind`, `ProjectileTerminalReason`, `Damage`, `AmmoCount`, `CooldownMillis`, `BattleWeaponState`, `BattleProjectileState`.

Current classification:

- Engine combat rules.
- Not literal database.

Current issues:

- Depends on world, actors, and runtime helpers.
- This is normal for an engine rule graph, but not microservice-isolated.

## Actors implementation

Files:

- `database/actors/*`

Implementation logic:

- Applies player input to runtime player state.
- Updates player movement, sprint, aim, weapon input, and bot behavior.
- Handles lifecycle state such as alive/dead/respawn-like data.

Type-safety structure:

- Uses `BattlePlayerState`, `BattlePlayerLifeState`, `BattleParticipantKind`, `BattleCommandRequest`, `BattleCommandVector`, `BattleWeaponState`.

Current classification:

- Engine actor/player/bot rules.
- Not literal database.

Current issues:

- Bot/player runtime imports world and combat rules.
- Should remain in engine cohesion boundary until a deliberate AI/bot contract exists.

## Abilities implementation

Files:

- `database/abilities/*`

Implementation logic:

- Defines skill catalog and skill cooldown/duration rules.
- Applies skill commands.
- Handles pickup availability/effects.
- Advances slow-field runtime effects.

Type-safety structure:

- Uses `SkillKind`, `SkillOutcomeStatus`, `SkillOutcomeReason`, `PickupKind`, `BattlePickupState`, `BattlePickupAvailability`, `BattleSlowFieldState`.

Current classification:

- Engine ability/pickup rules.
- Not literal database.

Current issues:

- Depends on runtime, world, and combat helpers.
- Not ready to become an independent microservice.

## Projections and render implementation

Files:

- `database/projections/*`

Implementation logic:

- Converts finished battle state into persistent result records.
- Computes settlement/rating/labels/timeline strings.
- Writes result, replay, and mail artifacts through ports.
- Renders replay frames into JSON payloads.

Type-safety structure:

- Uses `BattleFinishProjector`, `BattleFinishProjectionOutcome`, `BattleFinishProjectionStatus`.
- Uses `BattleResultRecord`, `BattleReplayFrameState`, player/projectile/pickup state ADTs.

Circe/render:

- `BattleReplayFramesJsonRenderer` uses private wire payload case classes.
- Uses Circe `deriveEncoder`, `.asJson`, and `.noSpaces`.
- This is render/projection code, not gameplay mutation.

Current classification:

- Projection/render adapter.
- Not literal database except where it calls `BattleResultRepository`.

Current issues:

- Replay JSON payloads live in projection implementation. If replay JSON becomes an external stable API contract, move payload contract/codecs toward `objects/apiTypes/replay`.

## Dependency direction summary

Good direction:

- `routes -> api -> database services/repositories`.
- `api -> objects/apiTypes -> objects`.
- `database -> objects`.
- `objects` mostly stays passive and does not depend on route/http/database.

Problematic direction:

- `runtime <-> abilities/actors/combat/world` forms an engine implementation graph, not service isolation.
- `queue -> session`.
- `session -> runtime`.
- `projections -> results`.

Interpretation:

- This is acceptable as one cohesive battle runtime.
- It is not acceptable to call these subpackages independent microservices yet.

## Alignment against requested route

Already aligned:

- Four top-level folders exist.
- API files are split by business domain.
- API endpoints are `XXXAPIMessage.scala`.
- API messages return `IO[Response]`.
- API registry exists in `BattleRoutes.scala`.
- API paths are class-name derived.
- Object-layer ADTs are now reused by API messages.
- Circe codecs are in `objects/apiTypes`.
- `database/results` has table and initializer.

Partially aligned:

- `objects/apiTypes` is codec-only for battle API wrappers now, but response encoders still compute frontend-friendly wire fields.
- `database` has relevant subblocks, but most are not actual database.
- Business logic is split by concern, but engine packages call each other densely.

Not aligned or requires decision:

- Strict "database means Table/TableInitializer only" is not true today.
- Strict "different business logic must not call each other" is not true inside the engine rule graph.
- Strict `object` singular folder is not recommended in Scala; `objects` is the practical package.
- Some generated comments are mojibake and should be cleaned in a later doc/comment ticket.

## Recommended next step

Do not move code until the architecture decision is made.

Recommended near-term path:

- Use Option A from `battle-refactor-decision-matrix.md` for one or two more stabilization tickets.
- Keep the current four-folder top-level shape.
- Add explicit boundary documentation if `database` remains a broad implementation bucket.
- Continue type-safety cleanup and codec cleanup.

If the team chooses persistence-only semantics for `database`, the next code ticket should not be a broad move. It should move one boundary at a time after agreeing on the new destination package for runtime/session/queue/rules/projections.

## Decision required

The required decision remains:

- A. Keep `database` as broad implementation bucket for now.
- B. Make `database` persistence-only and approve a new destination for runtime/session/engine/projection.
- C. Start a staged `services/battle/microservices` migration, beginning with `results`, not engine.
