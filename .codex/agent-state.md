# Agent State

## Current Goal

Refactor `backend/src/main/scala/services/battle` toward the requested four-layer shape:

```text
services/battle/api
services/battle/objects
services/battle/routes
services/battle/database
```

The user explicitly asked to analyze the route first, then wait for a further architecture decision before continuing code migration.

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
Each microservice should recursively keep api/objects/routes/database.
Shared battle/objects stay for cross-microservice ADTs.
Domain-local objects should move under microservices/{domain}/objects.
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

## Current Evidence

Latest inspected `services/battle` state:

| Folder | Scala files | Meaning |
| --- | ---: | --- |
| `api` | 9 | APIMessage planners |
| `objects` | 69 | ADTs, value objects, API codecs, remaining pure rules |
| `routes` | 2 | battle API registry and runtime context |
| `database` | 18 | PostgreSQL tables plus transitional rule books |
| `microservices` | 53 | remaining queue/session/runtime/world/combat/actors/projections logic |

Known high-risk areas:

- `microservices/session/services/BattleStateService.scala`: mutable `var battles`.
- `microservices/queue/services/BattleQueueService.scala`: `AtomicReference` and `synchronized` runtime state.
- `microservices/projections/services/BattleReplayFramesJsonRenderer.scala`: typed Circe render logic still under `microservices`.
- `database/*/Battle*RuleBook.scala`: process-local caches in the database package.
- `database/world/BattleWorldRuleTable.scala`: table access and map JSON conversion are mixed.

## Last Completed Tickets

- `BE-BATTLE-ARCH-REPORT-01`: replaced `problem/battle-architecture-full-report.md` with current-state architecture report.
- `BE-BATTLE-DECISION-GATE-01`: added `problem/battle-four-layer-decision-gate.md`.
- `BE-BATTLE-STRICT-OBJECTS-RETENTION-01`: removed standalone `objects/runtime/BattleRetentionRules.scala` and inlined retention helpers into current callers.
- `BE-BATTLE-STRICT-OBJECTS-AGGREGATE-UPDATE-02`: removed standalone `objects/runtime/BattleAggregateUpdateRules.scala` and inlined `replacePlayer` into current callers.
- `BE-BATTLE-FOUR-LAYER-RATIONALITY-01`: added a documentation-only analysis of the requested strict four-layer battle route and decision points.
- `BE-BATTLE-FOUR-LAYER-MIGRATION-PLAN-01`: added a documentation-only migration sequence, invariants, and decision matrix for strict battle four-layer refactor.
- `BE-BATTLE-FOUR-LAYER-DECISION-REQUEST-01`: added the final A/B/C decision request needed before Scala migration continues.
- `BE-BATTLE-FIVE-LAYER-MICROSERVICES-DECISION-01`: recorded the user's revised target: battle keeps api/objects/routes/database and additionally owns recursive microservices.
- `BE-REPLAY-APIMESSAGE-01`: added missing replay APIMessage endpoints outside battle.
- `BE-BATTLE-COMBAT-PROJECTILE-FACTORY-PURE-RULE-15`: moved projectile factory pure rule into `objects/combat`.

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

Latest backend verification after `BE-BATTLE-STRICT-OBJECTS-AGGREGATE-UPDATE-02`:

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

Result:

```text
passed
```

## Next Ticket

Ticket:

```text
BE-BATTLE-STRICT-OBJECTS-NEXT-RULE-03
```

Goal:

- Continue removing standalone rule modules from `objects`.
- Pick the lowest-dependency remaining rule object.
- Do not create a fifth layer.
- Do not move rules into `database` unless they are table/initializer logic.

Allowed boundary:

```text
backend/src/main/scala/services/battle/objects
backend/src/main/scala/services/battle/api
backend/src/main/scala/services/battle/database
backend/src/main/scala/services/battle/routes
focused callers required by compile
```

Verification:

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
git diff --check
```
