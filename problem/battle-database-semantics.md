# Battle database package semantics audit

## Scope

This audit classifies every current `backend/src/main/scala/services/battle/database/*` subpackage by its real responsibility.

The purpose is to support the next architecture decision:

- keep `database` as a broad battle implementation bucket;
- or restrict `database` to persistence-only code;
- or move toward `services/battle/microservices`.

No Scala code was changed for this audit.

## Summary

`services/battle/database` is not a literal database layer today.

It contains:

- true persistence: result table, table initializer, repositories, file/Postgres adapters;
- application services: queue and session services;
- mutable in-memory runtime state: queue rooms, tickets, stored battles;
- battle engine rules: runtime tick, world, actors, combat, abilities;
- projection/render adapters: finish projection, replay JSON render, mail/replay artifact writers.

Only `database/results` fits the name `database` cleanly.

## Subpackage classification

| Subpackage | Files | Real responsibility | Literal DB? | Current role |
| --- | ---: | --- | --- | --- |
| `abilities` | 6 | skill, pickup, slow-field gameplay rules | No | Engine rule layer |
| `actors` | 5 | player input/runtime, bot behavior, lifecycle | No | Engine rule layer |
| `combat` | 10 | weapon, fire, projectile, targeting, damage, terminal rules | No | Engine rule layer |
| `projections` | 13 | finish projection, replay JSON render, mail/replay artifact planning | No | Projection/render adapter |
| `queue` | 15 | matchmaking queue, rooms, heartbeat, queue snapshots, session bootstrap | No | Application/runtime service |
| `results` | 10 | result table, table initializer, repositories, file/Postgres result persistence | Yes | Persistence layer |
| `runtime` | 12 | battle engine facade, tick step, finalization, event/replay frame capture, time rules | No | Engine orchestration |
| `session` | 11 | battle state service, stored battle lifecycle, command acceptance, finish status | No | Application/runtime service |
| `world` | 7 | map catalog, collision, geometry, movement, spawn layout, map spec loading | Mostly no | Engine rule plus asset loading |

## Detailed findings

### `database/results`

Files:

- `BattleResultTable.scala`
- `BattleResultTableInitializer.scala`
- `BattleResultRepository.scala`
- `BattleResultStorage.scala`
- `PostgresBattleResultRepository.scala`
- `FileBattleResultRepository.scala`
- `InMemoryBattleResultRepository.scala`
- `BattleResultFileJsonParser.scala`
- `BattleResultFileJsonRenderer.scala`
- `BattleResultRepositoryOrderingRules.scala`

This is the only subpackage that matches literal database semantics.

Evidence:

- Uses `java.sql.Connection`, `PreparedStatement`, `ResultSet`, `Types`.
- Uses `PostgresSupport`.
- Contains SQL statements: `CREATE TABLE`, `ALTER TABLE`, `INSERT INTO`, `SELECT`.
- Defines repository abstractions and storage variants.
- File repository uses `java.nio.file.Files`.

Architecture note:

- This package can remain under `database/results`.
- `BattleResultFileJsonParser` and `BattleResultFileJsonRenderer` are persistence compatibility helpers. They are acceptable in a repository adapter boundary, but should not spread into gameplay logic.

### `database/queue`

Files include:

- `BattleQueueService.scala`
- `BattleQueueServiceContracts.scala`
- `BattleQueueRuntimeModel.scala`
- queue join/leave/heartbeat/room selection/rules files.

Real responsibility:

- queue join/status/leave;
- matchmaking room lifecycle;
- room heartbeat;
- ticket snapshots;
- queue request reuse;
- session bootstrap lookup.

Evidence:

- `InMemoryBattleQueueService` holds mutable state with `var rooms`, `var tickets`, `var queueRequests`, and `var idAllocator`.
- Uses `lock.synchronized`.
- Uses `System.currentTimeMillis`.
- Imports `database/session` types such as `BattleSessionSeed`.

Classification:

- Application/runtime service.
- Not database.

Decision impact:

- If keeping four folders strictly, this can only stay under `database` if `database` is redefined as "battle implementation".
- If `database` means persistence-only, this should move out later.

### `database/session`

Files include:

- `BattleStateService.scala`
- `BattleSessionStateFactory.scala`
- `BattleStoredBattleAdvanceRules.scala`
- `BattleStoredBattleInitializationRules.scala`
- finish projection status/preparation/completion rules.

Real responsibility:

- authoritative battle state read;
- command submission;
- stored battle initialization and advancement;
- finish projection lifecycle;
- battle room lifecycle notification.

Evidence:

- `InMemoryBattleStateService` holds mutable `var battles`.
- Uses `lock.synchronized`.
- Uses `System.currentTimeMillis`.
- Imports `database/runtime.BattleEngine`.
- Emits projection candidates through `BattleFinishProjector`.

Classification:

- Application/runtime service.
- Not database.

Decision impact:

- This is the authoritative runtime session service, not persistence.
- It should not be treated as a table/repository package.

### `database/runtime`

Files include:

- `BattleEngine.scala`
- `BattleRuntimeStepRules.scala`
- `BattleCommandApplicationRules.scala`
- `BattleRuntimeFinalizationRules.scala`
- `BattleReplayFrameRecorder.scala`
- `BattleTimeRules.scala`.

Real responsibility:

- battle engine facade;
- fixed-step advancement;
- runtime state finalization;
- command application orchestration;
- event and replay frame generation;
- retention/time helpers.

Evidence:

- `BattleEngine` imports abilities, actors, combat, and world rules.
- `BattleRuntimeStepRules` imports pickup, slow field, player runtime, held fire, projectile runtime, and weapon fire.

Classification:

- Engine orchestration.
- Not database.

Decision impact:

- This is the package most likely to become a future `engine/runtime` or `microservices/session/database/runtime` implementation block, depending on the chosen route.

### `database/world`

Files include:

- `BattleArenaCatalog.scala`
- `BattleArenaCollision.scala`
- `BattleGeometry.scala`
- `BattleInitialLayout.scala`
- `BattleMapSpecLoader.scala`
- `BattleMotionRules.scala`
- `BattleMovementCatalog.scala`.

Real responsibility:

- map dimensions/catalog;
- collision;
- geometry;
- movement;
- spawn layout;
- optional map spec file loading.

Evidence:

- `BattleMapSpecLoader` uses `java.nio.file.Files` and `Paths`, so it is an asset/file adapter.
- Other files provide geometry and movement rules used by combat, actors, and abilities.

Classification:

- Engine rule plus asset-loading adapter.
- Not database.

Decision impact:

- The pure geometry/collision pieces should not be coupled to persistence naming.
- The map loader is side-effectful and should remain clearly adapter-named.

### `database/combat`

Files include:

- `BattleWeaponCatalog.scala`
- `BattleWeaponRules.scala`
- `BattleWeaponFireRules.scala`
- projectile factory/motion/impact/runtime/targeting/terminal rules.

Real responsibility:

- weapon state construction;
- fire cooldown/ammo behavior;
- projectile creation and motion;
- projectile impact and terminal handling;
- damage and hit targeting.

Evidence:

- Imports world collision/geometry/motion.
- Imports runtime event/retention/time helpers.
- Imports actor lifecycle rules.

Classification:

- Engine rule layer.
- Not database.

Decision impact:

- Combat depends on world and runtime helpers; runtime also calls combat. This is a cohesive engine graph, not an isolated microservice.

### `database/actors`

Files include:

- `BattleBotCatalog.scala`
- `BattleBotRules.scala`
- `BattleInputRules.scala`
- `BattlePlayerLifecycleRules.scala`
- `BattlePlayerRuntimeRules.scala`.

Real responsibility:

- bot decision behavior;
- player input application;
- player movement/runtime update;
- player alive/dead lifecycle.

Evidence:

- Imports world geometry/collision/motion.
- Imports combat weapon rules.
- Imports runtime time helpers.
- `BattleBotRules` imports `BattleInputRules`, world, and combat.

Classification:

- Engine rule layer.
- Not database.

Decision impact:

- Bot/player logic should remain in the engine/rules cohesion boundary unless a later microservice split introduces a clean API contract for it.

### `database/abilities`

Files include:

- `BattleSkillCatalog.scala`
- `BattleSkillRules.scala`
- `BattleSkillCommandRules.scala`
- `BattlePickupCatalog.scala`
- `BattlePickupRules.scala`
- `BattleSlowFieldRuntimeRules.scala`.

Real responsibility:

- skill catalog and validation;
- skill command application;
- pickup catalog and pickup effects;
- slow-field runtime update.

Evidence:

- Imports runtime update/event/retention/time helpers.
- Imports world collision/geometry/motion.
- Imports combat weapon rules.

Classification:

- Engine rule layer.
- Not database.

Decision impact:

- This is tightly tied to runtime/world/combat and should not be split independently before the engine dependency graph is cleaned.

### `database/projections`

Files include:

- `BattleFinishProjectionService.scala`
- `BattleFinishProjectionPlanner.scala`
- `BattleFinishProjectionArtifactWriters.scala`
- `BattleReplayFramesJsonRenderer.scala`
- `BattleReplayFrameTimelineRules.scala`
- scoring/label/player/time/replay/mail helpers.

Real responsibility:

- convert finished battle state into result/replay/mail artifacts;
- save result records through `BattleResultRepository`;
- render replay frames to JSON;
- publish mail/replay artifacts through ports.

Evidence:

- Imports `database/results.BattleResultRepository`.
- Uses `BattleMailPublisherPort` and `BattleReplayWriterPort`.
- `BattleReplayFramesJsonRenderer` uses Circe `deriveEncoder`, `.asJson`, and `.noSpaces`.

Classification:

- Projection/render adapter.
- Not database, except where it calls result persistence through a repository.

Decision impact:

- If replay JSON is a stable public wire contract, its payload types/codecs should eventually move toward `objects/apiTypes/replay` or a clearly named projection contract package.

## Dependency direction findings

Current high-level flow:

- `routes` registers API messages and imports services.
- `api` decodes messages, calls services/repositories, and maps errors.
- `objects` defines battle ADTs and value objects.
- `objects/apiTypes` provides Circe decoders/encoders for wire contracts.
- `database` contains both persistence and battle implementation.

Current internal database graph is not one-way:

- `runtime` imports `abilities`, `actors`, `combat`, and `world`.
- `abilities` imports `runtime`, `world`, and `combat`.
- `actors` imports `runtime`, `world`, and `combat`.
- `combat` imports `runtime`, `world`, and `actors`.
- `session` imports `runtime`.
- `queue` imports `session`.
- `projections` imports `results`.

This means the current `database` subpackages are not independent microservices.

They are a single engine/runtime implementation split by concern. The split is useful for readability, but not yet a hard service boundary.

## Type-safety assessment

Positive:

- Business concepts are mostly wrapped in ADTs/value objects: IDs, ticks, time durations, ratings, HP, damage, weapon kind, battle phase, queue phase, survival outcome.
- API messages now store object-layer command/query ADTs directly.
- `objects/apiTypes` no longer declares duplicate request/response case classes for battle API wrappers.
- Finite states are represented by enums instead of raw strings in the domain layer.

Remaining concerns:

- Some render/projection payloads intentionally use raw `String`, `Long`, `Int`, and `Boolean` because they are wire payloads. This is acceptable inside a renderer boundary, but should not leak into domain objects.
- Some service result errors live in `database/session` or `database/queue`. If the package remains named `database`, this makes error ownership misleading.
- Mutable runtime state exists in queue/session services. It is acceptable inside adapter/application services, but not in domain objects.

## Circe assessment

Positive:

- API wire codecs live in `objects/apiTypes`.
- API messages import only the required `given Decoder`/`Encoder`.
- Replay rendering uses Circe derivation instead of manual JSON string escaping.

Remaining concerns:

- `BattleReplayFramesJsonRenderer` keeps private payload DTOs and codecs inside `database/projections`. That is acceptable for an internal artifact renderer, but not ideal if replay JSON becomes an API contract.
- Some file JSON parser/renderer code under `results` is persistence compatibility code. It should stay isolated.

## Cats Effect assessment

Positive:

- API planner boundary is `IO`.
- Blocking service/database calls are wrapped with `IO.blocking`.
- HTTP routes are built through http4s and `APIMessageRouter`.

Remaining concerns:

- Queue/session services themselves are synchronous mutable services. The effect boundary sits above them in API planners.
- This is workable now, but if concurrency pressure grows, a later ticket should consider effect-native state management or a repository/Ref boundary.

## Recommendation

Do not rename or move these 89 files blindly.

The safe decision path is:

1. Keep `database/results` as true persistence.
2. Decide whether the word `database` is allowed to mean "battle implementation" for this project.
3. If yes, add documentation/package comments that make this explicit and continue tightening type safety inside the current four-folder shape.
4. If no, split the non-persistence packages out of `database` in a separate migration ticket.
5. Do not attempt microservice isolation for `abilities/actors/combat/runtime/world` until their dependency cycle is intentionally broken.

## Architecture decision required

The next implementation depends on one decision:

Should `services/battle/database` mean:

- A. broad battle implementation bucket, including runtime/rules/services/projections; or
- B. literal persistence only, with queue/session/runtime/rules/projections moved elsewhere; or
- C. temporary staging area before `services/battle/microservices/*/{api,objects,routes,database}`?

Until this is decided, the safest further work is limited to:

- type-safety tightening;
- APIMessage registry cleanup;
- duplicate DTO removal;
- codec placement cleanup;
- reports and dependency audits.
