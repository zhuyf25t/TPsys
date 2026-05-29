# Focused non-IO monad risk audit

Scope: `backend/src/main/scala/**/services/*.scala`. This report is narrower than the broad non-IO list: it highlights places where service code uses `for` over non-`IO` monads, returns `Option`/`Either`/bare values at service boundaries, or performs state/time/port/repository work outside `IO`.

Summary: 84 service files scanned; 4 non-IO functions contain for-comprehension/generator syntax; 112 public-or-relevant functions expose Option/Either/map lookup style failure; 40 public service-boundary functions do effectful work without returning IO.

## Non-IO for-comprehensions

| File:line | def | Return type | for/generator lines | Signals |
|---|---|---|---|---|
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala:11 | snapshotForTicket | `Option[BattleQueueSnapshot]` | 17, 18, 19, 20 | .get(, .find(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala:24 | snapshotForWaitingTicket | `Option[BattleQueueSnapshot]` | 30, 31, 32, 34 | .get(, .find(, Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:74 | createTopic | `Either[ForumCreateTopicError, ForumTopicView]` | 75, 76, 77, 78, 79 | Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:56 | register | `Either[IdentityRegistrationError, IdentityAccount]` | 57, 58, 66 | Either return, repository |

## Silent failure / non-typed error surfaces

| File:line | def | Return type | Signals |
|---|---|---|---|
| backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala:16 | collectPickups | `BattleAggregateState` | .filter(, match None, case None |
| backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala:28 | applyBlinkCommand | `CommandApplication` | match None, case None |
| backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala:97 | applyFreezeCommand | `CommandApplication` | match None, case None |
| backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillRules.scala:8 | availabilityFailure | `Option[SkillOutcomeReason]` | .find(, match None, case None, Option return |
| backend/src/main/scala/services/battle/microservices/abilities/services/BattleSlowFieldRuntimeRules.scala:8 | advanceSlowFields | `BattleAggregateState` | .filter( |
| backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala:24 | applyBotControl | `BattlePlayerState` | match None, case None |
| backend/src/main/scala/services/battle/microservices/actors/services/BattleInputRules.scala:34 | lastClientCommandSeq | `ClientCommandSeq` | .find( |
| backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerLifecycleRules.scala:26 | winnerFor | `Option[BattlePlayerState]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleHeldFireRuntimeRules.scala:8 | resolveHeldPrimaryFire | `BattleAggregateState` | .find( |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala:17 | applyProjectileImpact | `BattleAggregateState` | match None, case None, Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala:20 | resolveProjectileMotion | `ProjectileMotionResult` | match None, case None, Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala:23 | advanceProjectiles | `BattleAggregateState` | match None, case None |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala:15 | findProjectilePlayerHit | `Option[ProjectilePlayerHit]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala:24 | terminalForProjectile | `BattleProjectileTerminalState` | .find(, Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala:82 | resolveRequestedReloads | `BattleAggregateState` | .find( |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala:17 | currentWeapon | `Option[BattleWeaponState]` | Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala:26 | heatDefinition | `Option[BattleWeaponHeatDefinition]` | Option return |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala:76 | finishReload | `BattleWeaponState` | match None, case None |
| backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala:131 | applyWeaponSwitchRequest | `BattlePlayerState` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionLabelRules.scala:62 | timelineHint | `BattleTimelineHint` | match None, case None |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionLabelRules.scala:75 | playersLine | `BattlePlayersLine` | .filter( |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionLabelRules.scala:108 | replayResultLabel | `String` | .find( |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala:37 | find | `Option[BattleSettlement]` | .find(, Option return |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala:42 | fromVectorOrFallback | `BattleSettlements` | match None, case None, Option return |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala:101 | humanPlayersByPlacement | `Vector[BattlePlayerState]` | .filter( |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionReplayRules.scala:9 | replayOwnerSettlement | `BattleSettlement` | .find( |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleSettlementScoringRules.scala:10 | placementScore | `Int` | match None, case None, Option return |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleSettlementScoringRules.scala:21 | ratingDelta | `RatingDelta` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala:15 | authorize | `Either[BattleQueueJoinAuthorizationError, Unit]` | Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala:22 | authorize | `Either[BattleQueueJoinAuthorizationError, Unit]` | Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueHeartbeatRules.scala:9 | roomIdForHeartbeat | `Option[RoomId]` | .orElse(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueJoinRules.scala:55 | queueRequestsAfterJoin | `Map[QueueRequestId, TicketId]` | match None, case None |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueLeaveRules.scala:16 | leave | `BattleQueueLeaveTransition` | .get(, match None, case None |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueParticipantRules.scala:11 | normalizeOptionalText | `Option[String]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRequestReuseRules.scala:16 | reuseWaitingRequest | `BattleQueueRequestReuseResult` | .get(, .filter(, match None, case None |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomLifecycleRules.scala:53 | markFinished | `Map[RoomId, QueueRoom]` | .get(, match None, case None |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala:11 | openWaitingRooms | `Vector[QueueRoom]` | .filter( |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala:18 | reusableRoom | `Option[QueueRoom]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:64 | finishedAt | `Option[EpochMillis]` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:67 | battleSession | `Option[BattleSessionDescriptor]` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:73 | markFinished | `QueueRoom` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:89 | finishedAt | `Option[EpochMillis]` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:96 | battleSession | `Option[BattleSessionDescriptor]` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala:105 | markFinished | `QueueRoomLifecycle` | Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:32 | join | `BattleQueueSnapshot` | .get(, match None, case None |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:64 | status | `Either[BattleQueueStatusError, BattleQueueSnapshot]` | .get(, .toRight(, Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:76 | leave | `BattleQueueLeaveOutcome` | .get( |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:90 | roomSnapshot | `Either[BattleRoomError, RealtimeRoomSnapshot]` | .get(, .toRight(, Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:100 | heartbeat | `Either[BattleRoomError, RealtimeRoomSnapshot]` | .get(, match None, case None, Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:124 | markBattleFinished | `Unit` | .get( |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:131 | activeBattleSession | `Option[BattleSessionSeed]` | .get(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:22 | status | `Either[BattleQueueStatusError, BattleQueueSnapshot]` | Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:28 | roomSnapshot | `Either[BattleRoomError, RealtimeRoomSnapshot]` | Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:31 | heartbeat | `Either[BattleRoomError, RealtimeRoomSnapshot]` | Either return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueSessionLookupRules.scala:10 | activeBattleSession | `Option[BattleSessionSeed]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala:11 | snapshotForTicket | `Option[BattleQueueSnapshot]` | .get(, .find(, Option return |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala:24 | snapshotForWaitingTicket | `Option[BattleQueueSnapshot]` | .get(, .find(, Option return |
| backend/src/main/scala/services/battle/microservices/runtime/services/BattleEventFactory.scala:11 | battleEvent | `BattleEventState` | Option return |
| backend/src/main/scala/services/battle/microservices/session/services/BattleFailureMessageFormatter.scala:5 | throwableMessage | `String` | .filter( |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:29 | activeBattleSession | `Option[BattleSessionSeed]` | Option return |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:45 | currentState | `Either[BattleStateReadError, BattleAggregateState]` | Either return |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:47 | acceptCommand | `Either[BattleCommandSubmitError, BattleCommandAccepted]` | Either return |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:71 | currentState | `Either[BattleStateReadError, BattleAggregateState]` | match None, case None, Either return |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:91 | acceptCommand | `Either[BattleCommandSubmitError, BattleCommandAccepted]` | .get(, .find(, match None, case None, Either return |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCatalog.scala:11 | withMap | `A` | .get(, match None, case None |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala:7 | firstSegmentWorldExitT | `Option[Double]` | .filter(, Option return |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala:37 | firstSegmentObstacleEnterT | `Option[Double]` | Option return |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala:54 | firstSegmentAabbEnterT | `Option[Double]` | Option return |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala:79 | segmentAxisInterval | `Option[(Double, Double)]` | Option return |
| backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala:102 | segmentCircleHitT | `Option[Double]` | Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:59 | listTopics | `Vector[ForumTopicView]` | Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:60 | loadTopic | `Option[ForumTopicView]` | Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:61 | createTopic | `Either[ForumCreateTopicError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:62 | addReply | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:63 | setTopicVote | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:64 | setReplyVote | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:68 | listTopics | `Vector[ForumTopicView]` | Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:71 | loadTopic | `Option[ForumTopicView]` | Option return |
| backend/src/main/scala/services/forum/services/ForumService.scala:74 | createTopic | `Either[ForumCreateTopicError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:82 | addReply | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:85 | setTopicVote | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/forum/services/ForumService.scala:88 | setReplyVote | `Either[ForumTopicMutationError, ForumTopicView]` | Either return |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:46 | listReviewNotifications | `Vector[GovernanceReviewNotificationRecord]` | Option return |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:86 | listReviewNotifications | `Vector[GovernanceReviewNotificationRecord]` | Option return |
| backend/src/main/scala/services/identity/services/BuiltinAdminIdentity.scala:19 | account | `IdentityAccount` | Option return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:31 | register | `Either[IdentityRegistrationError, IdentityAccount]` | Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:32 | issueSession | `Either[IdentitySessionError, IdentityAccount]` | Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:33 | current | `Either[IdentityCurrentSessionError, IdentityAccount]` | Option return, Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:56 | register | `Either[IdentityRegistrationError, IdentityAccount]` | Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:74 | issueSession | `Either[IdentitySessionError, IdentityAccount]` | match None, case None, Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:94 | current | `Either[IdentityCurrentSessionError, IdentityAccount]` | .get(, .filter(, .toRight(, .orElse(, match None, case None, Option return, Either return |
| backend/src/main/scala/services/identity/services/IdentityService.scala:105 | listActiveAccounts | `Vector[IdentityAccountSummary]` | .filter( |
| backend/src/main/scala/services/mail/services/MailService.scala:15 | markRead | `Either[MailReadError, MailRecord]` | Either return |
| backend/src/main/scala/services/mail/services/MailService.scala:19 | list | `Vector[MailRecord]` | match None, case None |
| backend/src/main/scala/services/mail/services/MailService.scala:29 | markRead | `Either[MailReadError, MailRecord]` | match None, case None, Either return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:56 | record | `Either[ReplayRecordError, ReplayRecord]` | Either return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:58 | load | `Option[ReplayRecord]` | Option return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:59 | addComment | `Either[ReplayCommentError, ReplayCommentRecord]` | Either return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:64 | record | `Either[ReplayRecordError, ReplayRecord]` | Either return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:76 | list | `Vector[ReplayRecord]` | .filter( |
| backend/src/main/scala/services/replay/services/ReplayService.scala:84 | load | `Option[ReplayRecord]` | .filter(, Option return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:89 | addComment | `Either[ReplayCommentError, ReplayCommentRecord]` | Either return |
| backend/src/main/scala/services/replay/services/ReplayService.scala:92 | listComments | `Vector[ReplayCommentRecord]` | .filter(, match None, case None |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:38 | notificationMail | `Option[MailRecord]` | Option return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:55 | notificationMail | `Option[MailRecord]` | Option return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:63 | create | `Either[FriendRequestCreateError, FriendRequestSubmissionResult]` | Either return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:67 | respond | `Either[FriendRequestRespondError, FriendRequestResponseResult]` | Either return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:73 | find | `Option[FriendRequestRecord]` | Option return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:82 | create | `Either[FriendRequestCreateError, FriendRequestSubmissionResult]` | Either return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:88 | respond | `Either[FriendRequestRespondError, FriendRequestResponseResult]` | Either return |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:95 | list | `Vector[FriendRequestRecord]` | .filter(, match None, case None |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:103 | find | `Option[FriendRequestRecord]` | .filter(, Option return |

## Effectful service boundaries without IO

| File:line | def | Return type | Effects seen |
|---|---|---|---|
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:10 | write | `Unit` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:18 | write | `Unit` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:31 | apply | `BattleResultProjectionArtifactWriter` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionFailureReporter.scala:12 | reportFailure | `Unit` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala:96 | apply | `DefaultBattleFinishProjector` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleProjectionPorts.scala:7 | publish | `Unit` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleProjectionPorts.scala:11 | saveReplay | `Unit` | external side effect/IO |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:32 | join | `BattleQueueSnapshot` | time, lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:64 | status | `Either[BattleQueueStatusError, BattleQueueSnapshot]` | time, lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:76 | leave | `BattleQueueLeaveOutcome` | lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:90 | roomSnapshot | `Either[BattleRoomError, RealtimeRoomSnapshot]` | time, lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:100 | heartbeat | `Either[BattleRoomError, RealtimeRoomSnapshot]` | time, lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:124 | markBattleFinished | `Unit` | lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:131 | activeBattleSession | `Option[BattleSessionSeed]` | time, lock, mutable state |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:213 | apply | `InMemoryBattleQueueService` | time, system time |
| backend/src/main/scala/services/battle/microservices/session/services/BattleIdGenerator.scala:14 | nextBattleId | `BattleId` | random uuid |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:71 | currentState | `Either[BattleStateReadError, BattleAggregateState]` | time, lock |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:91 | acceptCommand | `Either[BattleCommandSubmitError, BattleCommandAccepted]` | time, lock |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:206 | apply | `InMemoryBattleStateService` | time, system time |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:11 | list | `Vector[BotProfileRecord]` | repository |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:23 | list | `Vector[BotProfileRecord]` | repository |
| backend/src/main/scala/services/forum/services/ForumService.scala:68 | listTopics | `Vector[ForumTopicView]` | repository |
| backend/src/main/scala/services/forum/services/ForumService.scala:71 | loadTopic | `Option[ForumTopicView]` | repository |
| backend/src/main/scala/services/forum/services/ForumService.scala:181 | apply | `DefaultForumService` | time, system time |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:64 | list | `Vector[ContributionAdjustmentRecord]` | repository |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:67 | create | `ContributionAdjustmentSubmissionResult` | time, repository |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:86 | listReviewNotifications | `Vector[GovernanceReviewNotificationRecord]` | repository |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:93 | createReviewNotification | `GovernanceReviewNotificationSubmissionResult` | time, repository |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:151 | apply | `DefaultGovernanceService` | time, system time |
| backend/src/main/scala/services/identity/services/IdentityService.scala:56 | register | `Either[IdentityRegistrationError, IdentityAccount]` | repository |
| backend/src/main/scala/services/identity/services/IdentityService.scala:74 | issueSession | `Either[IdentitySessionError, IdentityAccount]` | repository |
| backend/src/main/scala/services/identity/services/IdentityService.scala:94 | current | `Either[IdentityCurrentSessionError, IdentityAccount]` | repository |
| backend/src/main/scala/services/mail/services/MailService.scala:19 | list | `Vector[MailRecord]` | repository |
| backend/src/main/scala/services/mail/services/MailService.scala:67 | apply | `DefaultMailService` | time, system time |
| backend/src/main/scala/services/replay/services/ReplayService.scala:64 | record | `Either[ReplayRecordError, ReplayRecord]` | repository, external side effect/IO |
| backend/src/main/scala/services/replay/services/ReplayService.scala:84 | load | `Option[ReplayRecord]` | repository |
| backend/src/main/scala/services/replay/services/ReplayService.scala:137 | apply | `DefaultReplayService` | time, system time |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:95 | list | `Vector[FriendRequestRecord]` | repository |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:103 | find | `Option[FriendRequestRecord]` | repository |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:179 | apply | `DefaultFriendRequestService` | time, system time |
