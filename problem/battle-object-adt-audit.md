# Battle object ADT audit

## Scope

This audit treats `backend/src/main/scala/services/battle/objects` as the authoritative battle object layer.
`objects/apiTypes` is treated as API boundary code, not as the source of business truth.

Goal: every battle API should reuse these authoritative object ADTs when the same business concept already exists. API boundary files may keep request DTOs, decoders, encoders, and wire-only wrappers only when the wire shape is not the same as an existing object ADT.

## Authoritative ADT inventory

### Core ids

- `TicketId`
- `QueueRequestId`
- `RoomId`
- `BattleId`
- `PlayerId`
- `HeroId`
- `ProjectileId`
- `SlowFieldId`
- `PickupId`
- `BattleEventId`
- `BattleResultId`

### Core scalar/value objects

- `EpochMillis`
- `DurationMillis`
- `ElapsedMillis`
- `BattleTick`
- `ClientCommandSeq`
- `SeatIndex`
- `SpawnPointIndex`
- `BattleCapacity`
- `Rating`
- `RatingDelta`
- `BattleResultListLimit`
- `BattleMapId`
- `BattleAvatarKey`
- `BattleSkinKey`
- `BattleResultLabel`
- `BattleModeLabel`
- `BattleMapLabel`
- `BattleHighlightLine`
- `BattlePlayersLine`
- `BattleTimelineHint`
- `BattlePlacement`
- `Score`
- `KillCount`
- `HitPoints`
- `Stamina`
- `AmmoCount`
- `CooldownMillis`
- `FacingRadians`
- `Radius`
- `Damage`
- `BattleWeaponHeat`
- `BattleWeaponHeatRatePerSecond`
- `BattleVector2`

### Finite enums

- `MatchmakingRoomPhase`
- `BattleMode`
- `BattlePhase`
- `BattleArtifactStatus`
- `WeaponKind`
- `ProjectileKind`
- `SkillKind`
- `BattleCommandStatus`
- `BattleCommandReason`
- `SkillOutcomeStatus`
- `SkillOutcomeReason`
- `PickupKind`
- `ProjectileTerminalReason`
- `BattleEventKind`
- `BattleCommandRequestField`
- `BattleAPIRequestError`
- `BattleQueueLeaveOutcome`
- `BattlePickupAvailability`
- `BattleParticipantKind`
- `BattlePlayerLifeState`
- `BattleSurvivalOutcome`
- `BattleReplayHeroLifeState`
- `BattleFinishProjectionOutcome`
- `BattleFinishProjectionStatus`
- `BattleWeaponSwitchDirection`
- `BattleWeaponThermalState`

### Command/use-case objects

- `BattleQueueJoinCommand`
- `BattleQueueStatusQuery`
- `BattleQueueLeaveCommand`
- `RealtimeRoomHeartbeatCommand`
- `BattleRoomSnapshotQuery`
- `BattleStateReadQuery`
- `BattleResultRecordCommand`
- `BattleResultListQuery`
- `BattleCommandVector`
- `BattleCommandRequest`
- `BattleCommandSkillOutcome`
- `BattleCommandAccepted`
- `BattleCommandSkillIntents`
- `BattleWeaponSwitchIndex`

### Runtime state objects

- `BattleAggregateState`
- `BattleQueueParticipant`
- `BattleSessionRosterEntry`
- `BattleSessionBootstrapSeat`
- `BattleSessionBootstrap`
- `BattleSessionDescriptor`
- `BattleQueueSnapshot`
- `RealtimeRoomSnapshot`
- `BattleEventParticipant`
- `BattleEventState`
- `BattlePickupState`
- `BattleProjectileState`
- `BattleProjectileTerminalState`
- `BattlePlayerSkillState`
- `BattlePlayerState`
- `BattleSlowFieldState`
- `BattleWeaponState`

### Replay/result objects

- `BattleReplayHeroFrameState`
- `BattleReplayProjectileFrameState`
- `BattleReplayPickupFrameState`
- `BattleReplayFrameState`
- `BattleResultRecord`

## API duplicate findings

Removed in this pass:

- `BattleQueueLeaveResponse`
  - Before: API boundary declared `final case class BattleQueueLeaveResponse(outcome: BattleQueueLeaveOutcome)`.
  - Problem: `BattleQueueLeaveOutcome` is already the authoritative ADT for the leave result.
  - After: `BattleQueueLeaveAPIMessage` returns `BattleQueueLeaveOutcome` directly. `object BattleQueueLeaveResponse` remains only as an `Encoder[BattleQueueLeaveOutcome]` namespace, preserving the existing `{ "left": boolean }` wire shape.

- `BattleRoomHeartbeatRequest`
  - Before: API boundary declared a case class with the same fields as `RealtimeRoomHeartbeatCommand`.
  - Problem: `RealtimeRoomHeartbeatCommand` is already the authoritative command ADT for heartbeat.
  - After: API boundary decodes JSON directly into `RealtimeRoomHeartbeatCommand`. `object BattleRoomHeartbeatRequest` remains only as a decoder namespace.

- `BattleResultListResponse`
  - Before: API boundary declared `final case class BattleResultListResponse(results: Vector[BattleResultRecord])`.
  - Problem: the result-list envelope is a stable battle result object shape, not a route-specific business concept.
  - After: object layer declares authoritative `objects.result.BattleResultList`. `BattleResultListAPIMessage` returns `BattleResultList` directly. `object BattleResultListResponse` remains only as an `Encoder[BattleResultList]` namespace, preserving the existing `{ "results": [...] }` wire shape.

Already removed in earlier passes and still clean:

- `BattleCommandAcceptedResponse` case class -> `BattleCommandAccepted`
- `BattleQueueParticipantResponse` case class -> `BattleQueueParticipant`
- `BattleSessionRosterEntryResponse` case class -> `BattleSessionRosterEntry`
- `BattleSessionBootstrapSeatResponse` case class -> `BattleSessionBootstrapSeat`
- `BattleSessionBootstrapResponse` case class -> `BattleSessionBootstrap`
- `BattleSessionDescriptorResponse` case class -> `BattleSessionDescriptor`
- `BattleQueueSnapshotResponse` case class -> `BattleQueueSnapshot`
- `RealtimeRoomSnapshotResponse` case class -> `RealtimeRoomSnapshot`
- `BattleResultRecordResponse` case class -> `BattleResultRecord`
- `BattleStateVectorResponse` case class -> `BattleVector2`
- `BattleStateProjectileResponse` case class -> `BattleProjectileState`
- `BattleStateProjectileTerminalResponse` case class -> `BattleProjectileTerminalState`
- `BattleStateSlowFieldResponse` case class -> `BattleSlowFieldState`
- `BattleStatePickupResponse` case class -> `BattlePickupState`
- `BattleStateWeaponResponse` case class -> `BattleWeaponState`
- `BattleStateSkillResponse` case class -> `BattlePlayerSkillState`
- `BattleStateEventParticipantResponse` case class -> `BattleEventParticipant`
- `BattleStateEventResponse` case class -> `BattleEventState`
- `BattleStatePlayerResponse` case class -> `BattlePlayerState`
- `BattleStateRootResponse` case class -> `BattleAggregateState`

## Remaining API boundary codec namespaces

The API boundary now keeps decoder/encoder namespaces only. These objects do not declare new request/response ADTs.

- `BattleQueueJoinRequest`
  - Before: API boundary declared `final case class BattleQueueJoinRequest(...)`.
  - After: `object BattleQueueJoinRequest` only provides `Decoder[BattleQueueJoinCommand]`.
  - The API message now stores `BattleQueueJoinCommand` directly.

- `BattleQueueStatusRequest`
  - Before: API boundary declared `final case class BattleQueueStatusRequest(ticketId: TicketId)`.
  - After: object layer declares authoritative `BattleQueueStatusQuery(ticketId)`. `object BattleQueueStatusRequest` remains only as a decoder namespace.

- `BattleQueueLeaveRequest`
  - Before: API boundary declared `final case class BattleQueueLeaveRequest(ticketId: TicketId)`.
  - After: object layer declares authoritative `BattleQueueLeaveCommand(ticketId)`. `object BattleQueueLeaveRequest` remains only as a decoder namespace.

- `BattleRoomSnapshotRequest`
  - Before: API boundary declared `final case class BattleRoomSnapshotRequest(roomId: RoomId)`.
  - After: object layer declares authoritative `BattleRoomSnapshotQuery(roomId)`. `object BattleRoomSnapshotRequest` remains only as a decoder namespace.

- `BattleStateReadAPIRequest`
  - Before: API boundary declared `final case class BattleStateReadAPIRequest(battleId: BattleId)`.
  - After: object layer declares authoritative `BattleStateReadQuery(battleId)`. `object BattleStateReadAPIRequest` remains only as a decoder namespace.

- `BattleCommandAPIRequest`
  - Before: API boundary declared `final case class BattleCommandAPIRequest(...)`.
  - After: `object BattleCommandRequestApiTypes` only provides `Decoder[BattleCommandRequest]`.
  - Legacy wire fields such as `castDash/castBlink/castFreeze` are normalized during decoding into `BattleCommandSkillIntents`.

- `BattleResultListAPIRequest`
  - Before: API boundary declared `final case class BattleResultListAPIRequest(...)`.
  - After: `object BattleResultListRequest` only provides `Decoder[BattleResultListQuery]`.
  - The API message now stores `BattleResultListQuery` directly.

- `BattleResultRecordAPIRequest`
  - Before: API boundary declared `final case class BattleResultRecordAPIRequest(...)`.
  - After: `object BattleResultRecordRequest` only provides `Decoder[BattleResultRecordCommand]`.
  - The API message now stores `BattleResultRecordCommand` directly.

## Verification

- `rg -n "final case class .*Response|final case class .*APIRequest|final case class .*Request" backend/src/main/scala/services/battle/objects/apiTypes backend/src/main/scala/services/battle/api`
- `npm run backend:compile`
- `npm run backend:test-contracts`

Both backend checks passed after the duplicate removal.

## API planner simplification follow-up

After the ADT duplicate pass, the API planners were checked for response builders that only wrapped a value in `IO.pure` and returned it unchanged.

Removed no-op `buildResponse` functions from:

- `BattleQueueJoinAPIMessage`
- `BattleQueueStatusAPIMessage`
- `BattleRoomSnapshotAPIMessage`
- `BattleRoomHeartbeatAPIMessage`
- `BattleStateReadAPIMessage`
- `BattleCommandAPIMessage`
- `BattleResultRecordAPIMessage`

Kept:

- `BattleResultListAPIMessage.buildResponse`
  - Removed in the follow-up result-list ADT pass.
  - `BattleResultListAPIMessage` now returns `BattleResultList(records)` directly.

Effect:

- API planners now read closer to `decode API message -> call service/database -> return domain result`.
- Wire-specific shaping stays in `objects/apiTypes` encoders; result-list shape is now backed by the `BattleResultList` ADT.
- No business rule or JSON response shape changed.

Follow-up verification:

- `rg -n "buildResponse|render.*Response" backend/src/main/scala/services/battle/api -g "*.scala"` was rerun before the result-list ADT pass and only found the old result-list envelope builder.
- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Result list ADT follow-up

Added:

- `objects.result.BattleResultList`

Updated:

- `BattleResultListAPIMessage` now returns `BattleResultList`.
- `BattleRoutes` registers `BattleResultListAPIMessage` with response type `BattleResultList`.
- `BattleResultListResponse` is only an `Encoder[BattleResultList]` namespace.

Effect:

- The result-list API has no remaining response case class duplicate in `objects/apiTypes`.
- The existing `{ "results": [...] }` JSON response shape is preserved.
- The remaining `buildResponse|render.*Response` search now has no battle API planner hits.

Verification:

- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Command API normalization follow-up

Moved:

- `BattleCommandAPIRequest -> BattleCommandRequest` pure normalization.

From:

- `BattleCommandAPIMessage.buildCommandValue(...)`

To:

- `BattleCommandAPIRequest.toCommand(...)`

Effect:

- `BattleCommandAPIMessage` is thinner and only handles IO/service orchestration.
- The normalization still uses `BattleAPIRequestError.MissingTicket` and returns `Either`, so the failure state remains typed.
- No JSON field name, decoder, response encoder, or command behavior changed.

Verification:

- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Queue join API normalization follow-up

Moved:

- `BattleQueueJoinRequest -> BattleQueueJoinCommand` pure normalization.

From:

- `BattleQueueJoinAPIMessage.buildCommandValue(...)`

To:

- `BattleQueueJoinRequest.toCommand(...)`

Effect:

- `BattleQueueJoinAPIMessage` is thinner and keeps only IO orchestration: request conversion, authorization, and queue join.
- Invalid handle and missing session remain represented by `BattleAPIRequestError` ADT values.
- Default battle mode selection remains centralized in the typed request conversion.
- No JSON field name, decoder, response encoder, authorization behavior, or queue behavior changed.

Verification:

- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Result list API normalization follow-up

Moved:

- `BattleResultListAPIRequest -> BattleResultListQuery` pure normalization.

From:

- `BattleResultListAPIMessage.buildQuery(...)`

To:

- `BattleResultListAPIRequest.toQuery(...)`

Effect:

- `BattleResultListAPIMessage` is thinner and keeps only storage/read orchestration.
- Default list limit stays typed as `BattleResultListLimit(25)`.
- Handle lookup normalization stays pure and deterministic through `PlayerHandle.forLookup`.
- No JSON field name, decoder, response encoder, list filtering, repository, or table behavior changed.

Verification:

- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Result record API normalization follow-up

Moved:

- `BattleResultRecordAPIRequest -> BattleResultRecordCommand` pure normalization.

From:

- `BattleResultRecordAPIMessage.buildCommand(...)`
- `BattleResultRecordAPIMessage.parseSubmissionHandle(...)`

To:

- `BattleResultRecordAPIRequest.toCommand(...)`
- `BattleResultRecordAPIRequest.parseSubmissionHandle(...)`

Effect:

- `BattleResultRecordAPIMessage` is thinner and keeps only validation, transaction, storage, and save orchestration.
- Submission defaults remain typed through `EpochMillis`, `DurationMillis`, `Score`, `Rating`, `RatingDelta`, and label value objects.
- `aliveAtEnd` still normalizes to `BattleSurvivalOutcome`.
- Invalid battle id, invalid handle, and visitor-not-allowed remain `BattleAPIRequestError` ADT values.
- No JSON field name, decoder, response encoder, repository, table, or transaction behavior changed.

Verification:

- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Queue status/leave request ADT follow-up

Added:

- `BattleQueueStatusQuery`
- `BattleQueueLeaveCommand`

Removed duplicate API request case classes:

- `BattleQueueStatusRequest`
- `BattleQueueLeaveRequest`

Kept as decoder namespaces:

- `object BattleQueueStatusRequest`
- `object BattleQueueLeaveRequest`

Effect:

- Queue status and leave requests are now modeled as objects-layer query/command ADTs.
- API planners receive typed request models instead of raw `TicketId`.
- Wire JSON remains `{ "ticketId": "..." }`.
- No queue service behavior or response shape changed.

Verification:

- `rg -n "final case class BattleQueue(Status|Leave)Request|as\\[BattleQueue(Status|Leave)Request\\]|BattleQueue(Status|Leave)Request\\(" backend/src/main/scala/services/battle backend/src/test/scala/route/contract` returned no matches.
- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Room snapshot/state read request ADT follow-up

Added:

- `BattleRoomSnapshotQuery`
- `BattleStateReadQuery`

Removed duplicate API request case classes:

- `BattleRoomSnapshotRequest`
- `BattleStateReadAPIRequest`

Kept as decoder namespaces:

- `object BattleRoomSnapshotRequest`
- `object BattleStateReadAPIRequest`

Effect:

- Room snapshot and state read requests are now modeled as objects-layer query ADTs.
- API planners receive typed request models instead of raw `RoomId` or `BattleId`.
- Wire JSON remains unchanged.
- No room/state service behavior or response shape changed.

Verification:

- `rg -n "final case class Battle(RoomSnapshot|StateReadAPI)Request|as\\[Battle(RoomSnapshot|StateReadAPI)Request\\]|Battle(RoomSnapshot|StateReadAPI)Request\\(" backend/src/main/scala/services/battle backend/src/test/scala/route/contract` returned no matches.
- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.

## Final request wrapper elimination

Scan command:

- `rg -n "final case class .*Response|final case class .*APIRequest|final case class .*Request" backend/src/main/scala/services/battle/objects/apiTypes backend/src/main/scala/services/battle/api`

Removed duplicate wrapper declarations:

- `BattleQueueJoinRequest`
- `BattleCommandAPIRequest`
- `BattleResultListAPIRequest`
- `BattleResultRecordAPIRequest`

Decision:

- Delete the four API request wrapper case classes.
- Keep codec namespace objects only where legacy JSON shape needs decoding rules.
- API messages now store authoritative objects-layer ADTs directly:
  - `BattleQueueJoinAPIMessage.command: BattleQueueJoinCommand`
  - `BattleCommandAPIMessage.command: BattleCommandRequest`
  - `BattleResultListAPIMessage.query: BattleResultListQuery`
  - `BattleResultRecordAPIMessage.command: BattleResultRecordCommand`

Current clean state:

- No battle response case class remains in `objects/apiTypes`.
- No battle request/APIRequest case class remains in `objects/apiTypes`.
- Wire codec namespaces remain in `objects/apiTypes`, but all business request models now live in objects-layer command/query ADTs.

Verification:

- `rg -n "final case class .*Response|final case class .*APIRequest|final case class .*Request" backend/src/main/scala/services/battle/objects/apiTypes backend/src/main/scala/services/battle/api` now reports no matches.
- `npm run backend:compile` passed.
- `npm run backend:test-contracts` passed.
