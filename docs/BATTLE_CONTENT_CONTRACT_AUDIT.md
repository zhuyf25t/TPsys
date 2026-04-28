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
