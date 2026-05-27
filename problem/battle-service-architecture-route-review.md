# Battle service architecture route review

## Ticket

- ID: `BE-BATTLE-REPORT-105`
- Goal: review the current `services/battle` structure against the requested target route before the next broad refactor decision.
- Boundary: read and document `backend/src/main/scala/services/battle`, `backend/src/main/scala/route/battle`, and `backend/src/main/scala/system/api`.
- No behavior change in this ticket.

## Current top-level structure

Current `services/battle` already has the four required top-level buckets:

- `api/`
- `objects/`
- `routes/`
- `database/`

This is directionally aligned with the requested target. The important issue is not the top-level names anymore; it is whether each bucket owns the correct kind of responsibility.

Current file counts:

- `api/command`: 1 APIMessage
- `api/queue`: 3 APIMessages
- `api/results`: 2 APIMessages
- `api/room`: 2 APIMessages
- `api/state`: 1 APIMessage
- `objects/apiTypes`: 17 codec/wire files
- `database`: 89 files across `abilities`, `actors`, `combat`, `projections`, `queue`, `results`, `runtime`, `session`, `world`
- `routes`: battle API registry and runtime context

## Runtime request flow

Current battle HTTP flow is:

1. `route/battle/BattleHttp4sRoutes.routes(...)`
2. `services/battle/routes/BattleRoutes.apiMessages(...)`
3. `system/api/APIMessageRouter.routes(...)`
4. `POST /api/{apiName}`
5. Circe decodes JSON into `XXXAPIMessage`
6. `XXXAPIMessage.plan(...)` runs in `cats.effect.IO`
7. API planner calls the injected battle service/database boundary
8. Circe encoder renders the response JSON

This is a good direction because route code is thin and API behavior is moved into typed APIMessage planners.

## API naming and route registration

`system.api.APIMessage.apiNameFromClassName` derives the wire API name from the class name:

- `BattleQueueJoinAPIMessage` -> `battlequeuejoin`
- `BattleStateReadAPIMessage` -> `battlestateread`
- `BattleResultRecordAPIMessage` -> `battleresultrecord`

`BattleRoutes` registers typed `RegisteredAPIMessage` entries instead of raw string names. This is better than the previous `List[String]` approach because registration requires a `Decoder[Message]`, `Encoder[Response]`, and `ClassTag[Message]`.

Current limitation:

- The path is lower-case derived by `apiNameFromClassName`.
- If the desired contract is literally `/api/BattleQueueAPIMessagePlanner`, current routing does not match that. The current contract is `/api/battlequeuejoin`.
- I recommend keeping current lower-case generated names unless you explicitly want class-name-cased API paths. Lower-case generated names are simpler for frontend and avoid case-sensitive URL mistakes.

## `api/` module logic

Current APIMessage files:

- `command/BattleCommandAPIMessage.scala`
- `queue/BattleQueueJoinAPIMessage.scala`
- `queue/BattleQueueStatusAPIMessage.scala`
- `queue/BattleQueueLeaveAPIMessage.scala`
- `room/BattleRoomSnapshotAPIMessage.scala`
- `room/BattleRoomHeartbeatAPIMessage.scala`
- `state/BattleStateReadAPIMessage.scala`
- `results/BattleResultListAPIMessage.scala`
- `results/BattleResultRecordAPIMessage.scala`

Current responsibilities:

- decode an API boundary object through Circe
- inject `userId` from `APIMessageRouter`
- normalize request DTOs into objects-level command/query ADTs
- call queue/session/result service boundaries
- map domain/service errors into `APIMessageError`
- return a typed response object

Type safety structure:

- API planners use `final case class XXXAPIMessage(...)`.
- API planners extend `APIWithTokenContextMessage[Context, Response]` or `APIWithTokenMessage[Response]`.
- `plan(...)` returns `IO[Response]`.
- Side effects are explicit via `IO.blocking(...)`, `IO.fromEither(...)`, or `IO.raiseError(...)`.
- Response types now mostly reuse `objects` ADTs directly:
  - `BattleQueueSnapshot`
  - `RealtimeRoomSnapshot`
  - `BattleQueueLeaveOutcome`
  - `BattleAggregateState`
  - `BattleCommandAccepted`
  - `BattleResultRecord`

Remaining intentional wire wrapper:

- `BattleResultListResponse`
  - It keeps the existing `{ "results": [...] }` JSON shape.
  - The object layer currently has `BattleResultRecord` and `BattleResultListQuery`, but no authoritative `BattleResultList` ADT.

Main API issue still remaining:

- Some API planners still contain normalization logic that is larger than ideal, especially:
  - `BattleCommandAPIMessage`
  - `BattleQueueJoinAPIMessage`
  - `BattleResultRecordAPIMessage`
  - `BattleResultListAPIMessage`
- This logic is type-safe, but not perfectly separated. A future ticket can move pure normalization into object-level command/query constructors or companion functions, while keeping I/O in the planner.

## `objects/` module logic

Current `objects` is the authoritative ADT/value-object layer.

Main ADT groups:

- `BattleEnums.scala`
  - unified finite-state enums: battle phase, mode, weapon kind, projectile kind, skill kind, pickup kind, event kind, command status/reason, artifact status, etc.
- `core/`
  - IDs and value objects: `BattleId`, `PlayerId`, `TicketId`, `DurationMillis`, `EpochMillis`, `BattleVector2`, `HitPoints`, `AmmoCount`, etc.
- `command/`
  - command input and accepted command result.
- `queue/`
  - queue participant, session descriptor, queue snapshot, realtime room snapshot.
- `player/`, `weapon/`, `projectile/`, `pickup/`, `skill/`, `event/`
  - runtime state objects.
- `replay/`, `result/`
  - replay frame and result record objects.
- `apiTypes/`
  - Circe decoders and encoders for wire-level request/response shapes.

Type safety wins already present:

- IDs are value objects, not raw `String`.
- important numeric fields are value objects, not raw `Long`/`Int`.
- finite states are Scala 3 enums.
- state objects are immutable `final case class`.
- API response objects now mostly reuse domain ADTs instead of duplicate response DTOs.

Current concern:

- `objects/apiTypes` still contains codec code under the object tree. This is acceptable if we define `apiTypes` as boundary code, but it means `objects` is not a purely domain-only package.
- If the strict rule is “object must only contain final case class + enum and almost no codec,” then `apiTypes` should eventually be moved to `api/codec` or `api/types`. However, the user previously requested response/request types to be placed under `objects/apiTypes`, so I recommend keeping it for now.

## Circe usage

Current Circe use is mostly appropriate:

- `APIMessageRouter` uses http4s-circe to read request JSON and write response JSON.
- APIMessage companion objects define `given Decoder[XXXAPIMessage]`.
- `objects/apiTypes` defines request decoders and response encoders.
- State response encoders project domain ADTs into stable frontend wire fields.
- Map and file persistence readers use Circe parser/decoder instead of hand-written parsing in most newer paths.

Remaining technical debt:

- Some codec files still use manual cursor code because validation is custom.
- `BattleReplayFramesJsonRenderer` and result file JSON renderer are still render/persistence projection code. They are not API contract encoders, but they are still “rendering JSON” and should be treated as a compatibility boundary.

## Cats Effect usage

Current Cats Effect boundary:

- `APIMessage.plan(...)` returns `IO[Response]`.
- JDBC and mutable service calls are wrapped in `IO.blocking`.
- HTTP routes are http4s `HttpRoutes[IO]`.
- `APIMessageRouter` handles token injection and errors inside `IO`.

This is a cleaner side-effect model than directly running route logic imperatively.

Remaining issue:

- Several services under `database/queue` and `database/session` are still mutable/in-memory services internally.
- The current planner boundary makes this mutation explicit only at the API edge by wrapping calls in `IO.blocking`; it does not make the internal service implementation purely functional.

## Render and projection logic

There are two different “render” meanings in this backend:

1. API response rendering
   - Lives in `objects/apiTypes`.
   - Converts domain objects to frontend JSON shape.
   - Examples: `BattleStateRootResponse`, `BattleStatePlayerResponse`, `BattleQueueSnapshotResponse`.

2. Replay/result artifact rendering
   - Lives in `database/projections` and `database/results`.
   - Converts battle state/result data into persisted replay/result artifacts.
   - Examples: `BattleReplayFramesJsonRenderer`, `BattleResultFileJsonRenderer`.

This separation is mostly reasonable, but naming can be clearer:

- API rendering should be called encoder/codec.
- Replay artifact rendering can keep renderer naming because it is producing a stored artifact.

## `routes/` module logic

Current `services/battle/routes/BattleRoutes.scala` is now close to the requested structure:

- It imports all battle APIMessage classes.
- It registers typed API messages in a single list.
- It does not manually match every HTTP path.
- It keeps battle route registration separate from generic http4s routing.

Current `route/battle/BattleHttp4sRoutes.scala` is an adapter:

- It wires battle runtime context into `APIMessageRouter`.
- It resolves `userToken` through identity.
- It does not own battle business logic.

This is the right direction.

Remaining concern:

- `BattleRoutes.connectionBackedResultApiMessages` and `BattleResultAPIRegistration` exist because result storage has two backends.
- This is not wrong, but it exposes storage selection in route registration. A cleaner final shape would inject one `BattleResultStorage` in runtime assembly and keep route registration simpler.

## `database/` module logic

Current `database` is overloaded.

It contains:

- real persistence:
  - `results/BattleResultTable.scala`
  - `results/BattleResultTableInitializer.scala`
  - repositories
- queue/session mutable runtime services:
  - `queue/BattleQueueService.scala`
  - `session/BattleStateService.scala`
- game rules/engine:
  - `runtime/`
  - `world/`
  - `combat/`
  - `actors/`
  - `abilities/`
- projections:
  - finish projection
  - replay rendering
  - result artifact writing

This is the biggest mismatch with the clean architecture target.

The current layout satisfies the requested “battle/database has subblocks” literally, but semantically `database` is not only database. It is currently acting as:

- persistence layer
- application service layer
- engine/rule layer
- projection layer

Recommended decision:

- Short term: keep this structure while the APIMessage migration stabilizes.
- Medium term: rename or split only if you approve a second-stage refactor.
- If we keep your requested four-bucket model strictly, then `database` should be interpreted as “backend implementation layer,” not literal DB-only storage.
- If we want stricter clean architecture, then `database/runtime`, `database/world`, `database/combat`, `database/actors`, `database/abilities` should not live under a package named `database`.

## Dependency direction

Good current direction:

- `routes` imports `api`, `objects`, and service/context types.
- `api` imports `objects`, `objects/apiTypes`, and selected `database` service boundaries.
- `database` imports `objects`.
- `objects` does not import `api` or `routes`.

Known messy direction:

- subpackages inside `database` import each other heavily.
- This is acceptable inside a cohesive engine implementation, but it means these are not independent microservices.
- Treating `abilities`, `actors`, `combat`, `runtime`, and `world` as independent services would be false right now.

Conclusion:

- We should not split these internal engine rule packages into isolated microservices unless we first introduce explicit ports/events/state transition APIs.
- Current best target is layered one-way dependency, not forced microservice isolation.

## Reasonableness of the requested route

Reasonable parts:

- Keep one battle module with `api`, `objects`, `routes`, `database`.
- Put every endpoint in a typed `XXXAPIMessage.scala`.
- Use `APIMessageRouter` so route code is short.
- Use `apiNameFromClassName` to avoid manually maintaining string paths.
- Keep ADTs in `objects` and reuse them from API responses.
- Keep Circe codecs at the API boundary.
- Keep side effects in `IO`.
- Keep database table/repository code under `database/results`.

Parts that need adjustment:

- `object` should probably remain `objects` because the current package is already plural and exported widely. Renaming to singular would be high churn with little benefit.
- “`objects` should only have case class + enum + encoder decoder” is too strict if it means putting codec into pure domain files. Better: domain ADTs in `objects/*`, API codecs in `objects/apiTypes/*` as currently agreed.
- “`database` should have Table and TableInitializer” is too narrow for the current implementation. Most battle runtime is not SQL-backed. If `database` is literal, many files are misnamed. If `database` means implementation layer, current structure is workable.
- “API planners should only have private pure functions” is mostly good, but current planners must call services and map errors inside `IO`. That side-effect orchestration belongs in planner/application boundary.
- “No if/else in plan” is reasonable stylistically. Current plans mostly use `for` and delegate conditionals into private helpers. Some private helpers still use `if`; that is acceptable for pure validation, but can be replaced with `Either.cond` case-by-case.

## Recommended next decision

I recommend not doing another broad move yet.

Next safe ticket should be one of:

1. `BE-BATTLE-API-NORMALIZE-01`
   - Move `BattleCommandAPIRequest -> BattleCommandRequest` normalization into a typed object-level constructor/helper.
   - Keep API planner small.
   - Verify compile and contract tests.

2. `BE-BATTLE-RESULT-LIST-ADT-01`
   - Introduce an authoritative `BattleResultList` ADT in `objects/result`.
   - Replace `BattleResultListResponse` with that ADT plus encoder namespace.
   - This removes the last response envelope DTO from `objects/apiTypes`.

3. `BE-BATTLE-DATABASE-SEMANTICS-01`
   - Decide whether `database` means literal persistence or implementation layer.
   - If literal persistence: plan a rename/split of engine rules out of `database`.
   - If implementation layer: document it and stop treating it as DB-only.

Option 2 has now been implemented as `BE-BATTLE-RESULT-LIST-ADT-01`.

`BE-BATTLE-API-NORMALIZE-01` has now been implemented for battle command submission.

Change made:

- `BattleCommandAPIRequest.toCommand(...)` now owns the pure conversion from wire request DTO to `BattleCommandRequest`.
- `BattleCommandAPIMessage` delegates to that pure conversion and keeps only IO orchestration plus service error mapping.
- The conversion still returns `Either[BattleAPIRequestError, BattleCommandRequest]`, so missing ticket and invalid request states remain ADT-based instead of string-based.

`BE-BATTLE-QUEUE-NORMALIZE-01` has now been implemented for queue join.

Change made:

- `BattleQueueJoinRequest.toCommand(...)` now owns the pure conversion from wire request DTO to `BattleQueueJoinCommand`.
- `BattleQueueJoinAPIMessage` delegates to that conversion and keeps authorization plus queue service calls in `IO`.
- The conversion returns `Either[BattleAPIRequestError, BattleQueueJoinCommand]`, so invalid handle and missing session remain typed ADT failures.

`BE-BATTLE-RESULT-NORMALIZE-01` has now been partially implemented for result list.

Change made:

- `BattleResultListAPIRequest.toQuery(...)` now owns the pure conversion from wire request DTO to `BattleResultListQuery`.
- `BattleResultListAPIMessage` delegates to that conversion and keeps storage selection plus list loading in `IO`.
- Default limit and handle lookup normalization are still typed through `BattleResultListLimit` and `PlayerHandle`.

Not moved yet:

- `BattleResultRecordAPIRequest -> BattleResultRecordCommand`.
- Reason: that conversion also owns submission-time defaults, handle parsing, visitor policy switches, and result label fallback semantics. It should be moved as a separate focused ticket to avoid mixing list-read behavior with record-write behavior.

`BE-BATTLE-RESULT-RECORD-NORMALIZE-01` has now been implemented.

Change made:

- `BattleResultRecordAPIRequest.toCommand(...)` now owns the pure conversion from wire request DTO to `BattleResultRecordCommand`.
- `BattleResultRecordAPIMessage` delegates to that conversion and keeps record validation, transaction handling, storage selection, and repository/table save in `IO`.
- Submission defaults, handle trimming, visitor policy switch, and `aliveAtEnd -> BattleSurvivalOutcome` remain typed and deterministic in the request companion.

`BE-BATTLE-QUEUE-REQUEST-ADT-01` has now been implemented for queue status and queue leave.

Change made:

- Added `BattleQueueStatusQuery(ticketId: TicketId)` to the objects layer.
- Added `BattleQueueLeaveCommand(ticketId: TicketId)` to the objects layer.
- Removed the duplicate API request case classes `BattleQueueStatusRequest` and `BattleQueueLeaveRequest`.
- Kept `BattleQueueStatusRequest` and `BattleQueueLeaveRequest` object names only as decoder namespaces.

Effect:

- Queue status and queue leave now use objects-layer query/command ADTs.
- API planners no longer carry raw `TicketId` as the main request model.
- Wire JSON remains `{ "ticketId": "..." }`.

`BE-BATTLE-ROOM-STATE-REQUEST-ADT-01` has now been implemented.

Change made:

- Added `BattleRoomSnapshotQuery(roomId: RoomId)` to the objects layer.
- Added `BattleStateReadQuery(battleId: BattleId)` to the objects layer.
- Removed duplicate API request case classes `BattleRoomSnapshotRequest` and `BattleStateReadAPIRequest`.
- Kept the same object names only as decoder namespaces, preserving existing wire JSON fields.

Effect:

- Room snapshot and state read requests are now modeled as objects-layer query ADTs.
- API planners receive typed query models instead of raw `RoomId` / `BattleId`.
- Wire JSON remains `{ "roomId": "..." }` and `{ "battleId": "..." }`.

Recommended next ticket:

- `BE-BATTLE-REQUEST-WRAPPER-AUDIT-01`
  - Re-run the API boundary DTO scan.
  - Confirm that remaining request DTOs are either true wire compatibility objects or have an objects-layer command/query equivalent.

`BE-BATTLE-REQUEST-WRAPPER-AUDIT-01` has now been completed.

Current remaining request DTOs in `objects/apiTypes`:

- `BattleQueueJoinRequest`
- `BattleCommandAPIRequest`
- `BattleResultListAPIRequest`
- `BattleResultRecordAPIRequest`

Decision:

- Keep all four as API boundary DTOs.
- They are not simple ID wrappers anymore.
- They preserve wire compatibility fields and provide typed conversion into objects-layer command/query ADTs.

Current conversion map:

- `BattleQueueJoinRequest.toCommand(...)` -> `BattleQueueJoinCommand`
- `BattleCommandAPIRequest.toCommand(...)` -> `BattleCommandRequest`
- `BattleResultListAPIRequest.toQuery(...)` -> `BattleResultListQuery`
- `BattleResultRecordAPIRequest.toCommand(...)` -> `BattleResultRecordCommand`

Current API boundary state:

- No `final case class *Response` remains in battle `objects/apiTypes`.
- No simple ID-wrapper request case class remains in battle `objects/apiTypes`.
- Encoder/decoder object namespaces remain where they are needed to preserve JSON shape.

Recommended next ticket:

- `BE-BATTLE-DATABASE-SEMANTICS-01`
  - Decide whether `services/battle/database` is allowed to mean "battle implementation layer" or must mean literal persistence only.
  - This is the next major architectural decision because `database` currently contains persistence, queue/session services, runtime rules, world rules, combat rules, actor rules, abilities, and projections.

## Verification performed for this report

- Inspected `services/battle/api`.
- Inspected `services/battle/objects`.
- Inspected `services/battle/routes`.
- Inspected `services/battle/database`.
- Inspected `route/battle/BattleHttp4sRoutes.scala`.
- Inspected `system/api/APIMessage.scala`.
- Inspected `system/api/APIMessageRouter.scala`.
