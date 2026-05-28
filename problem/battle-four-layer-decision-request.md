# Battle four-layer refactor decision request

Updated: 2026-05-28

Superseded by:

```text
problem/battle-five-layer-microservices-decision.md
```

Status:

```text
The user chose A1 B1 C1, then changed the architecture target to strict four layers plus a fifth microservices layer.
This file is retained as decision history only.
```

## Current Status

The battle refactor analysis phase is complete.

Authoritative documents:

```text
problem/battle-four-layer-refactor-rationality.md
problem/battle-four-layer-migration-plan.md
```

Current evidence from the latest scan:

```text
services/battle/api          9 Scala files
services/battle/objects     69 Scala files
services/battle/routes       2 Scala files
services/battle/database    18 Scala files
services/battle/microservices 53 Scala files
```

The requested four-layer route is reasonable, but it needs three policy decisions before code migration continues.

## Decision A: apiTypes decoder boundary

Question:

```text
Can objects/apiTypes contain small custom Decoder helper functions?
```

Recommended answer:

```text
A1 yes
```

Meaning:

- `objects/apiTypes` may contain `Decoder` / `Encoder` code.
- It may contain small private helpers only for wire JSON parsing.
- It must construct existing object-layer ADTs.
- It must not call services, database, APIMessage, routes, or business runtime rules.

Alternative:

```text
A2 no
```

Meaning:

- Move all non-trivial decoding helpers into APIMessage companions.
- This keeps apiTypes passive but makes APIMessage files larger.

## Decision B: queue/session persistence timing

Question:

```text
Should queue/session mutable runtime state be migrated to PostgreSQL now?
```

Recommended answer:

```text
B1 yes, but in a dedicated high-risk ticket after the result slice
```

Meaning:

- Final target removes `AtomicReference`, `synchronized`, and `private var battles`.
- Queue/session become table-backed or explicit persisted state.
- This is not mixed into the first low-risk result cleanup ticket.

Alternative:

```text
B2 no, temporarily keep process memory while package shape is cleaned
```

Meaning:

- Lower immediate gameplay risk.
- But this does not satisfy the final strict target.

## Decision C: command/runtime helper shape

Question:

```text
Can command/runtime use package-private helper files under api/command?
```

Recommended answer:

```text
C1 yes
```

Meaning:

- `BattleCommandAPIMessage.scala` remains the public APIMessage file.
- Package-private helpers under `api/command` are allowed only if they support this APIMessage.
- Helpers must not become a new `microservices`, `engine`, `runtime`, or `services` layer.
- This avoids turning `BattleCommandAPIMessage.scala` into a god file.

Alternative:

```text
C2 no
```

Meaning:

- All command private helper functions must live inside `BattleCommandAPIMessage.scala`.
- This is literal but high-risk for readability.

## Recommended Decision Set

Use:

```text
A1
B1
C1
```

Then start:

```text
BE-BATTLE-FOUR-LAYER-RESULTS-01
```

Reason:

- Results are already closest to the final architecture.
- It proves the final pattern with low risk:

```text
api/results/*APIMessage.scala
objects/result/*
objects/apiTypes/results/*
database/results/*Table.scala
database/results/*TableInitializer.scala
routes/BattleRoutes.scala
```

Expected result of the first code ticket:

- Result APIs use `APIWithTokenMessage` and `plan(connection)`.
- Result APIs do not import `microservices`.
- `database/results` contains only table/initializer style code.
- `BattleRoutes` keeps or improves typed registration for result messages.
- Compile and contract tests pass.

## User Reply Format

Reply with one line:

```text
A1 B1 C1
```

or choose alternatives, for example:

```text
A2 B1 C2
```
