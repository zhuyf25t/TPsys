# Battle service refactor route analysis

## Scope

This report reviews the current `services/battle` structure against the requested target:

- `services/battle/api`
- `services/battle/objects`
- `services/battle/routes`
- `services/battle/database`

It focuses on route reasonableness, module responsibilities, type-safety structure, ADT ownership, Circe codec placement, Cats Effect boundaries, and render/persistence concerns.

No business behavior is changed by this report.

## Current structure evidence

Current top-level battle folders:

- `api`: 9 Scala files.
- `database`: 89 Scala files.
- `objects`: 47 Scala files.
- `routes`: 2 Scala files.

Current API groups:

- `api/command`: `BattleCommandAPIMessage.scala`
- `api/queue`: `BattleQueueJoinAPIMessage.scala`, `BattleQueueLeaveAPIMessage.scala`, `BattleQueueStatusAPIMessage.scala`
- `api/results`: `BattleResultListAPIMessage.scala`, `BattleResultRecordAPIMessage.scala`
- `api/room`: `BattleRoomHeartbeatAPIMessage.scala`, `BattleRoomSnapshotAPIMessage.scala`
- `api/state`: `BattleStateReadAPIMessage.scala`

Current `database` groups:

- `abilities`: skill/pickup/slow-field rules.
- `actors`: player input, player runtime, bot rules, lifecycle.
- `combat`: weapon, fire, projectile, hit, damage, terminal rules.
- `projections`: finish projection and replay JSON rendering.
- `queue`: matchmaking queue, room waiting, heartbeat, room/session bootstrap.
- `results`: result repositories, file/Postgres adapters, table, table initializer.
- `runtime`: tick step, finalization, replay frame recorder, event factory, retention, time.
- `session`: battle state service, command acceptance, stored battle initialization/advance, finish projection status.
- `world`: map catalog, collision, geometry, movement, spawn layout.

## Route design

Current route chain:

- `route/battle/BattleHttp4sRoutes.scala`
- `services/battle/routes/BattleRoutes.scala`
- `system/api/APIMessageRouter.scala`
- `services/battle/api/**/**APIMessage.scala`

This is directionally good.

`BattleHttp4sRoutes` is now thin. It selects result backend, injects identity token resolution, and delegates the actual `/api/{apiName}` routing to `APIMessageRouter`.

`BattleRoutes` is a registry. It uses `RegisteredAPIMessage.apiWithToken` and `apiWithTokenAndContext` to register typed API messages. The API names are derived from class names by `APIMessage.apiNameFromClassName`, so no manual path rewrite is needed.

`APIMessageRouter` owns the generic HTTP mechanics:

- `POST /api/{apiName}` path matching.
- JSON body decoding through http4s-circe.
- user token extraction and replacement with typed `UserId`.
- `Connection` resource use.
- `APIMessageError` to HTTP status mapping.

This route model is reasonable because battle-specific route code no longer manually repeats method/path/body/status logic in every endpoint.

## APIMessage design

Current API files follow the requested shape closely:

- each API is a `final case class XXXAPIMessage(...)`;
- each message extends an APIMessage trait;
- each message implements `plan(...): IO[Response]`;
- service effects are wrapped with `IO.blocking`;
- request decoding is provided by Circe `Decoder`;
- response encoding is registered through `objects/apiTypes`.

Example:

- `BattleCommandAPIMessage` stores `BattleCommandRequest`, calls `BattleStateService.acceptCommand`, and maps `BattleCommandSubmitError` into `APIMessageError`.
- `BattleQueueJoinAPIMessage` stores `BattleQueueJoinCommand`, authorizes the join, then calls `BattleQueueService.join`.

Remaining concern:

- API planners still directly know service error enums such as `BattleCommandSubmitError` and `BattleQueueJoinAuthorizationError`.
- This is acceptable for the current transitional route layer, but if the goal is stricter domain separation, each API group should eventually have a small error mapper local to that group.

## Objects and ADT ownership

Current `objects` layer is now the authoritative battle type source.

Important ADT/value object categories:

- IDs: `TicketId`, `QueueRequestId`, `RoomId`, `BattleId`, `PlayerId`, `HeroId`, `ProjectileId`, `SlowFieldId`, `PickupId`, `BattleEventId`, `BattleResultId`.
- scalar wrappers: `EpochMillis`, `DurationMillis`, `ElapsedMillis`, `BattleTick`, `ClientCommandSeq`, `Rating`, `RatingDelta`, `Score`, `HitPoints`, `AmmoCount`, `CooldownMillis`, `FacingRadians`, `Radius`, `Damage`.
- enums: `BattleMode`, `BattlePhase`, `WeaponKind`, `ProjectileKind`, `SkillKind`, `PickupKind`, `BattleCommandStatus`, `BattleCommandReason`, `SkillOutcomeStatus`, `SkillOutcomeReason`, `ProjectileTerminalReason`, `BattleEventKind`.
- command/query ADTs: `BattleQueueJoinCommand`, `BattleQueueStatusQuery`, `BattleQueueLeaveCommand`, `RealtimeRoomHeartbeatCommand`, `BattleRoomSnapshotQuery`, `BattleStateReadQuery`, `BattleCommandRequest`, `BattleResultListQuery`, `BattleResultRecordCommand`.
- state ADTs: `BattleAggregateState`, `BattlePlayerState`, `BattleProjectileState`, `BattlePickupState`, `BattleSlowFieldState`, `BattleWeaponState`, `BattleQueueSnapshot`, `RealtimeRoomSnapshot`.
- result/replay ADTs: `BattleResultRecord`, `BattleResultList`, `BattleReplayFrameState`.

The recent duplicate-removal pass moved API request ownership in the correct direction:

- API messages now store object-layer command/query ADTs directly.
- `objects/apiTypes` no longer declares request/response `case class` duplicates for battle API contracts.
- codec namespaces remain, but they decode into authoritative object ADTs.

This improves contract-level type safety because API drift now has fewer duplicated request/response structures to maintain.

## Circe codec placement

Current codec placement:

- request decoders live in `objects/apiTypes/**`.
- response encoders live in `objects/apiTypes/**`.
- API message decoders live beside each API message because they must inject `UserId` and map API-specific decode failures.
- replay JSON rendering uses Circe derivation in `database/projections/BattleReplayFramesJsonRenderer.scala`.

This is mostly reasonable.

The important distinction is:

- `objects` should own business ADTs.
- `objects/apiTypes` should own wire codecs for those ADTs.
- `api` should own API message decoding only when the message contains injected transport context such as `userId`.

One concern:

- `BattleReplayFramesJsonRenderer` declares private wire payload case classes inside a database/projection file. That is acceptable as a projection adapter, but it is not a pure domain object. If replay output becomes a stable public contract, those payloads should move into `objects/apiTypes/replay` or `objects/replay` plus codecs.

## Cats Effect and side-effect boundaries

Current effect model:

- HTTP route execution is `IO`.
- API planner `plan(...)` returns `IO[Response]`.
- blocking service/database calls are wrapped with `IO.blocking`.
- JDBC `Connection` is passed through APIMessage planning.
- `PostgresSupport.withTransactionIO` is used for transactional result save.

This is a clear improvement over hand-written synchronous route logic.

Remaining issues:

- `BattleQueueService` and `BattleStateService` are synchronous mutable in-memory services using `lock.synchronized` and `var`.
- This is acceptable as an adapter/runtime implementation, but it should not be treated as pure domain.
- The current package name `database` makes these mutable runtime services look like persistence. That is misleading.

## Render and projection technology

Backend render/projection currently appears in two forms:

- API response rendering through Circe encoders in `objects/apiTypes`.
- replay artifact rendering in `database/projections/BattleReplayFramesJsonRenderer`.

`BattleReplayFramesJsonRenderer`:

- reads `BattleAggregateState` and replay frames;
- normalizes/falls back timeline data;
- converts domain objects to private wire payloads;
- uses Circe `deriveEncoder` and `.asJson.noSpaces`;
- does not advance battle logic itself.

This is the correct side-effect direction for a renderer: domain state in, JSON artifact out.

Problems:

- The file currently sits under `database/projections`, but it is a render/projection adapter, not a database table.
- Some comments in this area are mojibake and not reviewable.
- If the final architecture insists that `database` only contains `Table` and `TableInitializer`, this renderer must move out of `database`.

## Database package correctness

Literal database code exists mainly in `database/results`:

- `BattleResultTable`
- `BattleResultTableInitializer`
- `PostgresBattleResultRepository`
- `FileBattleResultRepository`
- `InMemoryBattleResultRepository`
- `BattleResultRepository`
- mapper/ordering/parser/renderer helpers

This part matches the requested `database` responsibility reasonably well.

However, most of `database` is not database code:

- `database/queue` is queue application/runtime service.
- `database/session` is battle session orchestration and mutable in-memory state.
- `database/runtime` is engine runtime.
- `database/world` is map/collision/geometry/movement rules.
- `database/combat` is combat rules.
- `database/actors` is player/bot/input/lifecycle rules.
- `database/abilities` is skill/pickup/slow-field rules.
- `database/projections` is artifact projection/rendering.

This is the largest mismatch against the requested route.

If `database` means "battle implementation layer", current structure is coherent enough as a transitional layout.

If `database` means "persistence only", current structure is not acceptable and should not be expanded further.

## Business dependency direction

Good current direction:

- `routes` imports `api`, `objects`, and service interfaces.
- `api` imports `objects`, `objects/apiTypes`, and service interfaces.
- `objects` mostly does not import `api`, `routes`, or `database`.
- `objects/apiTypes` imports `objects` and Circe.
- `database` imports `objects`.

Risky current direction:

- `database/queue` imports `database/session`.
- `database/session` imports `database/runtime`.
- `database/runtime` imports `database/abilities`, `database/actors`, `database/combat`, `database/world`.
- engine rule packages import each other densely.

This means the subpackages under `database` are not independent microservices. They are one cohesive battle runtime split by concern. Treating them as independent services now would create artificial boundaries and likely force many imports back across those boundaries.

## Reasonableness of the requested route

Reasonable parts:

- Thin route registry: yes.
- One `XXXAPIMessage.scala` per endpoint: yes.
- API path generated from class name: yes.
- `objects` as authoritative ADT source: yes.
- `objects/apiTypes` for Circe request/response codecs: yes.
- `BattleRoutes.scala` as registry of supported API messages: yes.
- `database/results` with `Table` and `TableInitializer`: yes.

Parts needing correction before implementation:

- "database should contain the implementation" conflicts with "database should contain Table and TableInitializer" if battle runtime remains inside only four top-level folders.
- "object should contain final case class + encoder/decoder" should be refined: business objects should stay codec-free where possible; codecs should stay in `objects/apiTypes`.
- "avoid different business logic calling each other" is not fully realistic inside the engine. Combat, movement, collision, pickup, bot, and runtime tick logic must collaborate. The safer rule is one-way orchestration: runtime may call world/combat/actors/abilities, but those lower rule packages should not call session/queue/api/routes.
- API planners should not contain large business rules. They should decode, authorize, call a service/port, and map results.

## Recommended decision

Recommended path:

1. Keep the current top-level `api/objects/routes/database` temporarily because it already compiles and matches the requested outer shape.
2. Treat `database` as a transitional "implementation adapter" only until you decide whether literal persistence-only naming is required.
3. Do not move more engine/session/queue code into `database` if your final meaning of database is persistence-only.
4. Continue tightening type safety inside current structure first:
   - keep removing duplicate API DTOs;
   - keep API messages using object-layer command/query ADTs;
   - keep Circe codecs in `objects/apiTypes`;
   - keep API routes registered through `BattleRoutes`;
   - keep `APIMessageRouter` generic.
5. After this stabilizes, choose one of the two structural options below.

## Structural options needing your decision

Option A: four-folder compromise.

- Keep `api/objects/routes/database`.
- Accept that `database` means battle implementation, not literal database.
- Pros: smallest migration, likely keeps compile stable.
- Cons: misleading name, weak architecture semantics, harder future review.

Option B: literal database semantics.

- Keep `database` only for `Table`, `TableInitializer`, repositories, persistence mappers, file/Postgres adapters.
- Allow a separate implementation location for queue/session/runtime/rules/projections.
- Pros: clearer architecture and side-effect boundaries.
- Cons: violates the strict four-folder-only route unless you approve another top-level folder or microservice package.

Option C: business microservice packages under `services/battle/microservices`.

- Each microservice owns its `api`, `objects`, `routes`, and `database`.
- Cross-microservice calls go through APIMessagePlanner/APIMessage contracts instead of internal imports.
- Pros: closer to strict service isolation.
- Cons: much bigger migration; current engine packages are too tightly coupled to split safely in one pass.

## Immediate next safe ticket

Recommended next ticket:

- `BE-BATTLE-DATABASE-SEMANTICS-02`

Goal:

- Classify every `database/*` subpackage as persistence, runtime rule, application service, projection renderer, or temporary implementation.
- Do not move files yet.
- Use the classification to choose Option A, B, or C.

Acceptance:

- A table exists mapping every current `database` subpackage to its true responsibility.
- A decision is recorded about whether `database` remains a broad implementation bucket or becomes persistence-only.
