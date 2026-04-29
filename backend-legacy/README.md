# Backend Skeleton

This directory provides a minimal Scala 3 backend skeleton for the demo system.

It is intentionally skeleton-first:

- logical microservice boundaries are explicit
- `objects` contains pure data only
- `api` contains contracts only
- `routes` stays thin
- `planners` / `services` / `runtime` express behavior ownership with placeholders
- `ports` define dependency seams
- `policies` define rule-level decisions

Service families:

- `shared`
- `identity`
- `battle`
- `replay`
- `forum`
- `governance`

Root source path:

```text
backend/src/main/scala
```
