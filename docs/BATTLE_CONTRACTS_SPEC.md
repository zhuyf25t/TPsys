# Battle Contracts Spec

## 1. Purpose

This document defines the formal typed battle contract layer that should sit between:

- future battle backend service
- front-end battle adapter
- page shell
- Phaser renderer host

The current local `src/domain/types.ts` remains useful, but it is **not** the final formal contract layer.

---

## 2. Contract Goals

The formal battle contract layer must provide:

- typed command DTOs
- typed snapshot DTOs
- typed HUD view DTOs
- typed result / event DTOs
- explicit separation between renderer-consumable data and local runtime implementation detail

The renderer should eventually consume **contract DTOs**, not local prototype models.

---

## 3. Proposed Directory

```text
src/
  contracts/
    battle/
      commands/
      snapshots/
      views/
      events/
      results/
```

This should remain front-end contract code only.

Future shared backend parity can later mirror the same DTO vocabulary.

---

## 4. Core IDs and Meta Types

Recommended battle contract primitives:

- `BattleSessionId`
- `BattleReplayId`
- `HeroId`
- `ProjectileId`
- `PickupId`
- `BattleTick`
- `BattlePhase`

Suggested `BattlePhase` values:

- `queue`
- `loading`
- `active`
- `finished`
- `disconnected`

---

## 5. Command Contracts

Recommended command DTO:

```ts
interface BattleCommandDto {
  sessionId: string;
  playerId: string;
  tick: number;
  movement: Vec2Dto;
  aim: Vec2Dto;
  pointerWorld: Vec2Dto;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  preparedSkill: "Blink" | null;
  castBlink: boolean;
  castAbilityE: boolean;
  reloadPressed: boolean;
}
```

Important:

- this is a **transport command**
- it should not expose Phaser state
- it should not depend on local input helpers

---

## 6. Snapshot Contracts

Recommended snapshot root:

```ts
interface BattleSnapshotDto {
  sessionId: string;
  phase: BattlePhase;
  elapsedMs: number;
  world: BattleWorldViewDto;
  heroes: HeroViewDto[];
  projectiles: ProjectileViewDto[];
  pickups: PickupViewDto[];
  events: BattleEventDto[];
  localPlayerHeroId: string;
}
```

Recommended supporting DTOs:

```ts
interface BattleWorldViewDto {
  width: number;
  height: number;
  obstacles: ObstacleViewDto[];
}

interface HeroViewDto {
  heroId: string;
  displayName: string;
  team: string;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  position: Vec2Dto;
  facing: number;
  radius: number;
  lifeState: "alive" | "dead" | "respawning";
  score: number;
  currentWeapon: WeaponViewDto;
  preparedSkill: "Blink" | null;
}

interface WeaponViewDto {
  weaponKind: string;
  ammoInMagazine: number | null;
  reserveAmmo: number | null;
  heat: number | null;
  overheated: boolean;
  cooldownRemaining: number;
  reloadRemaining: number;
}

interface ProjectileViewDto {
  projectileId: string;
  kind: string;
  ownerHeroId: string;
  position: Vec2Dto;
  velocity: Vec2Dto;
  facing: number;
  radius: number;
}

interface PickupViewDto {
  pickupId: string;
  kind: string;
  label: string;
  position: Vec2Dto;
  available: boolean;
}
```

---

## 7. HUD View Contracts

HUD should eventually be fed by a stable view DTO rather than raw scene state.

Recommended top-level DTO:

```ts
interface BattleHudViewDto {
  timerText: string;
  fps: number;
  score: number;
  playerName: string;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  currentWeaponName: string;
  currentWeaponAmmoText: string;
  currentWeaponStateText: string;
  pickupHintText: string;
  weaponEntries: HudWeaponEntryDto[];
  skillEntries: HudSkillEntryDto[];
  leaderboard: HudLeaderboardEntryDto[];
  feed: HudFeedEntryDto[];
  minimap: HudMinimapDto;
  debugLines: string[];
}
```

This DTO is the right seam between:

- battle adapter / presenter
- DOM HUD renderer

---

## 8. Event Contracts

Recommended event DTO:

```ts
interface BattleEventDto {
  eventId: string;
  kind: "kill" | "heal" | "pickup" | "respawn" | "system";
  message: string;
  createdAtMs: number;
}
```

This is for battle timeline consumption, kill feed, result summary, replay metadata, and mails/rating hooks.

---

## 9. Result Contracts

Recommended result DTO:

```ts
interface BattleResultDto {
  sessionId: string;
  replayId: string | null;
  outcome: "finished" | "abandoned";
  playerHeroId: string;
  score: number;
  kills: number;
  deaths: number;
  placement: number | null;
  ratingDelta: number | null;
  earnedMailIds: string[];
}
```

This is the key bridge from battle into:

- result return
- mails
- rating
- replay index

---

## 10. Local Prototype Types vs Formal Contracts

Current `src/domain/types.ts` should be treated as:

- local runtime model
- local prototype convenience types

It should **not** be treated as the final public battle contract.

Migration rule:

1. introduce formal contract DTOs
2. write adapter mappers
3. progressively move renderer-facing code to consume DTOs
4. only then reduce dependency on local runtime model types

---

## 11. Immediate Implementation Recommendation

The first code-level contracts rollout should create:

- `src/contracts/battle/commands.ts`
- `src/contracts/battle/snapshots.ts`
- `src/contracts/battle/views.ts`
- `src/contracts/battle/events.ts`
- `src/contracts/battle/results.ts`

The first adapter should map:

- local `GameSnapshot` -> `BattleSnapshotDto`
- local HUD state -> `BattleHudViewDto`
- local input context -> `BattleCommandDto`

This is enough to begin typed battle-page integration without needing backend networking on day one.
