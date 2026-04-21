# Battle Adapter And Page Shell Plan

## 1. Purpose

This document defines how the battle renderer should be mounted into a real front-end shell without collapsing boundaries again.

The main design rule is:

- Phaser remains the renderer host
- page shell owns route/session/result flow
- adapter owns contract mapping

---

## 2. High-Level Layering

```text
Route / Page Shell
  -> BattleSessionFacade
    -> BattleAdapter
      -> Contract DTOs
        -> Renderer Bridge
          -> Phaser GameScene
          -> DOM HUD
```

---

## 3. Responsibilities By Layer

### 3.1 Route / Page Shell

Owns:

- route matching
- loading state
- error state
- exit / return actions
- result handoff to replay/mails/rating

Does not own:

- Phaser runtime logic
- local battle resolver logic
- direct HUD DOM mutation

### 3.2 BattleSessionFacade

Owns:

- lifecycle of current battle session
- local mock session for demo mode
- future network session for backend mode
- result completion callbacks

Does not own:

- scene rendering
- DOM HUD rendering details

### 3.3 BattleAdapter

Owns:

- command mapping
- snapshot mapping
- DTO normalization
- future websocket / polling integration seam

Does not own:

- route shell
- scene visuals

### 3.4 Renderer Bridge

Owns:

- mounting Phaser into the page
- feeding DTO-derived state into scene host
- wiring battle page shell to the renderer

Does not own:

- routing
- product navigation
- result storage

---

## 4. Battle Page Shell States

Recommended page shell states:

- `idle`
- `matching`
- `loading`
- `active`
- `finished`
- `error`

These can initially be local mock states.

---

## 5. Route Plan

Minimum product routes:

- `/`
- `/loadout`
- `/battle`
- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

Optional:

- `/login`
- `/register`

---

## 6. Battle Route Composition

Recommended structure:

```text
BattlePage
  -> BattlePageShell
    -> BattleShellHeader
    -> BattleMountSurface
      -> Phaser game host
      -> HUD root
    -> BattleShellFooter
    -> ResultActionStrip
```

The route page should provide:

- shell chrome
- battle status text
- links to replay / mails / rating
- a bounded mount region for Phaser

---

## 7. Renderer Host Migration Plan

Current state:

- `src/main.ts` mounts Phaser directly into `#app`

Target state:

- app shell mounts into `#app`
- battle route renders a dedicated `BattleRuntimeMount`
- `BattleRuntimeMount` creates/destroys the Phaser game instance as a child concern

That means `src/main.ts` should eventually become:

- app bootstrap only

while Phaser boot moves into:

- `src/features/battle/renderer/createBattleRuntime.ts`

---

## 8. Adapter Interfaces

Recommended initial interfaces:

```ts
interface BattleSessionFacade {
  start(): Promise<void>;
  stop(): Promise<void>;
  sendCommand(command: BattleCommandDto): void;
  subscribe(listener: (snapshot: BattleSnapshotDto) => void): () => void;
  subscribeResult(listener: (result: BattleResultDto) => void): () => void;
}

interface BattleAdapter {
  mapSnapshot(local: unknown): BattleSnapshotDto;
  mapHud(local: unknown): BattleHudViewDto;
  mapCommand(local: unknown): BattleCommandDto;
}
```

Initial implementation can be mock/local.

---

## 9. Immediate Productization Strategy

To reach a demo-capable front-end shell quickly:

1. introduce a small route-based app shell
2. wrap current battle runtime inside `/battle`
3. add typed mock data for replay/mails/rating/profile/discussion pages
4. keep page boundaries explicit so they can later swap from mock adapters to backend services

This achieves product demo readiness without prematurely implementing backend networking.

---

## 10. Phase-3 To Phase-4 Handoff

Phase 3 is complete enough to move into Phase 4 when these exist:

- formal battle DTO docs
- route/page shell plan
- adapter boundary plan

After that, the first code ticket should be:

- create app shell bootstrap + route skeleton + `/battle` runtime mount seam
