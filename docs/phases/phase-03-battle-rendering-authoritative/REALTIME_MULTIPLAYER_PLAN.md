# Realtime Multiplayer Plan

## Current State

- The demo has a shared backend matchmaking queue.
- Two clients can join the same 10-second room and start with real handles in player slots.
- The actual battle simulation is still client-local: movement, projectiles, damage, bot updates and replay recording run in each browser.
- This means the current feature is suitable for a staged demo, but it is not authoritative multiplayer.

## Target

Move from shared matchmaking to a server-authoritative battle room without rewriting the Phaser renderer.

The browser should become:

- input sender
- snapshot receiver
- renderer host
- local prediction layer only if added later

The backend should own:

- room membership
- match clock
- player/bot state
- projectile state
- hit/damage/death order
- final result
- replay event stream

## Minimal Architecture

### Backend Battle Runtime

Recommended directory:

```text
backend/src/main/scala/battle/runtime/
```

Responsibilities:

- Keep one `BattleRoomRuntime` per active match.
- Tick at a fixed server rate, for example 20 Hz.
- Accept `PlayerCommand` messages from connected clients.
- Simulate bots for empty slots.
- Emit compact `BattleSnapshot` messages.
- Produce final `BattleResultRecord` and replay event stream.

### WebSocket Route

Recommended route:

```text
GET /battle/live/{roomId}?ticket=...
```

Responsibilities:

- Validate queue ticket.
- Attach socket to the room.
- Receive commands.
- Send snapshots.
- Close with final result when match ends.

### Frontend Adapter

Recommended directory:

```text
src/features/battle/adapters/realtime/
```

Responsibilities:

- Open WebSocket from `/battle`.
- Send local `PlayerCommand`.
- Buffer latest `BattleSnapshot`.
- Expose renderer-friendly state to `BattlePage`.
- Fall back to local prototype runtime when no live socket is available.

### Phaser Renderer

`GameScene` should not know WebSocket details.

It should receive either:

- a local prototype snapshot source, or
- a realtime snapshot source.

The renderer should only sync visual objects from snapshot data.

## Contracts Needed First

Before implementing the socket, define typed DTOs:

```text
BattleRoomJoined
PlayerCommandDto
BattleSnapshotDto
BattleEventDto
BattleResultDto
ReplayEventDto
```

These contracts should live outside the Phaser scene and be reusable by frontend and backend.

## Safe Implementation Order

1. Define DTOs and JSON codecs.
2. Add WebSocket route skeleton with echo/heartbeat only.
3. Connect frontend socket adapter without changing local battle.
4. Add server room clock and room membership snapshots.
5. Move bot-only simulation to backend for a test room.
6. Move player command handling to backend.
7. Move projectiles/hit/damage/death order to backend.
8. Make backend result/replay the only persisted source.

## Do Not Do First

- Do not try to synchronize raw Phaser objects.
- Do not let both clients simulate damage independently.
- Do not mix server and client kill authority.
- Do not build full rollback netcode for the demo.
- Do not change battle UI while introducing the socket.

## Demo-Feasible Milestone

The nearest useful milestone is:

- shared 10-second room,
- both clients enter the same battle,
- backend assigns slots and broadcasts a room start snapshot,
- each client can see both real handles,
- battle remains local for combat.

The next milestone after that is true shared position snapshots.

Full authoritative combat is a larger ticket and should be isolated.

