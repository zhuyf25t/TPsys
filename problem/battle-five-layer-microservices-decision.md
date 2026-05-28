# Battle five-layer microservices decision

Updated: 2026-05-28

## New User Decision

The previous decision was:

```text
services/battle has only four top-level layers:
api / objects / routes / database
```

The new decision is:

```text
services/battle may have a fifth top-level microservices layer.
Each microservice must keep its own internal four-layer structure.
```

Accepted top-level shape:

```text
services/battle/api
services/battle/objects
services/battle/routes
services/battle/database
services/battle/microservices
```

Accepted microservice shape:

```text
services/battle/microservices/{domain}/api
services/battle/microservices/{domain}/objects
services/battle/microservices/{domain}/routes
services/battle/microservices/{domain}/database
```

Examples:

```text
services/battle/microservices/queue/api
services/battle/microservices/queue/objects
services/battle/microservices/queue/routes
services/battle/microservices/queue/database

services/battle/microservices/combat/api
services/battle/microservices/combat/objects
services/battle/microservices/combat/routes
services/battle/microservices/combat/database

services/battle/microservices/world/api
services/battle/microservices/world/objects
services/battle/microservices/world/routes
services/battle/microservices/world/database
```

## Interpretation

The top-level four layers now mean battle-level orchestration and public contract:

| Top-level layer | Meaning |
| --- | --- |
| `battle/api` | Public battle APIMessage entry points exposed through `/api/{apiName}` |
| `battle/objects` | Shared battle-level ADTs used across multiple microservices |
| `battle/routes` | Public battle APIMessage registry |
| `battle/database` | Shared battle-level PostgreSQL tables, if not owned by a single microservice |
| `battle/microservices` | Domain-specific internal services, each with api/objects/routes/database |

The microservice internal four layers mean each domain owns its own local contract and storage:

| Microservice layer | Meaning |
| --- | --- |
| `{domain}/api` | Internal APIMessage/planner commands for that domain |
| `{domain}/objects` | Domain-local ADTs/value objects/codecs |
| `{domain}/routes` | Internal registry only if the microservice exposes route-like APIMessage registration |
| `{domain}/database` | Domain-local PostgreSQL Table/Initializer |

## Boundary Rule

Shared objects may remain in:

```text
services/battle/objects
```

Only if they are used by more than one battle microservice.

Domain-local objects should move to:

```text
services/battle/microservices/{domain}/objects
```

Examples:

```text
battle/objects/abilities/* -> battle/microservices/abilities/objects/*
battle/objects/combat/*    -> battle/microservices/combat/objects/*
battle/objects/world/*     -> battle/microservices/world/objects/*
battle/objects/queue/*     -> battle/microservices/queue/objects/*
```

Do not move cross-cutting value objects such as IDs, scalar wrappers, aggregate state, or globally shared enums without checking import usage.

## Dependency Direction

Preferred dependency direction:

```text
battle/routes -> battle/api
battle/api -> battle/microservices/{domain}/api
battle/api -> battle/objects
battle/microservices/{domain}/api -> battle/microservices/{domain}/database
battle/microservices/{domain}/api -> battle/microservices/{domain}/objects
battle/microservices/{domain}/database -> battle/microservices/{domain}/objects
battle/microservices/{domain}/objects -> battle/objects
```

Forbidden direction:

```text
battle/objects -> battle/microservices/*
battle/objects -> battle/database
battle/objects -> battle/api
battle/objects -> battle/routes
microservices/{domain}/objects -> microservices/{otherDomain}/api
microservices/{domain}/database -> microservices/{otherDomain}/database
```

Cross-microservice calls should use APIMessage/planner contracts or battle-level orchestration, not direct imports of another domain's database or service internals.

## Migration Impact

This changes the earlier four-layer-only plan:

- `microservices` is no longer forbidden.
- The old `microservices/*/services` package is still not ideal because it is a service dump.
- Each existing microservice should be recursively reshaped into `api/objects/routes/database`.
- Domain-local objects currently under `battle/objects` should be moved into the relevant microservice object layer.
- Shared objects stay at `battle/objects`.

## Next Safe Ticket

ID:

```text
BE-BATTLE-FIVE-LAYER-MICROSERVICES-STRUCTURE-01
```

Goal:

```text
Pick one low-risk domain and migrate its domain-local objects into microservices/{domain}/objects without behavior changes.
```

Recommended first domain:

```text
results
```

Reason:

- Results already has the cleanest `api -> database -> objects/apiTypes` shape.
- It has fewer runtime dependencies than command/combat/world.
- The current code already started moving result APIs toward explicit response DTOs.

Alternative first domain:

```text
abilities
```

Reason:

- It directly matches the user's example.

Risk:

- Abilities are used by command/runtime, so moving them first has more import blast radius.

