# Realtime Room Heartbeat Seam

This seam exposes room presence over the existing in-memory battle queue state only.
It does not connect to `GameScene`, combat simulation, projectiles, damage, respawn, or bot AI.

## Backend API

- `GET /battle/rooms/:roomId/snapshot`
- `GET /battle/rooms/snapshot?roomId=:roomId`
- `POST /battle/rooms/:roomId/heartbeat`
- `POST /battle/rooms/heartbeat`

Heartbeat accepts a JSON body with optional `ticketId` or `handle`.
For the query-style heartbeat endpoint, `roomId`, `ticketId`, and `handle` may also be provided as query params.

Snapshot response:

```json
{
  "roomId": "room-id",
  "serverTime": 1710000000000,
  "participants": [
    {
      "handle": "player",
      "joinedAt": 1710000000000,
      "lastSeen": 1710000000000
    }
  ],
  "capacity": 6,
  "phase": "waiting"
}
```

Missing rooms return `404` with `{"error":"room_not_found"}`.

## Frontend Seam

`src/features/battle/adapters/realtimeRoomClient.ts` provides:

- `loadRealtimeRoomSnapshot(roomId)`
- `sendRealtimeRoomHeartbeat({ roomId, ticketId, handle })`

The client uses the existing `/api` base URL convention and is intentionally not called by any UI or battle runtime yet.
