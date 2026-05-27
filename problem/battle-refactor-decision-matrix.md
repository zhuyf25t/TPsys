# Battle refactor decision matrix

## Purpose

This document converts the current battle architecture audits into an implementation decision.

Source reports:

- `problem/battle-refactor-route-analysis.md`
- `problem/battle-database-semantics.md`
- `problem/battle-object-adt-audit.md`

No Scala code is changed by this document.

## Non-negotiable invariants

These should remain true for every route option:

- `services/battle/objects` is the authoritative ADT/value-object layer.
- `services/battle/objects/apiTypes` owns Circe wire codecs.
- `services/battle/api/**/XXXAPIMessage.scala` owns API message planning.
- `services/battle/routes/BattleRoutes.scala` owns the API message registry.
- `route/battle/BattleHttp4sRoutes.scala` stays thin and delegates to `APIMessageRouter`.
- API paths are derived from `APIMessage.apiNameFromClassName`; no manual path rewrite.
- API messages should store object-layer command/query ADTs, not duplicate request wrapper case classes.
- Domain objects should not depend on routes, API messages, database adapters, http4s, or JDBC.
- Blocking persistence or mutable runtime calls should be lifted at the API/application boundary with `IO.blocking`.
- Result persistence stays typed behind `BattleResultRepository` or `BattleResultTable`.

## Current evidence

Current top-level battle package already has:

- `api`
- `database`
- `objects`
- `routes`

Current API shape:

- 9 `final case class *APIMessage` files.
- `BattleRoutes` registers messages with `apiWithToken` / `apiWithTokenAndContext`.
- `APIMessageRouter` handles `POST /api/{apiName}`.

Current persistence evidence:

- `database/results/BattleResultTable.scala`
- `database/results/BattleResultTableInitializer.scala`
- `database/results/BattleResultRepository.scala`
- Postgres/file/in-memory result repositories.

Current mismatch:

- `database` contains 89 files.
- Only `database/results` is literal persistence.
- `database/queue`, `database/session`, `database/runtime`, `database/world`, `database/combat`, `database/actors`, `database/abilities`, and `database/projections` are implementation/runtime/rule/render packages, not database packages.

## Option A: four-folder compromise

Meaning:

- Keep `services/battle/api`, `services/battle/objects`, `services/battle/routes`, and `services/battle/database`.
- Treat `database` as "battle implementation and adapters", not literal database.
- Keep subdomains inside those four folders.

Directory meaning:

```text
services/battle/
  api/        APIMessage planners by endpoint/domain
  objects/    authoritative ADTs plus apiTypes codecs
  routes/     BattleRoutes registry and runtime context
  database/   battle implementation: persistence, runtime, rules, projections
```

Pros:

- Smallest migration from current state.
- Lowest immediate compile risk.
- Matches the strict top-level folder requirement.
- Preserves the current working APIMessage route flow.

Cons:

- `database` name remains semantically misleading.
- New reviewers may think engine/runtime rules are persistence code.
- It weakens side-effect boundary clarity.
- It makes later microservice splitting harder because everything lives under an overloaded implementation bucket.

Safe next ticket if chosen:

- Add package-level boundary docs for `database`, explaining it is a transitional implementation bucket.
- Keep tightening type safety and codec boundaries without moving runtime files.

Recommended only if:

- The priority is minimal disruption and keeping exactly four top-level battle folders.
- The team accepts that `database` is a temporary overloaded name.

## Option B: literal database semantics

Meaning:

- `services/battle/database` contains only persistence/database adapters.
- Queue/session/runtime/world/combat/actors/abilities/projections move out of `database`.

Possible directory meaning:

```text
services/battle/
  api/
  objects/
  routes/
  database/      persistence only: Table, TableInitializer, Repository, adapters
  implementation/ or runtime/ or engine/   non-persistence implementation
```

Pros:

- Best semantic clarity.
- Keeps persistence side effects visible.
- Makes future reviews simpler.
- Aligns with the phrase "database should have Table and TableInitializer".

Cons:

- Conflicts with the strict "only api/object/route/database under battle" reading.
- Requires another approved top-level implementation folder.
- Requires many package moves and import fixes.
- Larger rollback/review cost.

Safe next ticket if chosen:

- First move only `database/results` to confirm persistence boundary naming.
- Then move one non-persistence group at a time, starting with `world` or `runtime`, not all 79 non-persistence files in one ticket.

Recommended only if:

- The team wants clean long-term architecture more than strict four-folder naming.
- The user approves a non-database implementation folder.

## Option C: microservices under services/battle/microservices

Meaning:

- Introduce `services/battle/microservices`.
- Each battle business service eventually owns local `api`, `objects`, `routes`, and `database`.
- Cross-service communication uses typed APIMessage/API planner contracts, not internal imports.

Possible end shape:

```text
services/battle/
  microservices/
    queue/
      api/
      objects/
      routes/
      database/
    session/
      api/
      objects/
      routes/
      database/
    results/
      api/
      objects/
      routes/
      database/
    engine/
      api/
      objects/
      routes/
      database/
  routes/
    BattleRoutes.scala
```

Pros:

- Strongest service-boundary model.
- Makes "business capabilities communicate through APIMessage" explicit.
- Could support later extraction into separate services.

Cons:

- Current engine packages are not microservice-isolated.
- `runtime`, `abilities`, `actors`, `combat`, and `world` have dense cross-imports.
- Moving too early would create fake microservices with internal dependency leaks.
- Highest migration cost.
- Requires front/back contract review per service.

Safe next ticket if chosen:

- Do not start with engine.
- Start with `results`, because it already has the cleanest persistence and API boundary.
- Create a `microservices/results` vertical slice only after proving routes and API registry can register from that package without duplicate contracts.

Recommended only if:

- The user explicitly wants `services/battle/microservices`.
- The team accepts a multi-ticket migration with compile checks after every slice.

## Comparison table

| Criterion | Option A | Option B | Option C |
| --- | --- | --- | --- |
| Immediate compile risk | Low | Medium | High |
| Review size | Small | Medium/large | Large |
| Matches current state | High | Medium | Low |
| Matches strict four folders | High | Low unless approved exception | Medium if microservices are approved |
| Database semantic clarity | Low | High | Medium/high per microservice |
| Microservice isolation | Low | Medium | High target, low current readiness |
| Best first migration target | Documentation and type-safety cleanup | `database/results` boundary | `results` microservice slice |

## Recommended decision

Recommended near-term decision:

- Choose Option A for the next one or two tickets.
- Keep the current top-level four-folder shape.
- Stop adding new non-persistence responsibilities to `database`.
- Continue making the code more type-safe and easier to move later.

Recommended long-term direction:

- Move toward Option B or C only after the current APIMessage contract and battle tests are stable.
- If microservices are desired, split `results` first, not `engine`.

Reason:

- The current structure already satisfies the outer folder shape and APIMessage route direction.
- The largest unresolved problem is semantic naming and internal dependency direction, not route mechanics.
- A broad move now would risk breaking a working route/API contract surface before the service boundaries are ready.

## Next implementation ticket after decision

If Option A:

- `BE-BATTLE-DATABASE-BOUNDARY-DOC-01`
- Add concise package/object documentation that `database` currently means implementation adapters, with `results` as the only literal persistence subpackage.
- No behavior change.

If Option B:

- `BE-BATTLE-PERSISTENCE-BOUNDARY-01`
- Keep `database/results` as persistence and plan a new approved non-persistence folder for runtime/session/queue/rules.
- First ticket should only move one small non-persistence package or add compatibility package forwarding, not all packages.

If Option C:

- `BE-BATTLE-MICROSERVICE-RESULTS-01`
- Create a results-only microservice slice because it has the cleanest API/database boundary.
- Register the same result API messages through existing `BattleRoutes`.
- Do not split engine first.

## Decision question

The required human decision is:

Should the next code migration follow Option A, Option B, or Option C?

Default recommendation:

- Choose Option A now, then revisit Option B/C after the result API and route registry are fully stable.
