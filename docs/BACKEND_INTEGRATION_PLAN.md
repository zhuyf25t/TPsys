# Backend Integration Plan

## 1. Purpose

This document defines the backend planning/skeleton phase for the repository.

The goal is **not** to fully implement all business logic immediately.

The goal is to establish:

- service boundaries
- typed contract handoff points
- directory skeletons
- battle integration entry seams

---

## 2. Root Path

Backend root path is fixed:

```text
backend/src/main/scala
```

Within that root, the repository should create service-oriented modules for:

- `identity`
- `battle`
- `replay`
- `forum`
- `governance`
- `shared`

---

## 3. Shared Architectural Rules

All services should follow these rules:

- `objects/` holds pure data structures only
- `api/` defines public contracts only
- `routes/` remains thin
- `services/` and `planners/` hold orchestration logic
- `ports/` define external dependency seams
- `policies/` define rule-level decisions

Additional battle rule:

- `battle/runtime/` is allowed and expected

---

## 4. Recommended Directory Shape

```text
backend/src/main/scala/
  shared/
    api/
    objects/
    database/
    planners/
    services/
    ports/
    policies/

  identity/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  battle/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/
    runtime/

  replay/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  forum/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  governance/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/
```

---

## 5. Battle Integration Priorities

The first backend skeleton should clarify these battle seams:

- command intake
- authoritative battle snapshot production
- battle event emission
- battle result publication
- replay artifact publication

Front-end battle should eventually consume:

- typed snapshot DTOs
- result DTOs
- replay metadata DTOs

---

## 6. What Should Not Happen Yet

Do not prematurely:

- deeply implement persistence
- over-design deployment topology
- split into physically separate repos
- rebuild battle gameplay in Scala before contracts stabilize

This phase is skeleton-first, contracts-aware, and integration-oriented.

---

## 7. Immediate Execution Recommendation

The next code ticket in this phase should:

- create the backend directory skeleton
- add placeholder Scala files to each core layer
- introduce battle-facing API / object placeholders for commands, snapshots, events, and results

That is enough to establish a credible backend integration runway without overcommitting implementation details.
