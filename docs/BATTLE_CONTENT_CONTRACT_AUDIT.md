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

## Known Remaining Drift

- Gatling is still modeled differently across layers.
- Frontend local/runtime presentation uses heat semantics: `usesHeat`, `heat`, `overheated`, `overheatRemaining`.
- Backend authoritative runtime currently models Gatling as a high-capacity magazine with `magazineSize = 100`, `reserveAmmo = 0`, and no explicit heat state in the battle API.
- This is not a display-only mismatch. A correct fix needs a weapon-state contract decision, backend API fields, frontend adapter mapping, HUD behavior, and smoke coverage.

Decision: do not fold Gatling heat into this small audit patch. Treat it as the next weapon-contract task.

## Verification

Passed after fixes:

```powershell
npm run backend:compile
npm run build
```
