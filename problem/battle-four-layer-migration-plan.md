# Battle four-layer migration plan

Updated: 2026-05-28

Superseded by:

```text
problem/battle-five-layer-microservices-decision.md
```

Status:

```text
This document describes the previous four-layer-only route.
The user later changed the architecture decision to allow a fifth top-level microservices layer.
Use this document only for its layer-boundary analysis, not as the final migration target.
```

## Purpose

This document turns the four-layer rationality report into a concrete migration sequence.

The target is:

```text
services/battle/api
services/battle/objects
services/battle/routes
services/battle/database
```

The migration must not recreate the old split under a different name. Business subdomains may exist, but only under the four accepted layers.

## Non-negotiable Invariants

### Layer shape

Allowed:

```text
services/battle/api/{queue,room,state,command,results,...}
services/battle/objects/{queue,room,state,command,combat,world,...}
services/battle/objects/apiTypes/{queue,room,state,command,results,...}
services/battle/routes
services/battle/database/{queue,session,runtime,combat,world,abilities,results,...}
```

Forbidden top-level packages:

```text
services/battle/microservices
services/battle/application
services/battle/runtime
services/battle/engine
services/battle/services
```

### Dependency direction

Preferred direction:

```text
routes -> api -> database
routes -> api -> objects
database -> objects
apiTypes -> objects
```

Forbidden direction:

```text
objects -> api
objects -> routes
objects -> database
objects -> http4s
objects -> JDBC
objects -> IO
database -> routes
database -> APIMessage
routes -> business rules
```

Cross-subdomain calls should be minimized. If a queue API needs session output, it should produce a typed ADT or call a narrow table/API planner boundary, not directly import another subdomain's internal service.

### Object layer

Allowed:

- `final case class`.
- `enum`.
- sealed ADT.
- value object.
- companion construction helpers.
- `wireValue` / `fromWire`.
- Circe `Encoder` / `Decoder` only when the file is an API/wire contract or simple enum codec.

Forbidden:

- standalone `*Rules.scala` behavior modules.
- process-local state.
- `AtomicReference`.
- `synchronized`.
- JDBC.
- `IO`.
- route/http4s imports.
- APIMessage planners.

### API layer

Allowed:

- `XXXAPIMessage.scala`.
- `final case class XXXAPIMessage(...) extends APIWithTokenMessage[XXXResponse]`.
- `override def plan(connection: Connection): IO[XXXResponse] = ...`.
- private pure helper functions inside the same APIMessage file.
- private effect helpers that clearly wrap table calls with `IO`.

Target pattern:

```scala
final case class BattleQueueJoinAPIMessage(
  userId: UserId,
  command: BattleQueueJoinCommand
) extends APIWithTokenMessage[BattleQueueSnapshot] {
  override def plan(connection: Connection): IO[BattleQueueSnapshot] =
    for
      request <- validateRequest(command)
      state <- BattleQueueTable.load(connection)
      transition <- IO.fromEither(applyJoin(state, request))
      _ <- BattleQueueTable.save(connection, transition.nextState)
    yield transition.snapshot
}
```

Avoid:

- `APIWithTokenContextMessage` for battle final target.
- service injection through `BattleAPIRuntimeContext`.
- calling `microservices.*`.
- adding new public helper modules in `api` that are not APIMessage files unless the user explicitly allows command/runtime private planner decomposition.

### Route layer

Target:

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

Current blocker:

```text
BattleRoutes still registers seven context-backed APIs through apiWithTokenAndContext.
```

### Database layer

Allowed:

```text
XXXTable.scala
XXXTableInitializer.scala
```

Accepted role:

- PostgreSQL table creation.
- SQL read/write.
- conversion between SQL rows and typed object-layer ADTs.

Forbidden:

- `RuleBook` process caches.
- in-memory repositories.
- business state machines.
- combat/skill/bot runtime rules.

Current blockers:

```text
BattlePickupRuleBook
BattleSkillRuleBook
BattleBotRuleBook
BattleCombatRuleBook
BattleRuntimeRuleBook
BattleWorldRuleBook
```

## Current Gap Summary

| Area | Current issue | Target |
| --- | --- | --- |
| `routes` | imports queue/state services and registers context APIs | typed registry only |
| `api/queue` | calls injected `BattleQueueService` | `plan(connection)` |
| `api/room` | calls injected `BattleQueueService` | `plan(connection)` |
| `api/state` | calls injected `BattleStateService` | `plan(connection)` |
| `api/command` | calls injected `BattleStateService` | `plan(connection)` |
| `objects` | still has standalone rules | ADTs and codecs only |
| `database` | has `RuleBook` caches | Table/Initializer only |
| `microservices` | 53 files still own runtime behavior | eliminated |

## Proposed Migration Tickets

### Ticket 1: Results are the first stable slice

ID:

```text
BE-BATTLE-FOUR-LAYER-RESULTS-01
```

Goal:

- Make result APIs the model slice for the final pattern.

Boundary:

```text
services/battle/api/results
services/battle/objects/result
services/battle/objects/apiTypes/results
services/battle/database/results
services/battle/routes/BattleRoutes.scala
```

Expected change:

- Keep result APIs as `APIWithTokenMessage`.
- Ensure result API messages use `plan(connection)` only.
- Ensure database/results contains only `BattleResultTable` and `BattleResultTableInitializer`.
- Do not touch queue/state/command.

Acceptance:

- No result API imports `microservices`.
- No result database file except table and initializer remains active.
- `sbt compile` passes.
- contract runner passes.

Risk:

- Low. This is already closest to target.

### Ticket 2: Route registry cleanup gate

ID:

```text
BE-BATTLE-FOUR-LAYER-ROUTES-02
```

Goal:

- Identify exactly which API messages still require context before removing `BattleAPIRuntimeContext`.

Boundary:

```text
services/battle/routes
services/battle/api
```

Expected change:

- No behavior change unless one API is already context-free.
- Add or update documentation/tests proving context-backed battle APIs list.

Acceptance:

- `BattleRoutes` has a clear split: context-free vs context-backed.
- This is temporary and marked as migration debt.

Risk:

- Low.

### Ticket 3: Queue state ADT consolidation

ID:

```text
BE-BATTLE-FOUR-LAYER-QUEUE-ADT-03
```

Goal:

- Move queue finite states and transition result types into `objects/queue`.
- Remove primitive or implicit queue state transitions where possible.

Boundary:

```text
services/battle/objects/queue
services/battle/api/queue
focused current queue callers
```

Expected change:

- `Waiting`, `Active`, `Finished` queue/room states are explicit ADTs.
- Join/status/leave/heartbeat return explicit transition/result ADTs, not Boolean or string status.

Acceptance:

- No new raw string status.
- No new Boolean business result.
- compile and contracts pass.

Risk:

- Medium. Queue service is currently mutable and tightly coupled to session seed creation.

### Ticket 4: Queue table-backed planner

ID:

```text
BE-BATTLE-FOUR-LAYER-QUEUE-DB-04
```

Goal:

- Convert one queue API, preferably `BattleQueueStatusAPIMessage`, from injected service to `plan(connection)`.

Boundary:

```text
services/battle/api/queue/BattleQueueStatusAPIMessage.scala
services/battle/database/queue
services/battle/objects/queue
services/battle/routes/BattleRoutes.scala
```

Expected change:

- Add `BattleQueueTable` and `BattleQueueTableInitializer` if not present.
- Read queue ticket/room from PostgreSQL.
- Register `BattleQueueStatusAPIMessage` without context.

Acceptance:

- `BattleQueueStatusAPIMessage` extends `APIWithTokenMessage[BattleQueueSnapshot]`.
- It does not import `microservices`.
- `BattleRoutes` uses `apiWithToken` for status.

Risk:

- High if queue persistence schema is not finalized.

### Ticket 5: Room heartbeat after queue table is stable

ID:

```text
BE-BATTLE-FOUR-LAYER-ROOM-DB-05
```

Goal:

- Convert room snapshot and heartbeat to table-backed API messages.

Boundary:

```text
services/battle/api/room
services/battle/database/queue or database/room
services/battle/objects/queue
services/battle/objects/apiTypes/room
```

Expected change:

- Heartbeat updates are explicit ADT transitions.
- No injected `BattleQueueService`.

Acceptance:

- `BattleRoomSnapshotAPIMessage` and `BattleRoomHeartbeatAPIMessage` extend `APIWithTokenMessage`.
- No direct queue service import.

Risk:

- Medium/high. Room heartbeat and queue start rules are coupled.

### Ticket 6: State/session persistence decision

ID:

```text
BE-BATTLE-FOUR-LAYER-SESSION-DECISION-06
```

Goal:

- Decide and document whether active battle state lives in PostgreSQL or a temporary table-backed JSON blob.

Boundary:

```text
services/battle/objects/core
services/battle/objects/result
services/battle/database/session
services/battle/api/state
```

Expected change:

- If accepted, add `BattleSessionTable` and initializer.
- Define stored battle lifecycle ADT.

Acceptance:

- No `private var battles`.
- No process-local active battle map.

Risk:

- High. This changes runtime semantics.

### Ticket 7: Command runtime decomposition

ID:

```text
BE-BATTLE-FOUR-LAYER-COMMAND-07
```

Goal:

- Convert command API to `plan(connection)` without creating a god file.

Boundary:

```text
services/battle/api/command
services/battle/objects/command
services/battle/objects/combat
services/battle/objects/abilities
services/battle/objects/world
services/battle/database/{session,runtime,combat,abilities,world}
```

Expected change:

- `BattleCommandAPIMessage` loads current session state and dynamic rules from tables.
- It applies pure transitions.
- It stores next session state.
- It returns `BattleCommandAccepted`.

Acceptance:

- No `BattleStateService` import.
- No `microservices` import.
- No standalone rule module under `objects`.

Risk:

- Very high. This is the core game simulation path.

Decision required:

```text
Allow private helper files under api/command, or force all helpers into BattleCommandAPIMessage.scala.
```

Recommendation:

```text
Allow private helper files only if they are still API planning helpers and do not become a fifth layer.
```

### Ticket 8: Dynamic rule config cleanup

ID:

```text
BE-BATTLE-FOUR-LAYER-RULE-CONFIG-08
```

Goal:

- Remove all `RuleBook` caches from database.

Boundary:

```text
services/battle/database/abilities
services/battle/database/actors
services/battle/database/combat
services/battle/database/runtime
services/battle/database/world
services/battle/objects/{abilities,actors,combat,runtime,world}
```

Expected change:

- Rule config lives in typed object ADTs.
- Rule persistence lives in Table/Initializer only.
- API/session command planners load config as needed.

Acceptance:

- `rg "RuleBook" backend/src/main/scala/services/battle` returns no active code matches.
- No `AtomicReference` in `database`.

Risk:

- Medium/high. Runtime performance and data load frequency must be considered.

### Ticket 9: Projection and replay render relocation

ID:

```text
BE-BATTLE-FOUR-LAYER-RENDER-09
```

Goal:

- Move replay/result render DTOs into API boundary types.

Boundary:

```text
services/battle/objects/apiTypes/results
services/battle/objects/apiTypes/replay
services/battle/api/results
services/battle/database/results
```

Expected change:

- No render service under `microservices/projections`.
- Circe encoders own JSON response shape.
- APIMessage owns orchestration.

Acceptance:

- No handwritten JSON string rendering.
- Replay frame payload DTOs have typed encoders.

Risk:

- Medium. Frontend replay shape must remain compatible.

### Ticket 10: Delete `microservices`

ID:

```text
BE-BATTLE-FOUR-LAYER-MICROSERVICES-DELETE-10
```

Goal:

- Remove the fifth top-level `microservices` package after all imports are gone.

Boundary:

```text
services/battle/microservices
all remaining import callers found by rg
```

Acceptance:

- `Test-Path backend/src/main/scala/services/battle/microservices` is false.
- `rg "services\\.battle\\.microservices" backend/src/main/scala` has no matches.
- `sbt compile` passes.
- contract runner passes.

Risk:

- Low only if all previous tickets are complete.

## Decision Matrix

### Decision A: `objects/apiTypes` decoder helper policy

Option A1:

```text
Allow small custom decoder helpers in apiTypes.
```

Pros:

- Keeps wire parsing near wire DTOs.
- Avoids bloating APIMessage companions.
- Still type-safe if helpers only construct object-layer ADTs.

Cons:

- Slightly broader than "only final case class plus object encoder/decoder".

Recommendation:

```text
Choose A1.
```

Option A2:

```text
Move all non-trivial decoding into APIMessage companions.
```

Pros:

- Makes apiTypes extremely passive.

Cons:

- APIMessage files become larger.
- Request parsing and planning become mixed.

### Decision B: queue/session persistence timing

Option B1:

```text
Migrate queue/session state to PostgreSQL now.
```

Pros:

- Truly eliminates in-memory mutable state.
- Aligns with `plan(connection)`.

Cons:

- Larger behavior change.
- Needs schema and transaction decisions.

Recommendation:

```text
Choose B1 if strict architecture is more important than minimal runtime risk.
```

Option B2:

```text
Keep process-memory temporarily while only package shape changes.
```

Pros:

- Lower short-term gameplay risk.

Cons:

- Does not satisfy the final strict target.
- Keeps service injection alive.

### Decision C: command/runtime helper structure

Option C1:

```text
Allow private helper files under api/command.
```

Pros:

- Avoids a giant `BattleCommandAPIMessage.scala`.
- Maintains reviewable chunks.

Cons:

- Needs strict naming and package discipline to avoid recreating `microservices`.

Recommendation:

```text
Choose C1 with a rule: helpers must be package-private and only support one APIMessage.
```

Option C2:

```text
Force all command helpers into BattleCommandAPIMessage.scala.
```

Pros:

- Literal interpretation of the requested API layer.

Cons:

- High chance of a god file.
- Harder to review and test.

## Next Safe Code Ticket After Decision

If the user accepts recommendations A1, B1, and C1:

```text
Start BE-BATTLE-FOUR-LAYER-RESULTS-01.
```

Why:

- It is the lowest-risk vertical slice.
- It proves the final APIMessage + objects/apiTypes + database/Table + route registry style.
- It does not force queue/session persistence immediately.

If the user wants immediate strictness over safety:

```text
Start BE-BATTLE-FOUR-LAYER-QUEUE-DB-04.
```

Why:

- Queue is the first visible mutable service area.
- It will start removing `APIWithTokenContextMessage` and `BattleAPIRuntimeContext`.

Risk:

- This requires schema decisions and may affect matchmaking behavior.
