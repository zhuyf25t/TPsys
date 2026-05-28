# Battle four-layer refactor rationality report

Updated: 2026-05-28

## Ticket

ID:

```text
BE-BATTLE-FOUR-LAYER-RATIONALITY-01
```

Goal:

```text
Audit the current services/battle implementation and judge whether the requested strict four-layer route is reasonable before continuing code migration.
```

Allowed boundary:

```text
Documentation only.
No Scala behavior changes.
```

## Requested Target

The requested final `services/battle` shape is:

```text
services/battle/api
services/battle/objects
services/battle/routes
services/battle/database
```

The important details are:

- `api`: grouped by business capability; only `XXXAPIMessage.scala` files should live here.
- `objects`: ADT/value objects/case classes/enums and companion construction or codec helpers only.
- `objects/apiTypes`: request/response wire DTO codecs; mainly `final case class XXXResponse` plus `object XXXResponse` with Circe `Encoder`/`Decoder`.
- `routes`: one thin `BattleRoutes.scala` registry containing registered API messages.
- `database`: grouped PostgreSQL table access; each sub-block should be table-oriented, not a business service dump.
- Business subdomains are allowed, but they should be nested under the four layers, not as a fifth top-level `microservices`, `runtime`, `application`, `engine`, or `services` layer.

## Current State From Scan

Current top-level battle folders:

| Folder | Scala files | Current role | Alignment |
| --- | ---: | --- | --- |
| `api` | 9 | APIMessage planners for queue, room, state, command, results | Partly aligned |
| `objects` | 69 | ADTs, value objects, API codecs, but also standalone rules | Not aligned |
| `routes` | 2 | API registry plus runtime context | Partly aligned |
| `database` | 18 | PostgreSQL tables plus rule-book process caches | Partly aligned |
| `microservices` | 53 | queue/session/runtime/world/combat/actors/projections service logic | Not aligned |

Conclusion:

```text
The repository has started moving toward the requested four-layer shape, but the current implementation is still transitional.
```

The largest mismatch is that `microservices` still owns most active battle behavior. The second largest mismatch is that `objects` still contains rule modules, and `database` contains RuleBook caches.

## Module Logic Summary

### Queue

Current files:

```text
api/queue/*
objects/queue/*
microservices/queue/services/*
objects/apiTypes/queue/*
```

Current responsibility:

- Join queue.
- Read queue status.
- Leave queue.
- Maintain waiting rooms.
- Reuse queue requests.
- Start a room when capacity or timeout condition is reached.
- Produce room snapshots used by the frontend waiting area.

Current type-safety structure:

- Good: IDs such as `TicketId`, `RoomId`, `PlayerId`, `QueueRequestId` are value objects.
- Good: queue room phase uses ADT/enum-style state such as `Waiting`, `Active`, `Finished`.
- Weak: `InMemoryBattleQueueService` keeps runtime state through `AtomicReference` plus `synchronized`.
- Weak: queue use cases are still service methods, not directly expressed as APIMessage `plan(connection)`.

Current Cats Effect / side-effect boundary:

- API files wrap blocking service calls with `IO.blocking`.
- The actual queue state is still process memory, not PostgreSQL-backed command planning.

Route target:

- `BattleQueueJoinAPIMessage`, `BattleQueueStatusAPIMessage`, `BattleQueueLeaveAPIMessage` should stay in `api/queue`.
- `BattleRoutes` should register those messages through typed `RegisteredAPIMessage.apiWithToken[...]`.
- Queue service injection should be removed only when queue operations can be expressed through `Connection` and table APIs or a strictly local private planner inside the APIMessage.

Risk:

```text
Moving queue logic directly into APIMessage too quickly will create a giant API file and may preserve the same mutable state under a new name.
```

Recommended migration:

- First replace queue mutable runtime state with immutable queue transition ADTs in `objects/queue`.
- Then let each queue APIMessage call table operations and private pure transition functions.
- Finally delete `microservices/queue`.

### Room

Current files:

```text
api/room/*
objects/apiTypes/room/*
microservices/queue/services/*
```

Current responsibility:

- Read realtime waiting room snapshot.
- Receive room heartbeat.
- Keep waiting room players fresh before battle starts.

Current type-safety structure:

- Good: `RoomId`, `TicketId`, `RealtimeRoomHeartbeatCommand`, `RealtimeRoomSnapshot` are typed.
- Weak: room is not an independent domain yet; it is coupled into queue service internals.

Current Cats Effect / side-effect boundary:

- API calls `queueService.roomSnapshot` and `queueService.heartbeat` through `IO.blocking`.

Route target:

- Keep room API messages under `api/room`.
- Keep room DTO codecs under `objects/apiTypes/room`.
- Do not make `routes` know heartbeat details.

Risk:

```text
Room and queue are strongly coupled. Splitting methods before extracting shared ADTs will cause duplicated room-start logic.
```

### Session / State

Current files:

```text
api/state/*
objects/core/*
microservices/session/services/*
objects/apiTypes/state/*
```

Current responsibility:

- Store active battle sessions.
- Read authoritative battle state.
- Advance stored battles.
- Track battle lifecycle and finish projection status.

Current type-safety structure:

- Good: `BattleAggregateState`, `BattlePhase`, `BattleTick`, `EpochMillis`, `ElapsedMillis`, `DurationMillis` express important concepts as typed values.
- Weak: `BattleStateService` still has `private var battles: Map[BattleId, StoredBattle]`.
- Weak: state transitions are split across many rule/service files and are not yet represented as one clear ADT transition pipeline.

Current Cats Effect / side-effect boundary:

- API wraps `stateService.currentState(...)` with `IO.blocking`.
- Stored battle state is process memory.

Route target:

- `BattleStateReadAPIMessage` should remain the only API entry for state read.
- Its final form should be `APIWithTokenMessage[BattleStateResponse]` or equivalent with `plan(connection)`, not `APIWithTokenContextMessage[BattleStateService, ...]`.

Risk:

```text
Session/state is the highest-risk area. Removing the service layer before defining table-backed stored battle lifecycle will break gameplay.
```

### Command / Runtime

Current files:

```text
api/command/*
objects/command/*
objects/skill/*
objects/weapon/*
microservices/runtime/services/*
microservices/combat/services/*
microservices/actors/services/*
microservices/world/services/*
objects/apiTypes/command/*
```

Current responsibility:

- Accept player input.
- Validate command ownership.
- Apply movement, weapon fire, projectiles, pickups, skills, bot updates, and finish rules.
- Return command acceptance result.

Current type-safety structure:

- Good: `BattleCommandRequest`, `BattleCommandAccepted`, `BattleCommandStatus`, `BattleCommandReason`, `SkillOutcomeStatus`, `SkillOutcomeReason` avoid many string states.
- Weak: several rule modules still live in `objects`, which violates the strict ADT-only object target.
- Weak: many dynamic rules are accessed through process-local RuleBooks.

Current Circe structure:

- Request decoder exists in `objects/apiTypes/command/BattleCommandRequestApiTypes.scala`.
- It currently contains custom private decoder helpers. This is acceptable as boundary decoding, but it violates the user's stricter preference that apiTypes only contain case class plus encoder/decoder object and minimal codec code.

Current Cats Effect / side-effect boundary:

- API uses `IO.blocking(stateService.acceptCommand(command))`.
- The actual runtime simulation is not expressed as `IO` until the API boundary.

Route target:

- `BattleCommandAPIMessage` should be the single command use-case entry.
- Heavy runtime rules should not be dumped into `BattleCommandAPIMessage`; they need typed private helpers grouped by capability only if the user accepts that APIMessage files can become orchestration modules.

Risk:

```text
The requested "only APIMessage plus private helpers" style is reviewable for small APIs, but command/runtime is large enough that putting all private helpers in one file will create a new god file.
```

### Combat

Current files:

```text
objects/combat/*
microservices/combat/services/*
database/combat/*
```

Current responsibility:

- Weapon inventory.
- Fire cooldown.
- Projectile creation.
- Projectile movement.
- Projectile collision and hit resolution.
- Weapon heat/reload.

Current type-safety structure:

- Good: `WeaponKind`, `ProjectileKind`, `Damage`, `Radius`, `CooldownMillis`, `BattleWeaponProjectileSpeed`, `BattleWeaponProjectileCount` are typed.
- Good: weapon/projectile configuration is now represented as typed definitions.
- Weak: `BattleCombatRuleBook` is a mutable process cache over PostgreSQL rows.
- Weak: several `objects/combat/*Rules.scala` files are standalone rule modules, not ADT declarations.

Current database structure:

- `BattleCombatRuleTable` and `BattleCombatRuleTableInitializer` are aligned with the requested database shape.
- `BattleCombatRuleBook` is not aligned if database must only contain Table and TableInitializer.

Route target:

- Combat should not expose independent route files unless there is a combat API.
- Combat rules should be consumed by command/state APIs through typed rule config loaded from tables.

Risk:

```text
If RuleBook is deleted without a replacement load path, command/runtime code will lose access to weapon rules.
```

### Abilities / Pickups

Current files:

```text
objects/abilities/*
objects/pickup/*
objects/skill/*
database/abilities/*
microservices/runtime/services/*
```

Current responsibility:

- Skill config.
- Skill cooldown/runtime.
- Pickup availability and respawn.
- Slow field behavior.

Current type-safety structure:

- Good: `SkillKind`, `PickupKind`, `DurationMillis`, `Radius`, `SkillOutcomeReason`, `BattlePickupAvailability` exist.
- Weak: `objects/abilities/*Rules.scala` contain behavior.
- Weak: ability database has Table and Initializer, but also RuleBook cache.

Route target:

- Keep ability objects as typed config and state ADTs.
- Dynamic ability config can be table-backed.
- Skill and pickup behavior should be reached through command/runtime API planning, not through public route files.

### World / Map / Movement

Current files:

```text
objects/world/*
database/world/*
microservices/world/services/*
```

Current responsibility:

- Map IDs and labels.
- World size.
- Obstacles and collision.
- Spawn points.
- Movement rule config.

Current type-safety structure:

- Good: `BattleMapId`, `BattleVector2`, `BattleWorldRuleSet`, obstacle shapes, spawn definitions are typed.
- Weak: `BattleGeometry.scala` is a behavior module in `objects`.
- Weak: `BattleWorldRuleTable.scala` is large and mixes table access, JSON decoding, and conversion.

Current Circe/render structure:

- Map specs are decoded from JSON in the table layer.
- This is a boundary concern, but the file is too large for the requested database style.

Route target:

- Keep world config ADTs in `objects/world`.
- Keep map table schema and row reading in `database/world`.
- If a public world content API exists later, it should be an `api/world/*APIMessage.scala`, not a route-specific parser.

Risk:

```text
World has the highest data-shape complexity. Forcing it into only one Table file may keep compilation simple but reduce readability unless the table file is carefully bounded.
```

### Results / Replay / Projection

Current files:

```text
api/results/*
objects/result/*
objects/replay/*
database/results/*
microservices/projections/services/*
objects/apiTypes/results/*
```

Current responsibility:

- Record finished battle result.
- List player results.
- Render battle replay frames JSON.
- Produce mail/replay/rating projection plans.

Current type-safety structure:

- Good: `BattleResultRecord`, `BattleResultList`, result labels, score, placement, artifact status are typed.
- Good: result API messages already use `Connection` directly and are closer to the requested target than queue/state/command.
- Weak: replay frame rendering still lives under `microservices/projections/services`.
- Weak: replay render payload contains private DTO classes and manual shape construction inside a service file.

Current Circe/render structure:

- API result response uses Circe `Encoder`.
- Replay frame render uses Circe semiauto derivation and `.asJson.noSpaces`.
- This is better than handwritten JSON strings, but it is still located outside the requested four-layer target.

Route target:

- Result API messages can be migrated first because they already match `plan(connection)`.
- Replay render output should either become response DTO/encoder under `objects/apiTypes/replay` or remain as private helper inside the specific result/replay APIMessage if the user insists on no fifth layer.

Risk:

```text
Replay rendering is a boundary renderer, not a domain object. It should not be moved into objects unless modeled as DTO encoders.
```

## Type Safety Assessment

### Strong parts

- Major IDs and units are value objects: `BattleId`, `TicketId`, `RoomId`, `PlayerId`, `DurationMillis`, `EpochMillis`, `ElapsedMillis`, `Radius`, `Damage`.
- Finite states mostly use enums/ADTs: `BattlePhase`, `MatchmakingRoomPhase`, `BattleCommandStatus`, `BattleCommandReason`, `SkillOutcomeStatus`, `SkillOutcomeReason`, `BattleArtifactStatus`.
- Wire values are centralized for most enums through `wireValue` and `fromWire`.
- Circe is already used for API encoding/decoding.
- http4s route layer is thin for battle because `BattleHttp4sRoutes` delegates to `APIMessageRouter`.

### Weak parts

- API messages still depend on injected services through `APIWithTokenContextMessage`.
- Runtime state still uses mutable memory in queue/session.
- `objects` still contains behavior modules, not just ADTs.
- `database` still contains RuleBook caches and not only Table/Initializer.
- Some apiTypes decoder files are more than pure case class + encoder/decoder declarations; they contain request parsing helper logic.
- Error mapping still uses runtime exceptions through `APIMessageError`, which is acceptable at API boundary but not pure ADT all the way down.

## Circe Assessment

Reasonable:

- Using Circe `Encoder`/`Decoder` in `objects/apiTypes` is correct.
- Response encoders using enum `wireValue` are correct because frontend sees stable wire strings.
- Semiauto derivation for replay DTO payload is better than string-building.

Needs improvement:

- `objects/apiTypes` should not own business decisions.
- Request decoders should decode wire payload into already-defined object-layer commands, not redefine duplicate domain types.
- If the user requires apiTypes to contain only case class plus object encoder/decoder, complex custom cursor helpers must either become small shared boundary helpers or move into the corresponding APIMessage companion.

Decision needed:

```text
Should custom Decoder helper functions be allowed in objects/apiTypes as API boundary code, or must all non-trivial decoding move into APIMessage companions?
```

My recommendation:

```text
Allow small Decoder helpers in apiTypes when they only validate wire shape and construct object-layer ADTs. Do not allow service calls or business transitions there.
```

## Cats Effect Assessment

Reasonable:

- `APIMessage.plan(connection): IO[Response]` is a good use-case boundary.
- JDBC calls should be wrapped in `IO.blocking`.
- `for ... yield` comprehension is appropriate for sequencing table calls and typed transition results.

Needs improvement:

- Runtime services currently do synchronous mutable work and are only wrapped in `IO.blocking` by the API layer.
- A stronger final design would make API plans call table operations returning `IO[A]`, then apply pure transitions.
- Avoid using Cats Effect to hide mutation; it should reveal side-effect boundaries, not legitimize mutable service state.

Decision needed:

```text
Should queue/session state become PostgreSQL-backed now, or should it temporarily remain process-memory while only package shape is fixed?
```

My recommendation:

```text
For strict architecture, queue/session must eventually be PostgreSQL-backed or represented as explicit table-backed state. But this should be its own ticket because it changes runtime behavior.
```

## Render / Response Shape Assessment

Backend render surfaces currently mean:

- HTTP JSON response rendering through `APIMessageRouter` + Circe encoders.
- Battle state response rendering through `objects/apiTypes/state/*`.
- Battle result response rendering through `objects/apiTypes/results/*`.
- Replay frame JSON rendering through `microservices/projections/services/BattleReplayFramesJsonRenderer.scala`.

Reasonable:

- State/result API JSON should stay as Circe encoders in `objects/apiTypes`.
- `BattleHttp4sRoutes` should remain thin and should not know response shape details.

Needs improvement:

- Replay frame rendering is currently a service under `microservices`; under strict four layers it needs a new legal home.
- If only four folders are allowed, replay render DTOs should become API response DTOs/codecs under `objects/apiTypes/results` or `objects/apiTypes/replay`, while orchestration should live inside the specific APIMessage that produces replay artifacts.

Decision needed:

```text
Is "render" considered API boundary DTO encoding, or business projection logic?
```

My recommendation:

```text
Treat render as API/persistence boundary formatting. Keep typed DTOs and encoders in apiTypes; keep orchestration in APIMessage or table-backed result planning.
```

## Route Layer Assessment

Current battle http route:

```text
route/battle/BattleHttp4sRoutes.scala
```

This file is already thin:

- It delegates to `APIMessageRouter.routes`.
- It passes `BattleRoutes.apiMessages(context)`.
- It resolves user token through identity service.

Current battle registry:

```text
services/battle/routes/BattleRoutes.scala
```

This file is close to the target because it mostly lists `RegisteredAPIMessage`s.

Mismatch:

- It still registers context-backed APIs through `apiWithTokenAndContext`.
- It imports queue/state service types.
- Therefore route registry still knows runtime services.

Final route target:

```scala
object BattleRoutes {
  val apiMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[BattleQueueJoinAPIMessage, BattleQueueSnapshot],
      apiWithToken[BattleQueueStatusAPIMessage, BattleQueueSnapshot],
      apiWithToken[BattleQueueLeaveAPIMessage, BattleQueueLeaveOutcome],
      apiWithToken[BattleRoomSnapshotAPIMessage, RealtimeRoomSnapshot],
      apiWithToken[BattleRoomHeartbeatAPIMessage, RealtimeRoomSnapshot],
      apiWithToken[BattleStateReadAPIMessage, BattleAggregateState],
      apiWithToken[BattleCommandAPIMessage, BattleCommandAccepted],
      apiWithToken[BattleResultListAPIMessage, BattleResultList],
      apiWithToken[BattleResultRecordAPIMessage, BattleResultRecord]
    )
}
```

This is reasonable only after each APIMessage can execute from `Connection` without service context.

## Database Layer Assessment

Current aligned pattern:

```text
database/combat/BattleCombatRuleTable.scala
database/combat/BattleCombatRuleTableInitializer.scala
database/results/BattleResultTable.scala
database/results/BattleResultTableInitializer.scala
```

Current non-aligned pattern:

```text
database/*/Battle*RuleBook.scala
```

Reason:

- RuleBook files are mutable process caches.
- They are not pure tables.
- They make the database layer act like an in-memory service registry.

Recommended target:

- Keep `Table` and `TableInitializer`.
- If cached reads are needed for performance, introduce an explicit cache decision later; do not hide it as `database/*RuleBook`.
- Prefer each API plan to load required config from tables through `IO`.

Risk:

```text
Directly reading rule rows every tick may be too slow for runtime command processing.
```

Mitigation:

- Use one loaded immutable `BattleRuleSnapshot` per battle/session if accepted.
- But under strict four layers this snapshot must be an object-layer ADT, not a top-level service.

## Reasonableness Of The Requested Route

### Reasonable parts

The four-layer target is reasonable for this repository because:

- The project is not large enough to justify too many top-level battle layers.
- APIMessage routing already exists and can make http4s route files very thin.
- Circe already exists and should replace ad-hoc JSON parsing/rendering.
- The backend currently has too many transitional packages, especially `microservices`.
- `objects` should stop containing executable business services.
- `database` should stop containing process-local service caches.

### Parts that need a careful interpretation

The phrase "api only contains APIMessage files" is reasonable only if:

- APIMessage files are allowed to call private pure helper functions.
- Large shared behavior is migrated in small vertical slices.
- We do not put all command/runtime/game simulation logic into one giant `BattleCommandAPIMessage.scala`.

The phrase "objects only final case class + object encoder decoder" needs adjustment:

- `objects` should allow enums and companion functions like `wireValue/fromWire`.
- `objects/apiTypes` should allow Circe codec objects.
- Pure construction helpers are acceptable if they do not perform business transitions.

The phrase "database only Table and TableInitializer" is directionally correct:

- But current rule config access needs a replacement.
- If RuleBook caches are deleted first, runtime code will lose access to dynamic config.

## Recommended Migration Order

### Phase 0: Decision freeze

Before code migration, decide these three policy points:

1. Whether small custom `Decoder` helpers may stay in `objects/apiTypes`.
2. Whether queue/session state must become PostgreSQL-backed now.
3. Whether battle runtime command simulation can temporarily remain as private API helpers while `microservices` is eliminated.

### Phase 1: Result APIs first

Why:

- `BattleResultListAPIMessage` and `BattleResultRecordAPIMessage` already use `plan(connection)`.
- This area is closest to the final pattern.

Work:

- Ensure result response codecs are in `objects/apiTypes/results`.
- Keep only `BattleResultTable` and `BattleResultTableInitializer` in `database/results`.
- Remove any remaining result repository/service indirection.

### Phase 2: Routes registry simplification

Why:

- `BattleRoutes.scala` can become a stable list of typed API messages once context-free APIs exist.

Work:

- Replace context-backed registrations one API at a time.
- Do not remove `BattleAPIRuntimeContext` until queue/state/command no longer need injected services.

### Phase 3: Queue/room vertical slice

Why:

- Queue is user-visible and currently mutable.

Work:

- Move queue state ADTs and transition results into `objects/queue`.
- Create `database/queue/BattleQueueTable.scala` and `BattleQueueTableInitializer.scala` only if queue is PostgreSQL-backed.
- Convert join/status/leave/room/heartbeat API messages to `plan(connection)`.
- Delete `microservices/queue` only after all callers are migrated.

### Phase 4: State/command vertical slice

Why:

- This is the gameplay core and highest risk.

Work:

- Model stored battle lifecycle as ADTs.
- Convert state read and command submit to `plan(connection)`.
- Move runtime helper logic out of standalone `objects/*Rules.scala`.
- Avoid dumping the entire engine into one API file.

### Phase 5: World/combat/abilities config

Why:

- These are dynamic rules that can be table-backed.

Work:

- Keep typed config ADTs under `objects`.
- Keep table schema/read/write under `database`.
- Remove `RuleBook` caches or replace them with explicit loaded snapshots.

### Phase 6: Projection/replay render

Why:

- Replay render is a boundary formatter and currently lives under `microservices`.

Work:

- Move replay DTOs/codecs under `objects/apiTypes`.
- Keep orchestration inside result/replay API messages.

## Final Recommendation

I recommend accepting the strict four-layer direction, but not accepting a literal "all behavior must be private functions inside APIMessage" interpretation for large gameplay runtime logic.

The safe interpretation is:

```text
api: use-case planning and orchestration
objects: ADTs/value objects/wire codecs only
routes: registry only
database: PostgreSQL table access only
```

Under this interpretation:

- The route is architecture-coherent.
- Type safety improves because service context and mutable in-memory state can be removed gradually.
- Circe and Cats Effect are used at the correct boundaries.
- The biggest risks are queue/session persistence and command/runtime file size.

## Decision Needed Before Code Migration

Please decide:

1. Should `objects/apiTypes` allow custom Decoder helper functions when they only decode wire JSON into object-layer ADTs?
2. Should queue/session runtime state be migrated to PostgreSQL now, or temporarily remain process-memory while only package shape is fixed?
3. For command/runtime, do you accept small private helper modules under `api/command` subpackages, or must everything be inside `BattleCommandAPIMessage.scala`?

