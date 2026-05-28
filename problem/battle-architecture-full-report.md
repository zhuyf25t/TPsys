# Battle architecture full report

Updated: 2026-05-28

This report reflects the current worktree, not the older committed layout.

The purpose is to decide whether the requested route is reasonable before continuing the `services/battle` refactor.

## 1. Current Shape

Current `backend/src/main/scala/services/battle` top-level folders:

| Folder | Scala files | Current meaning |
| --- | ---: | --- |
| `api` | 9 | `XXXAPIMessage.scala` endpoint planners |
| `objects` | 71 | ADTs, value objects, API codecs, and some pure rule modules |
| `routes` | 2 | battle API registry and runtime context |
| `database` | 18 | PostgreSQL table access plus temporary rule books |
| `microservices` | 53 | remaining queue/session/runtime/world/combat/actors/projections services |

Current status:

- The outer shape is moving toward `api / objects / routes / database`.
- The migration is not complete because `microservices` still owns most runtime behavior.
- `abilities` and `results` have effectively been moved out of `microservices`, but queue/session/runtime/world/combat/actors/projections are still active there.
- The code compiles in the current state based on the latest verified `sbt compile` and contract test run before this report.

## 2. Runtime Request Flow

Current request flow:

```text
HTTP POST /api/{apiName}
  -> route/battle/BattleHttp4sRoutes
  -> system/api/APIMessageRouter
  -> services/battle/routes/BattleRoutes.apiMessages(context)
  -> services/battle/api/**/XXXAPIMessage.plan(...)
  -> queue/session/result service or table
  -> objects/apiTypes Circe Encoder
  -> JSON response
```

Important details:

- API paths are already derived by `APIMessage.apiNameFromClassName`.
- `BattleRoutes` no longer needs rewrite logic.
- `BattleHttp4sRoutes` is thin and delegates to `APIMessageRouter`.
- Token resolution is handled before `plan` by replacing `userToken` with typed backend `userId`.
- Most runtime APIs still use `APIWithTokenContextMessage`, meaning they need injected queue/session services.
- Results APIs use `APIWithTokenMessage` and directly use JDBC `Connection`.

## 3. Module Logic

| Business area | Current files | What it manages | Current problem |
| --- | --- | --- | --- |
| Queue | `api/queue`, `objects/queue`, `microservices/queue` | matchmaking, ticket, room waiting, heartbeat, join/leave snapshot | service is still mutable in-memory state with `AtomicReference` and `synchronized` |
| Room | `api/room`, shared queue objects | waiting-room snapshot and heartbeat | API is thin, but implementation is still queue service |
| Session | `microservices/session`, `objects/core`, `objects/result` | battle session lookup, state read, command acceptance, finish projection state | has `var battles` and lock-based state machine |
| Runtime | `microservices/runtime`, `objects/runtime` | tick advancement, command application, finalization, replay-frame retention | orchestration still imports actors/combat/world/abilities |
| World | `objects/world`, `database/world`, `microservices/world` | map rules, collision, spawn, motion, map spec loading | rule tables exist, but catalog/motion/collision still live in `microservices` |
| Combat | `objects/combat`, `database/combat`, `microservices/combat` | weapon config, fire, projectile creation, projectile motion/impact | pure projectile helpers partly moved; weapon runtime still service-side |
| Actors | `objects/actors`, `database/actors`, `microservices/actors` | player lifecycle, input, bot movement/fire behavior | bot/player runtime still imports world and combat services |
| Abilities | `objects/abilities`, `database/abilities` | skill rules, pickups, slow field | closest to target shape; remaining issue is rule book cache in database |
| Results | `api/results`, `objects/result`, `database/results` | battle result list/record in PostgreSQL | closest to requested `APIMessage + Table` shape |
| Projections | `microservices/projections` | finish settlement, replay JSON, mail/replay publication | still a separate service block; contains render logic |

## 4. Type-Safety Structure

Current type-safety evidence from the battle package:

| Construct | Approximate count |
| --- | ---: |
| `enum` | 38 |
| `final case class` | 178 |
| `given Encoder` | 30 |
| `given Decoder` | 28 |
| `cats.effect.IO` usage | 19 files/lines matched |
| `IO.blocking` usage | 35 matches |
| `AtomicReference` usage | 15 matches |
| `var` usage | 2 matches |
| `synchronized` usage | 10 matches |

Positive modeling:

- Core identifiers and scalars are value objects: `BattleId`, `PlayerId`, `TicketId`, `RoomId`, `DurationMillis`, `CooldownMillis`, `HitPoints`, `Score`, `Radius`, etc.
- Finite game states are modeled as enums/ADTs: `BattlePhase`, `MatchmakingRoomPhase`, `BattleArtifactStatus`, `WeaponKind`, `ProjectileKind`, `SkillKind`, `PickupKind`, `ProjectileTerminalReason`, `BattleEventKind`.
- Queue lifecycle is already an ADT:

```scala
private[battle] enum QueueRoomLifecycle {
  case Waiting
  case Active(session: BattleSessionDescriptor)
  case Finished(completedAt: EpochMillis, session: Option[BattleSessionDescriptor])
}
```

- Queue start decision is also explicit:

```scala
private[battle] enum QueueRoomStartDecision {
  case Start
  case Keep
}
```

Remaining modeling gaps:

- `BattleAPIRequestError` is a broad shared enum. It is convenient, but it mixes queue, room, command, result, and generic parse errors.
- Some response encoders still expose Boolean projections, for example `resultReady`, `replayReady`, `alive`, and `isBot`. This is acceptable at the JSON boundary, but the authoritative model should remain enum/value-object based.
- Mutable service state still exists in queue/session implementation, so illegal runtime transitions are not fully prevented by ADTs.
- `objects` currently contains pure rule modules, not only passive case classes/enums/codecs. This conflicts with the strict interpretation of "objects only has final case class + object encoder/decoder + enum".

## 5. Circe Boundary

Current good points:

- `objects/apiTypes` owns most request decoders and response encoders.
- APIMessage files import `given Decoder` from `objects/apiTypes`, instead of duplicating field parsing in routes.
- Response JSON is mostly produced through Circe `Encoder`, `Encoder.forProductN`, `deriveEncoder`, `Json.obj`, or `.asJson`.
- Map spec JSON in `database/world/BattleWorldRuleTable.scala` is decoded using Circe decoders rather than regex/string parsing.
- Replay-frame rendering uses Circe payload encoders and `.asJson.noSpaces`.

Current problems:

- Some `apiTypes` files contain substantial decode helpers, not just `final case class + object Encoder/Decoder`.
- `BattleCommandRequestApiTypes.scala` is especially heavy because command payload validation is complex.
- State response encoders manually build large `Json.obj` payloads. This is still typed Circe, but it is render/presenter logic, not plain DTO declaration.
- `database/world/BattleWorldRuleTable.scala` contains private JSON DTOs and conversion logic inside a table file. That makes the database layer more than "Table + TableInitializer".

Recommendation:

- `objects/apiTypes` should contain request/response DTOs and Circe codecs, but not APIMessage planners.
- It is too strict to say apiTypes can only contain `XXXResponse`; request DTO/decoder is also part of the API contract.
- It is reasonable to forbid business planning and service calls inside `apiTypes`.
- Heavy decoders can be split into small `RequestCodec` helpers only if the user allows more than two file shapes in apiTypes. If not, keep helpers private in the companion object.

## 6. Cats Effect and Side-Effect Boundary

Current good points:

- APIMessage `plan` returns `IO[Response]`.
- Blocking service/database calls are generally lifted with `IO.blocking`.
- `route/battle/BattleHttp4sRoutes.scala` uses http4s `HttpRoutes[IO]`.
- `APIMessageRouter` centralizes JSON decoding, token injection, and error conversion.

Current problems:

- Queue/session are still in-memory services with lock/mutable state, not PostgreSQL-backed state.
- `BattleStateService` mutates `private var battles`.
- `InMemoryBattleQueueService` uses `AtomicReference` plus `synchronized`.
- Rule books in `database/*/Battle*RuleBook.scala` use `AtomicReference` as process-local caches.
- Several `plan` functions call injected services, so `Connection` is not yet the single effect boundary for all battle APIs.

Implication:

- Results APIs are closest to the sample style.
- Queue/session/state/command are still transitional because they depend on runtime services.
- Fully migrating them to `plan(connection: Connection)` requires deciding where live battle session state belongs: PostgreSQL tables, an in-memory runtime port, or a typed process runtime that is explicitly outside database.

## 7. Render Technology

There are two backend "render" surfaces:

1. HTTP/API JSON rendering.
2. Replay/projection JSON rendering.

HTTP/API rendering:

- `objects/apiTypes/*` provides Circe encoders for battle queue/status/room/state/command/result responses.
- `APIMessageRouter` converts the typed response into JSON through `Encoder[Response]`.
- This is the right direction because route files do not manually build response strings.

Replay/projection rendering:

- `microservices/projections/services/BattleReplayFramesJsonRenderer.scala` converts `BattleAggregateState` and replay frame state into replay-player JSON.
- It uses typed payload case classes plus Circe encoders and `.asJson.noSpaces`.
- It is still located in `microservices/projections`, so it violates the desired four-folder target.

Recommended placement:

- If "render" means wire response DTOs, keep it under `objects/apiTypes`.
- If "render" means replay artifact generation, it should not be in `objects` if objects are passive. It should either:
  - stay as projection/application logic under an allowed subfolder, or
  - become part of `api/results` only if replay artifact creation is strictly tied to result API planning.

## 8. Dependency Direction

Current desired direction:

```text
route/battle
  -> services/battle/routes
  -> services/battle/api
  -> services/battle/objects + services/battle/database
  -> system/database, system/api
```

Current actual direction includes:

```text
api -> microservices queue/session
routes -> microservices queue/session
microservices/runtime -> microservices actors/combat/world
microservices/actors -> microservices world/combat
microservices/combat -> microservices world
microservices/session -> microservices runtime
microservices/queue -> microservices session
microservices/projections -> database/results + replay/mail ports
```

Main risk:

- The current `microservices` package is not a clean set of independent services. It is a graph of implementation helpers.
- Moving these files mechanically under `api/object/route/database` without changing dependencies would only hide the dependency problem.

## 9. Route Reasonability Analysis

The requested route is partly reasonable:

```text
services/battle/
  api/
  objects/
  routes/
  database/
```

Reasonable parts:

- `api` should contain only `XXXAPIMessage.scala` entry files.
- `routes/BattleRoutes.scala` should only register API messages.
- `objects` should own authoritative ADTs, value objects, enums, and API DTO/codec definitions.
- `database` should own PostgreSQL table access and table initialization.
- Business subdomains should be expressed inside each layer, for example `api/queue`, `objects/queue`, `database/queue`.
- API paths should be class-name derived; no rewrite.

Problematic parts:

- The phrase "objects only final case class + object encoder decoder + unified enum" is too strict if all pure battle rules must also leave `microservices`.
- If no `services`, no `application`, no `engine`, and no `runtime` package is allowed, then all orchestration has to move into `XXXAPIMessage.plan` private functions. That will create god APIMessage files.
- `database` cannot be only `Table` and `TableInitializer` if runtime queue/session state remains in memory. Either queue/session state must be stored in PostgreSQL, or a non-database runtime boundary must remain.
- A strict "database has only Table/Initializer" rule conflicts with the current rule-book caches. Those caches must either be removed after loading config into typed runtime dependencies, or explicitly moved out of database.

## 10. Recommended Decision

Recommended route:

```text
services/battle/
  api/
    queue/*APIMessage.scala
    room/*APIMessage.scala
    state/*APIMessage.scala
    command/*APIMessage.scala
    results/*APIMessage.scala
  objects/
    core/
    queue/
    room/
    session/
    runtime/
    world/
    combat/
    actors/
    abilities/
    result/
    replay/
    apiTypes/
    BattleEnums.scala
  routes/
    BattleRoutes.scala
    BattleAPIRuntimeContext.scala
  database/
    queue/*Table.scala
    queue/*TableInitializer.scala
    session/*Table.scala
    session/*TableInitializer.scala
    runtime/*Table.scala
    runtime/*TableInitializer.scala
    world/*Table.scala
    world/*TableInitializer.scala
    combat/*Table.scala
    combat/*TableInitializer.scala
    actors/*Table.scala
    actors/*TableInitializer.scala
    abilities/*Table.scala
    abilities/*TableInitializer.scala
    results/*Table.scala
    results/*TableInitializer.scala
```

But with one necessary clarification:

- Either allow `objects` to include pure transition/rule objects, or allow one additional non-database application/runtime layer.
- If both are forbidden, the refactor will make `api` too large and less type-safe.

## 11. Suggested Migration Order

1. Finish queue/session API shape.
   - Goal: APIMessage files depend on typed objects and a clear runtime/table boundary.
   - Do not first move all files; first model state transitions as ADTs.

2. Split queue state.
   - Move queue state ADTs into `objects/queue`.
   - Replace free-form state checks with `QueueRoomLifecycle` transitions.
   - Decide whether queue runtime state goes to PostgreSQL or remains a typed process runtime.

3. Split session state.
   - Move session state ADTs into `objects/session`.
   - Replace `var battles` with a typed store boundary.
   - Avoid direct queue/session mutual implementation imports.

4. Move projection renderer.
   - Keep replay JSON rendering typed with Circe.
   - Place it in a layer explicitly allowed by the final decision.

5. Reduce database.
   - Leave only PostgreSQL tables and initializers in `database`.
   - Remove rule books after config loading has a clean owner.

6. Remove `microservices`.
   - Only after all remaining files have a correct home and no package imports remain.

## 12. Decision Needed Before More Code Movement

I need one architecture decision before continuing broad migration:

Option A: strict four-layer only.

- `api`, `objects`, `routes`, `database` are the only battle top-level folders.
- Pure rule/application logic must live either in `objects` or private functions inside APIMessage.
- This matches the folder demand but risks fat API files or non-passive objects.

Option B: four required layers plus one explicit runtime/application layer.

- Keep `api`, `objects`, `routes`, `database`.
- Add one narrow `runtime` or `application` folder for battle simulation orchestration.
- This is cleaner architecturally, but it violates the strictest interpretation of "not under battle directly".

Option C: strict four-layer, but allow pure rule objects inside `objects`.

- `objects` contains ADTs/value objects plus pure transition modules like `BattleTimeRules`, `BattleGeometry`, `BattleProjectileMotionRules`.
- Side effects stay out of `objects`.
- This is the most practical route if no fifth folder is allowed.

My recommendation is Option C.

Reason:

- It preserves the requested four-layer shape.
- It avoids turning APIMessage files into huge service implementations.
- It keeps domain transitions pure and testable.
- It lets `database` shrink toward PostgreSQL `Table` and `TableInitializer`.

## 13. Current Highest-Risk Items

- `microservices/session/services/BattleStateService.scala`: mutable `var battles`, session lifecycle, command acceptance, and projection completion are all in one service.
- `microservices/queue/services/BattleQueueService.scala`: queue runtime state is lock-based and stateful, even though queue lifecycle ADTs already exist.
- `microservices/projections/services/BattleReplayFramesJsonRenderer.scala`: typed Circe rendering exists but is still in the wrong package.
- `database/world/BattleWorldRuleTable.scala`: Table + JSON conversion + map manifest decoding are mixed in one file.
- `database/*/Battle*RuleBook.scala`: process-local cache in database package; this should be removed or relocated after config ownership is decided.

## 14. Conclusion

The route is directionally correct, but the strict wording needs one adjustment.

The safe target is:

```text
routes = registry only
api = APIMessage planners only
objects = ADTs/value objects/apiTypes/pure transition rules
database = PostgreSQL table access and initialization only
```

Do not continue with mechanical file moves until the user confirms whether pure rules may live in `objects`.

If confirmed, the next implementation ticket should be:

```text
BE-BATTLE-QUEUE-STATE-ADT-01
```

Goal:

- Keep queue under the four-layer model.
- Move remaining queue state/service contracts out of `microservices`.
- Preserve `QueueRoomLifecycle` as the authoritative ADT.
- Reduce mutable state surface without rewriting frontend/backend contract.
