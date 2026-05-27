# Battle refactor readiness checklist

## Purpose

This checklist maps the active battle refactor goal to current evidence.

It answers:

- what has already been analyzed;
- what is already structurally true in the current worktree;
- what remains undecided;
- what should not be implemented before the user chooses an architecture route.

No Scala code was changed for this checklist.

## Evidence sources

Primary reports:

- `problem/battle-architecture-full-report.md`
- `problem/battle-database-semantics.md`
- `problem/battle-refactor-decision-matrix.md`
- `problem/battle-refactor-route-analysis.md`
- `problem/battle-object-adt-audit.md`

Primary code evidence:

- `backend/src/main/scala/services/battle/api`
- `backend/src/main/scala/services/battle/objects`
- `backend/src/main/scala/services/battle/routes`
- `backend/src/main/scala/services/battle/database`
- `backend/src/main/scala/route/battle/BattleHttp4sRoutes.scala`
- `backend/src/main/scala/system/api/APIMessage.scala`
- `backend/src/main/scala/system/api/APIMessageRouter.scala`

## Goal requirement mapping

| Requirement | Current evidence | Status |
| --- | --- | --- |
| Produce a detailed report of battle logic | `battle-architecture-full-report.md` covers routes, API, objects, apiTypes, database/results, queue, session, runtime, world, combat, actors, abilities, projections | Done for analysis phase |
| Explain type-safe ADT structure | `battle-architecture-full-report.md` and `battle-object-adt-audit.md` list IDs, scalar wrappers, enums, command/query ADTs, state ADTs, result/replay ADTs | Done for analysis phase |
| Explain Circe usage | `battle-architecture-full-report.md` covers `objects/apiTypes`, APIMessage decoders, replay renderer encoders | Done for analysis phase |
| Explain Cats Effect/http4s usage | `battle-architecture-full-report.md` covers `HttpRoutes[IO]`, `APIMessageRouter`, `IO.blocking`, `Connection` planning | Done for analysis phase |
| Explain render/projection technology | `battle-architecture-full-report.md` covers `BattleReplayFramesJsonRenderer`, projection service, result/replay/mail artifact writing | Done for analysis phase |
| Battle must contain api/object/route/database | Current code contains `api`, `objects`, `routes`, `database`; singular `object` should remain `objects` because `object` is a Scala keyword and repo convention | Structurally true with naming clarification |
| API split by business logic | Current `api` has `command`, `queue`, `room`, `state`, `results` subpackages | Mostly true |
| API files are `XXXAPIMessage.scala` | Current search finds 9 `final case class *APIMessage` files under `api` | True |
| APIMessage has `plan(...): IO[Response]` | Current APIMessage files implement `plan` with `IO`; some use context because queue/state services are injected | True with context variant |
| APIMessage should use object-layer request/response types | Recent ADT pass removed duplicate request wrapper case classes; API messages now hold command/query ADTs such as `BattleCommandRequest`, `BattleQueueJoinCommand`, `BattleResultListQuery` | Improved and currently true for battle API wrappers |
| `objects/apiTypes` should hold codecs | Current `objects/apiTypes` contains `given Decoder` and `given Encoder` namespaces | True |
| Object layer should be authoritative ADT source | `objects` owns IDs, scalars, enums, commands, state, result/replay objects | True |
| Unified enum strategy | `BattleEnums.scala` and domain enum files centralize finite states and wire mappings | Mostly true |
| `routes/BattleRoutes.scala` registry | Current `services/battle/routes/BattleRoutes.scala` registers battle API messages using `RegisteredAPIMessage` | True |
| `database` should contain Table/TableInitializer | `database/results` contains `BattleResultTable` and `BattleResultTableInitializer` | True for results only |
| Avoid cross-business mutual calls | Current engine packages under `database` have dense cross-imports between runtime, world, combat, actors, abilities | Not achieved; requires architecture decision |
| Do not use old direct route-heavy logic | Current battle route is thin and delegates to `APIMessageRouter` | Mostly achieved |
| Ask user before next major decision | `battle-refactor-decision-matrix.md` presents Option A/B/C and asks for decision | Pending user decision |

## Current blockers to safe code migration

The next code migration depends on one architecture decision:

- Option A: keep `database` as a broad implementation bucket.
- Option B: make `database` persistence-only and approve a new destination for runtime/session/engine/projection.
- Option C: start staged `services/battle/microservices`, beginning with `results`.

Without that decision, moving files would likely violate one of the stated goals:

- If we keep all implementation under `database`, the word `database` remains semantically wrong.
- If we move runtime/rules out of `database`, we violate the strict four-folder reading unless a new folder is approved.
- If we split microservices immediately, engine dependency cycles make the split unsafe.

## Safe work that can continue without decision

Only low-risk stabilization should continue before the user chooses A/B/C:

- type-safety cleanup inside existing folders;
- APIMessage registry cleanup;
- duplicate DTO/codecs audit;
- comment/mojibake cleanup;
- dependency direction reports;
- package documentation that does not lock in a disputed architecture.

Unsafe before decision:

- moving `database/runtime`, `database/world`, `database/combat`, `database/actors`, or `database/abilities`;
- creating `microservices` packages;
- renaming `database` semantics by code move;
- changing API paths or frontend contract;
- splitting engine packages as if they were independent services.

## Recommended decision

Default recommendation remains Option A for the next small implementation ticket:

- keep the four-folder top-level structure;
- explicitly document that `database` is currently a transitional implementation bucket;
- do not add new non-persistence responsibilities to `database`;
- continue tightening ADTs/codecs/APIMessage plans.

If the user wants strict semantic architecture instead, choose Option B.

If the user wants long-term service isolation, choose Option C, but start with `results`, not engine.

## Next ticket after decision

If Option A:

- `BE-BATTLE-DATABASE-BOUNDARY-DOC-01`
- Add boundary comments/package docs clarifying current `database` semantics.
- Run `npm run backend:compile`.

If Option B:

- `BE-BATTLE-PERSISTENCE-BOUNDARY-01`
- Define the new destination package for non-persistence runtime/rules.
- Move only one small package first.
- Run `npm run backend:compile` and `npm run backend:test-contracts`.

If Option C:

- `BE-BATTLE-MICROSERVICE-RESULTS-01`
- Split only results into a microservice-shaped slice first.
- Keep API contract names stable.
- Run `npm run backend:compile` and `npm run backend:test-contracts`.
