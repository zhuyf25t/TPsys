# Battle Content Contract Audit

Date: 2026-04-29 Asia/Shanghai

Scope: first audit after extracting backend and frontend battle content catalogs.

## Catalogs

- Backend: `backend/src/main/scala/battle/runtime/BattleContentCatalog.scala`
- Frontend: `frontend/src/game/battleContentCatalog.ts`

## Confirmed Aligned

- `WeaponKind`: `Pistol`, `RocketLauncher`, `Gatling`, `Shotgun`
- `ProjectileKind`: `pistol-bullet`, `rocket`, `gatling-bullet`, `shotgun-pellet`
- weapon cooldown, reload, speed, damage, lifetime, projectile radius, splash radius, pellets, spread, reserve ammo, pickup ammo
- hero spawn points
- weapon pickup IDs, weapon kinds, and positions
- base movement speed, sprint multiplier, stamina max/drain/recover
- blink range/cooldown, freeze range/radius/duration/cooldown

## Fixed In This Audit

- Dash cooldown now matches backend authority: `5000ms`.
- Backend authoritative medkits now use the same two medkit pickup IDs and positions as the frontend map pads:
  - `pickup-medkit-1` at `(960, 608)`
  - `pickup-medkit-2` at `(1600, 992)`

## Follow-Up Fix

Gatling contract drift was fixed after this audit:

- Backend authoritative runtime now stores and outputs `heat`, `overheated`, and `overheatRemainingMs`.
- Backend Gatling now uses `magazineSize = 0`, `reserveAmmo = 0`, `usesHeat = true`, `maxHeat = 100`, `heatPerShot = 8`, `coolRatePerSecond = 32`, and `overheatLockMs = 1400`.
- Gatling no longer consumes ammo in authoritative runtime. It fires while `primaryHeld`, cooldown is ready, and the weapon is not overheated.
- Frontend authoritative client, frame bridge, and snapshot applier now carry the heat fields into `WeaponState`.

Remaining note: this intentionally changes old backend behavior from a high-capacity magazine approximation to the heat model already used by the frontend HUD and local runtime.

## Verification

Passed after fixes:

```powershell
npm run backend:compile
npm run build
```

## Extension Boundary Audit

Current state: battle map, weapon, and skill content still exists as duplicated frontend TypeScript and backend Scala catalogs. The audit is a drift guard and invariant gate for that duplicated state. It is not yet a generated single-source content system.

The offline audit script is:

```powershell
npm run audit:battle-contracts
```

It compares parsed frontend and backend values for:

- default map identity, world size, spawn points, obstacles, weapon pickups, and item pickups
- weapon definitions and projectile/combat numeric fields
- skill definitions and skill effect fields

It also validates extension safety invariants with explicit failure paths such as `frontend.defaultMap.innerObstacles[3].size.x`:

- `mapId`, `themeId`, and `worldSize` must be present, with positive world dimensions.
- hero spawn points must have at least two entries and remain inside world bounds.
- obstacle IDs, weapon pickup IDs, and item pickup IDs must be non-empty and unique within their own catalogs.
- obstacles and pickups must remain inside world bounds, and obstacle sizes must be positive.
- weapon pickup `weaponKind` values must reference an existing weapon definition.
- item pickup kinds must be non-empty; the current whitelist is `Medkit`.
- weapon numeric fields are checked for positive or non-negative ranges according to field semantics.
- heat weapons must define positive heat capacity, heat cost, cooling rate, and overheat lock duration.
- skill map keys must match `definition.skillKind`.
- skill cooldown and active durations must be non-negative.
- skill `effectType` / `activationKind` combinations must provide their required fields:
  - `teleport` requires `prepared-target` and positive `range`.
  - `dash` requires `instant` and positive `distance`.
  - `slow-field` requires `prepared-target` plus positive `range`, `radius`, `durationMs`, and `speedMultiplier`.

Future direction: replace the duplicated frontend/backend catalogs with a single source content file that generates TypeScript and Scala definitions. Until then, new maps, weapons, and skills must pass this offline audit before being accepted.
