# Battle four-layer decision gate

Updated: 2026-05-28

## Final User Decision

The user decision is:

```text
Strict four layers.
objects must be ADT declarations only.
```

Accepted battle top-level shape:

```text
services/battle/api
services/battle/objects
services/battle/routes
services/battle/database
```

Rejected:

```text
services/battle/microservices
services/battle/runtime
services/battle/application
services/battle/engine
services/battle/services
```

Strict `objects` rule:

- Allowed: `final case class`, enum, sealed ADT, value object, companion object construction helpers, `wireValue/fromWire`, Circe `Encoder`/`Decoder` for API contract types.
- Forbidden: standalone rule modules, runtime services, mutable state, IO, JDBC, http4s, APIMessage, database table logic, cross-domain orchestration.

This overrides the previous recommendation that pure rules could stay in `objects`.

## Current Mismatch

Current `services/battle` still has:

```text
api/
objects/
routes/
database/
microservices/
```

Current file counts from the latest scan:

| Folder | Scala files | Status |
| --- | ---: | --- |
| `api` | 9 | Mostly aligned: APIMessage planners |
| `objects` | 69 | Not aligned: still contains pure rule modules |
| `routes` | 2 | Mostly aligned: registry/context |
| `database` | 18 | Partly aligned: tables plus rule-book caches |
| `microservices` | 53 | Not aligned: must be eliminated |

## Non-ADT Files Currently In `objects`

These files are not acceptable in the final strict ADT `objects` layer:

```text
objects/abilities/BattlePickupRules.scala
objects/abilities/BattleSkillCommandRules.scala
objects/abilities/BattleSkillRules.scala
objects/abilities/BattleSlowFieldRuntimeRules.scala
objects/actors/BattleInputRules.scala
objects/actors/BattlePlayerLifecycleRules.scala
objects/combat/BattleProjectileFactoryRules.scala
objects/combat/BattleProjectileMotionRules.scala
objects/combat/BattleProjectileTargetingRules.scala
objects/combat/BattleProjectileTerminalRules.scala
objects/runtime/BattleEventFactory.scala
objects/runtime/BattleReplayFrameRecorder.scala
objects/runtime/BattleTimeRules.scala
objects/world/BattleGeometry.scala
```

Borderline files that contain ADTs plus pure projection helpers:

```text
objects/BattleAPIRequestError.scala
objects/BattleEnums.scala
objects/pickup/BattlePickupAvailability.scala
objects/player/BattlePlayerLifeState.scala
objects/player/BattleParticipantKind.scala
objects/replay/BattleReplayFrameState.scala
objects/result/BattleFinishProjectionContracts.scala
objects/weapon/BattleWeaponThermalState.scala
```

Decision:

- Keep companion helper functions only when they are construction, projection, `wireValue/fromWire`, or codec-related.
- Move gameplay transition functions out.

## Where Logic Can Go Under Strict Four Layers

Because no fifth layer is allowed, legal homes are limited:

| Logic kind | Legal home |
| --- | --- |
| HTTP/API planning | `api/**/XXXAPIMessage.scala` |
| API private validation/orchestration helpers | private functions inside the relevant `XXXAPIMessage.scala` |
| ADT/value objects | `objects/**` |
| Request/response codecs | `objects/apiTypes/**` |
| PostgreSQL schema/access | `database/**/XXXTable.scala` and `XXXTableInitializer.scala` |
| Route registry | `routes/BattleRoutes.scala` |

Consequence:

- Business rules cannot remain as standalone `Rules.scala` objects.
- If a rule is tied to one API, fold it into that APIMessage as private functions.
- If a rule is tied to storage, fold it into the related `Table` or `TableInitializer`.
- If a rule is shared by multiple APIs, the strict design forces duplication or a higher-level APIMessage owner. Prefer duplication only for tiny pure helpers; otherwise the API boundary must be redesigned.

## Implementation Risk

This strict route is reviewable only if implemented in small vertical slices.

Do not move all `objects/*Rules.scala` at once.

Do not mechanically move them to `database`, because `database` is restricted to Table/Initializer.

Do not create a new `runtime` or `application` folder.

## Next Ticket

Ticket:

```text
BE-BATTLE-STRICT-OBJECTS-AUDIT-03
```

Goal:

- Remove the first standalone rule module from `objects`.
- Choose a low-dependency file.
- Keep behavior unchanged.
- Compile after the move.

Candidate order:

1. `objects/actors/BattlePlayerLifecycleRules.scala`
2. `objects/combat/BattleProjectileTerminalRules.scala`
3. `objects/runtime/BattleTimeRules.scala`
4. `objects/world/BattleGeometry.scala`
5. `objects/actors/BattleInputRules.scala`

Selection principle:

- Start with smallest dependency surface.
- Inline or relocate only inside the current strict four layers.
- If a candidate is used by multiple domains, do not duplicate blindly; pick a narrower candidate.

Verification:

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
git diff --check
```
