# Non-IO service function audit

Scope: `backend/src/main/scala/**/services/*.scala`. A hit is any `def` whose explicit return type is not `IO[...]`, plus inferred-return `def`s in that services directory. Pure model/object files outside `services/` are intentionally excluded.

Summary: 84 service files scanned, 479 defs found, 477 non-IO defs in 83 files, 2 IO defs.

## Bucket counts

| Bucket | Non-IO defs | Files |
|---|---:|---:|
| battle.projections.services | 81 | 13 |
| battle.queue.services | 68 | 15 |
| battle.results.services | 4 | 1 |
| battle.rules.services | 174 | 30 |
| battle.session.services | 34 | 10 |
| bots.services | 6 | 1 |
| forum.services | 23 | 1 |
| governance.services | 18 | 2 |
| identity.services | 14 | 2 |
| mail.services | 9 | 1 |
| replay.services | 18 | 3 |
| social.services | 25 | 3 |
| system.services | 3 | 1 |

## Priority non-IO service boundaries

These are public or protected service/port methods that mutate/read state, cross repositories/ports, use time/random IDs, or expose service contracts without `IO`.

| File:line | def | Return type |
|---|---|---|
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:10 | write | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:18 | write | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:31 | apply | `BattleResultProjectionArtifactWriter` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:41 | write | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala:47 | apply | `BattleReplayProjectionArtifactWriter` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionFailureReporter.scala:7 | reportFailure | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionFailureReporter.scala:12 | reportFailure | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala:27 | project | `BattleFinishProjectionOutcome` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala:96 | apply | `DefaultBattleFinishProjector` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala:111 | combine | `BattleFinishProjectionOutcome` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleProjectionPorts.scala:7 | publish | `Unit` |
| backend/src/main/scala/services/battle/microservices/projections/services/BattleProjectionPorts.scala:11 | saveReplay | `Unit` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala:15 | authorize | `Either[BattleQueueJoinAuthorizationError, Unit]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala:22 | authorize | `Either[BattleQueueJoinAuthorizationError, Unit]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala:36 | apply | `DefaultBattleQueueJoinAuthorizationService` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:32 | join | `BattleQueueSnapshot` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:64 | status | `Either[BattleQueueStatusError, BattleQueueSnapshot]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:76 | leave | `BattleQueueLeaveOutcome` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:90 | roomSnapshot | `Either[BattleRoomError, RealtimeRoomSnapshot]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:100 | heartbeat | `Either[BattleRoomError, RealtimeRoomSnapshot]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:124 | markBattleFinished | `Unit` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:131 | activeBattleSession | `Option[BattleSessionSeed]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala:213 | apply | `InMemoryBattleQueueService` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:19 | join | `BattleQueueSnapshot` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:22 | status | `Either[BattleQueueStatusError, BattleQueueSnapshot]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:25 | leave | `BattleQueueLeaveOutcome` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:28 | roomSnapshot | `Either[BattleRoomError, RealtimeRoomSnapshot]` |
| backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala:31 | heartbeat | `Either[BattleRoomError, RealtimeRoomSnapshot]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleIdGenerator.scala:9 | nextBattleId | `BattleId` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleIdGenerator.scala:14 | nextBattleId | `BattleId` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:29 | activeBattleSession | `Option[BattleSessionSeed]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:45 | currentState | `Either[BattleStateReadError, BattleAggregateState]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:47 | acceptCommand | `Either[BattleCommandSubmitError, BattleCommandAccepted]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:52 | markBattleFinished | `Unit` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:57 | markBattleFinished | `Unit` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:71 | currentState | `Either[BattleStateReadError, BattleAggregateState]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:91 | acceptCommand | `Either[BattleCommandSubmitError, BattleCommandAccepted]` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:190 | apply | `InMemoryBattleStateService` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:194 | apply | `InMemoryBattleStateService` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:198 | apply | `InMemoryBattleStateService` |
| backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala:206 | apply | `InMemoryBattleStateService` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:7 | list | `Vector[BotProfileRecord]` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:11 | list | `Vector[BotProfileRecord]` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:16 | apply | `DefaultBotProfileService` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:23 | list | `Vector[BotProfileRecord]` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:28 | demo | `StaticBotProfileService` |
| backend/src/main/scala/services/bots/services/BotProfileService.scala:31 | apply | `StaticBotProfileService` |
| backend/src/main/scala/services/forum/services/ForumService.scala:59 | listTopics | `Vector[ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:60 | loadTopic | `Option[ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:61 | createTopic | `Either[ForumCreateTopicError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:62 | addReply | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:63 | setTopicVote | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:64 | setReplyVote | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:68 | listTopics | `Vector[ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:71 | loadTopic | `Option[ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:74 | createTopic | `Either[ForumCreateTopicError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:82 | addReply | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:85 | setTopicVote | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:88 | setReplyVote | `Either[ForumTopicMutationError, ForumTopicView]` |
| backend/src/main/scala/services/forum/services/ForumService.scala:176 | apply | `DefaultForumService` |
| backend/src/main/scala/services/forum/services/ForumService.scala:181 | apply | `DefaultForumService` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:40 | list | `Vector[ContributionAdjustmentRecord]` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:42 | create | `ContributionAdjustmentSubmissionResult` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:46 | listReviewNotifications | `Vector[GovernanceReviewNotificationRecord]` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:52 | createReviewNotification | `GovernanceReviewNotificationSubmissionResult` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:64 | list | `Vector[ContributionAdjustmentRecord]` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:67 | create | `ContributionAdjustmentSubmissionResult` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:86 | listReviewNotifications | `Vector[GovernanceReviewNotificationRecord]` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:93 | createReviewNotification | `GovernanceReviewNotificationSubmissionResult` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:142 | apply | `DefaultGovernanceService` |
| backend/src/main/scala/services/governance/services/GovernanceServices.scala:151 | apply | `DefaultGovernanceService` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:31 | register | `Either[IdentityRegistrationError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:32 | issueSession | `Either[IdentitySessionError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:33 | current | `Either[IdentityCurrentSessionError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:34 | listActiveAccounts | `Vector[IdentityAccountSummary]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:56 | register | `Either[IdentityRegistrationError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:74 | issueSession | `Either[IdentitySessionError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:94 | current | `Either[IdentityCurrentSessionError, IdentityAccount]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:105 | listActiveAccounts | `Vector[IdentityAccountSummary]` |
| backend/src/main/scala/services/identity/services/IdentityService.scala:147 | apply | `DefaultIdentityService` |
| backend/src/main/scala/services/mail/services/MailService.scala:14 | list | `Vector[MailRecord]` |
| backend/src/main/scala/services/mail/services/MailService.scala:15 | markRead | `Either[MailReadError, MailRecord]` |
| backend/src/main/scala/services/mail/services/MailService.scala:19 | list | `Vector[MailRecord]` |
| backend/src/main/scala/services/mail/services/MailService.scala:29 | markRead | `Either[MailReadError, MailRecord]` |
| backend/src/main/scala/services/mail/services/MailService.scala:62 | apply | `DefaultMailService` |
| backend/src/main/scala/services/mail/services/MailService.scala:67 | apply | `DefaultMailService` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:56 | record | `Either[ReplayRecordError, ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:57 | list | `Vector[ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:58 | load | `Option[ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:59 | addComment | `Either[ReplayCommentError, ReplayCommentRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:60 | listComments | `Vector[ReplayCommentRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:64 | record | `Either[ReplayRecordError, ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:76 | list | `Vector[ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:84 | load | `Option[ReplayRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:89 | addComment | `Either[ReplayCommentError, ReplayCommentRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:92 | listComments | `Vector[ReplayCommentRecord]` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:132 | apply | `DefaultReplayService` |
| backend/src/main/scala/services/replay/services/ReplayService.scala:137 | apply | `DefaultReplayService` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:32 | friendRequest | `FriendRequestRecord` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:38 | notificationMail | `Option[MailRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:49 | friendRequest | `FriendRequestRecord` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:55 | notificationMail | `Option[MailRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:63 | create | `Either[FriendRequestCreateError, FriendRequestSubmissionResult]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:67 | respond | `Either[FriendRequestRespondError, FriendRequestResponseResult]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:72 | list | `Vector[FriendRequestRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:73 | find | `Option[FriendRequestRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:82 | create | `Either[FriendRequestCreateError, FriendRequestSubmissionResult]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:88 | respond | `Either[FriendRequestRespondError, FriendRequestResponseResult]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:95 | list | `Vector[FriendRequestRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:103 | find | `Option[FriendRequestRecord]` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:170 | apply | `DefaultFriendRequestService` |
| backend/src/main/scala/services/social/services/FriendRequestService.scala:179 | apply | `DefaultFriendRequestService` |
| backend/src/main/scala/system/services/HealthService.scala:8 | current | `HealthResponse` |
| backend/src/main/scala/system/services/HealthService.scala:16 | current | `HealthResponse` |
| backend/src/main/scala/system/services/HealthService.scala:26 | apply | `StaticHealthService` |

## Strict full services-directory list

### backend/src/main/scala/services/battle/microservices/abilities/services/BattlePickupRules.scala

- 16: collectPickups: `BattleAggregateState`
- 75: advancePickups: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillCommandRules.scala

- 28: applyBlinkCommand: `CommandApplication`
- 62: applyDashCommand: `CommandApplication`
- 97: applyFreezeCommand: `CommandApplication`
- 131: withAvailableSkill: `CommandApplication`
- 146: unavailableSkill: `CommandApplication`
- 153: skillOutcome: `BattleCommandSkillOutcome`
- 160: updateSkill: `Vector[BattlePlayerSkillState]`
- 171: isBlinkTargetAllowed: `Boolean`
- 178: blinkDestination: `BattleVector2`
- 181: replacePlayer: `BattleAggregateState`
- 184: normalizedDirection: `BattleVector2`

### backend/src/main/scala/services/battle/microservices/abilities/services/BattleSkillRules.scala

- 8: availabilityFailure: `Option[SkillOutcomeReason]`

### backend/src/main/scala/services/battle/microservices/abilities/services/BattleSlowFieldRuntimeRules.scala

- 8: advanceSlowFields: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/actors/services/BattleBotRules.scala

- 24: applyBotControl: `BattlePlayerState`
- 51: selectTarget: `Option[BotTarget]`
- 69: targetScore: `Double`
- 76: aimAtTarget: `BattleVector2`
- 87: combatMovement: `BattleVector2`
- 106: reloadMovement: `BattleVector2`
- 116: objectiveMovement: `BattleVector2`
- 122: pickupObjective: `Option[BattlePickupState]`
- 140: shouldRetreat: `Boolean`
- 146: coverOrRetreatDirection: `BattleVector2`
- 168: flankDirection: `BattleVector2`
- 171: chooseOpenMovement: `BattleVector2`
- 204: shouldFireAtTarget: `Boolean`
- 214: shouldBotReload: `Boolean`
- 225: canBotFireAtTarget: `Boolean`
- 228: isBotFirePulseOpen: `Boolean`
- 237: botFireRangeForTarget: `Double`
- 242: patrolTarget: `BattleVector2`
- 251: orbitDirection: `Double`
- 255: rotate: `BattleVector2`
- 261: clampToPlayable: `BattleVector2`

### backend/src/main/scala/services/battle/microservices/actors/services/BattleInputRules.scala

- 14: applyCommandToPlayer: `BattlePlayerState`
- 34: lastClientCommandSeq: `ClientCommandSeq`
- 37: normalizeAim: `BattleVector2`
- 42: maxClientCommandSeq: `ClientCommandSeq`

### backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerLifecycleRules.scala

- 7: clearFinishedPlayerRuntime: `BattlePlayerState`
- 10: clearDeadPlayerRuntime: `BattlePlayerState`
- 26: winnerFor: `Option[BattlePlayerState]`

### backend/src/main/scala/services/battle/microservices/actors/services/BattlePlayerRuntimeRules.scala

- 19: advancePlayers: `BattleAggregateState`
- 36: advancePlayerTimers: `BattlePlayerState`
- 76: movePlayer: `BattlePlayerState`
- 109: advanceWeaponHeat: `BattleWeaponState`
- 129: advanceStamina: `Stamina`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleHeldFireRuntimeRules.scala

- 8: resolveHeldPrimaryFire: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala

- 10: weaponProjectiles: `Vector[BattleProjectileState]`
- 36: resolvePistolShot: `BattleAggregateState`
- 49: projectileId: `ProjectileId`
- 59: spreadDirection: `BattleVector2`
- 74: rotate: `BattleVector2`
- 89: pistolProjectile: `BattleProjectileState`
- 110: projectileBirthPosition: `BattleVector2`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileImpactRules.scala

- 17: applyProjectileImpact: `BattleAggregateState`
- 41: applyRocketProjectileImpact: `BattleAggregateState`
- 82: damageProjectileTarget: `(BattleAggregateState, Option[ProjectileDamageReport])`
- 126: retainRecentEvents: `Vector[services.battle.microservices.runtime.objects.event.BattleEventState]`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileMotionRules.scala

- 20: resolveProjectileMotion: `ProjectileMotionResult`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileRuntimeRules.scala

- 23: advanceProjectiles: `BattleAggregateState`
- 101: firstProjectileBlock: `Option[ProjectileBlock]`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala

- 15: findProjectilePlayerHit: `Option[ProjectilePlayerHit]`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala

- 16: appendProjectileTerminal: `BattleAggregateState`
- 24: terminalForProjectile: `BattleProjectileTerminalState`
- 54: retainRecentProjectileTerminals: `Vector[BattleProjectileTerminalState]`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponFireRules.scala

- 15: applyPrimaryFire: `BattleAggregateState`
- 82: resolveRequestedReloads: `BattleAggregateState`
- 100: runtimeFireCommandSeq: `ClientCommandSeq`
- 103: applyWeaponRecoil: `BattlePlayerState`
- 122: chargeHeatWeapon: `BattleWeaponState`
- 143: projectileBirthOffset: `Double`
- 148: replacePlayer: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/combat/services/BattleWeaponRules.scala

- 17: currentWeapon: `Option[BattleWeaponState]`
- 20: fireDefinition: `BattleWeaponFireDefinition`
- 23: inventoryDefinition: `BattleWeaponInventoryDefinition`
- 26: heatDefinition: `Option[BattleWeaponHeatDefinition]`
- 30: updateCurrentWeapon: `BattlePlayerState`
- 39: canFireMagazineWeapon: `Boolean`
- 45: canFireHeatWeapon: `Boolean`
- 51: chargeMagazineWeapon: `BattleWeaponState`
- 60: shouldAutoReload: `Boolean`
- 64: canStartMagazineReload: `Boolean`
- 72: startMagazineReload: `BattleWeaponState`
- 76: finishReload: `BattleWeaponState`
- 91: createWeaponState: `BattleWeaponState`
- 106: refillWeaponState: `BattleWeaponState`
- 121: equipOrRefillWeapon: `BattlePlayerState`
- 131: applyWeaponSwitchRequest: `BattlePlayerState`
- 167: clampWeaponIndex: `Int`
- 171: weaponUsesHeat: `Boolean`
- 174: usesHeatResource: `Boolean`
- 177: weaponReloadMs: `Int`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionArtifactWriters.scala

- 10: write: `Unit`
- 18: write: `Unit`
- 31: apply: `BattleResultProjectionArtifactWriter`
- 41: write: `Unit`
- 47: apply: `BattleReplayProjectionArtifactWriter`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionFailureReporter.scala

- 7: reportFailure: `Unit`
- 12: reportFailure: `Unit`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionLabelRules.scala

- 32: finishedAtLabel: `String`
- 36: modeLabel: `BattleModeLabel`
- 40: mapLabel: `BattleMapLabel`
- 44: resultLabel: `BattleResultLabel`
- 52: highlightLine: `BattleHighlightLine`
- 62: timelineHint: `BattleTimelineHint`
- 75: playersLine: `BattlePlayersLine`
- 85: serverDisplayName: `DisplayName`
- 89: serverResultLabel: `BattleResultLabel`
- 93: serverHighlightLine: `BattleHighlightLine`
- 97: serverTimelineHint: `BattleTimelineHint`
- 101: replayTitle: `ReplayTitle`
- 108: replayResultLabel: `String`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionMailFactory.scala

- 11: battleMail: `MailRecord`
- 30: ratingMail: `MailRecord`
- 48: replaySourcePath: `String`
- 51: urlEncode: `String`
- 54: signed: `String`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlanner.scala

- 28: toVector: `Vector[BattleSettlement]`
- 31: map: `Vector[A]`
- 34: foreach: `Unit`
- 37: find: `Option[BattleSettlement]`
- 42: fromVectorOrFallback: `BattleSettlements`
- 72: ratingBefore: `Rating`
- 81: fromRatings: `BattlePreviousRatings`
- 89: build: `BattleFinishProjectionPlan`
- 101: humanPlayersByPlacement: `Vector[BattlePlayerState]`
- 106: buildSettlements: `BattleSettlements`
- 126: settlementBuildContext: `BattleSettlementBuildContext`
- 141: playableSettlementFor: `BattleSettlement`
- 178: serverResult: `BattleResultRecord`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionPlayerRules.scala

- 8: playersByPlacement: `Vector[BattlePlayerState]`
- 15: isPlayableHumanPlayer: `Boolean`
- 19: safeDisplayName: `String`
- 24: safeHandle: `String`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionReplayRules.scala

- 9: replayOwnerSettlement: `BattleSettlement`
- 18: replayRecord: `ReplayRecord`
- 56: replaySettlement: `ReplaySettlementRecord`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionService.scala

- 27: project: `BattleFinishProjectionOutcome`
- 50: writeArtifact: `BattleProjectionArtifactWriteOutcome`
- 58: catchArtifactWriteFailure: `BattleProjectionArtifactWriteOutcome`
- 72: previousRatingsFor: `BattlePreviousRatings`
- 80: fetchPreviousRating: `Rating`
- 88: failureMessage: `String`
- 96: apply: `DefaultBattleFinishProjector`
- 111: combine: `BattleFinishProjectionOutcome`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleFinishProjectionTimeRules.scala

- 7: projectedDuration: `DurationMillis`
- 11: projectedFinishedAt: `EpochMillis`
- 17: clampElapsed: `Long`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleProjectionPorts.scala

- 7: publish: `Unit`
- 11: saveReplay: `Unit`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFramesJsonRenderer.scala

- 39: render: `BattleReplayFramesJson`
- 52: fallbackReplayFramePayloads: `Vector[BattleReplayFramePayload]`
- 60: replayFramePayload: `BattleReplayFramePayload`
- 73: replayFramePayload: `BattleReplayFramePayload`
- 87: replayFramePayload: `BattleReplayFramePayload`
- 104: heroFramePayload: `BattleReplayHeroPayload`
- 120: heroFramePayload: `BattleReplayHeroPayload`
- 144: heroFramePayload: `BattleReplayHeroPayload`
- 172: projectileFramePayload: `BattleReplayProjectilePayload`
- 182: projectileFramePayload: `BattleReplayProjectilePayload`
- 192: projectileFramePayload: `BattleReplayProjectilePayload`
- 210: pickupFramePayload: `BattleReplayPickupPayload`
- 219: pickupFramePayload: `BattleReplayPickupPayload`
- 228: pickupFramePayload: `BattleReplayPickupPayload`
- 253: eventMessages: `Vector[String]`
- 260: replayDisplayName: `String`
- 268: replayPickupKind: `String`
- 274: vectorPayload: `BattleReplayVectorPayload`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleReplayFrameTimelineRules.scala

- 14: normalizeReplayFrames: `Vector[BattleReplayFrameState]`
- 29: fallbackTimeline: `BattleReplayFallbackTimeline`
- 50: clampElapsed: `Long`

### backend/src/main/scala/services/battle/microservices/projections/services/BattleSettlementScoringRules.scala

- 10: placementScore: `Int`
- 21: ratingDelta: `RatingDelta`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueAuthorizationService.scala

- 15: authorize: `Either[BattleQueueJoinAuthorizationError, Unit]`
- 22: authorize: `Either[BattleQueueJoinAuthorizationError, Unit]`
- 36: apply: `DefaultBattleQueueJoinAuthorizationService`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueHeartbeatRules.scala

- 9: roomIdForHeartbeat: `Option[RoomId]`
- 16: updateHeartbeat: `QueueRoom`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueIdAllocator.scala

- 11: allocateTicketId: `(TicketId, BattleQueueIdAllocator)`
- 18: allocateRoomId: `(RoomId, BattleQueueIdAllocator)`
- 25: allocatePlayerId: `(PlayerId, BattleQueueIdAllocator)`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueJoinRules.scala

- 15: normalizeCommand: `BattleQueueJoinCommand`
- 19: draft: `BattleQueueJoinDraft`
- 55: queueRequestsAfterJoin: `Map[QueueRequestId, TicketId]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueLeaveRules.scala

- 16: leave: `BattleQueueLeaveTransition`
- 49: roomsAfterLeave: `Map[RoomId, QueueRoom]`
- 67: queueRequestsAfterLeave: `Map[QueueRequestId, TicketId]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueParticipantRules.scala

- 11: normalizeOptionalText: `Option[String]`
- 15: normalizeHandle: `PlayerHandle`
- 19: sameHandleKey: `Boolean`
- 23: heartbeatMatches: `Boolean`
- 30: touchHeartbeatParticipant: `QueueParticipantEntry`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRequestReuseRules.scala

- 16: reuseWaitingRequest: `BattleQueueRequestReuseResult`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomLifecycleRules.scala

- 10: newWaitingRoom: `QueueRoom`
- 30: startDecision: `QueueRoomStartDecision`
- 39: startRoom: `QueueRoom`
- 53: markFinished: `Map[RoomId, QueueRoom]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRoomSelectionRules.scala

- 11: openWaitingRooms: `Vector[QueueRoom]`
- 18: reusableRoom: `Option[QueueRoom]`
- 28: shouldStartFreshRoom: `Boolean`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueRuntimeModel.scala

- 27: withRooms: `QueueRuntimeState`
- 30: withRoom: `QueueRuntimeState`
- 33: withTickets: `QueueRuntimeState`
- 36: withQueueRequests: `QueueRuntimeState`
- 61: phase: `MatchmakingRoomPhase`
- 64: finishedAt: `Option[EpochMillis]`
- 67: battleSession: `Option[BattleSessionDescriptor]`
- 70: isWaiting: `Boolean`
- 73: markFinished: `QueueRoom`
- 82: phase: `MatchmakingRoomPhase`
- 89: finishedAt: `Option[EpochMillis]`
- 96: battleSession: `Option[BattleSessionDescriptor]`
- 105: markFinished: `QueueRoomLifecycle`
- 135: toQueueSnapshot: `BattleQueueSnapshot`
- 158: toRoomSnapshot: `RealtimeRoomSnapshot`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueService.scala

- 32: join: `BattleQueueSnapshot`
- 64: status: `Either[BattleQueueStatusError, BattleQueueSnapshot]`
- 76: leave: `BattleQueueLeaveOutcome`
- 90: roomSnapshot: `Either[BattleRoomError, RealtimeRoomSnapshot]`
- 100: heartbeat: `Either[BattleRoomError, RealtimeRoomSnapshot]`
- 124: markBattleFinished: `Unit`
- 131: activeBattleSession: `Option[BattleSessionSeed]`
- 140: selectJoinRoom: `(QueueRoom, QueueRuntimeState)`
- 154: createRoom: `(QueueRoom, QueueRuntimeState)`
- 160: advanceRooms: `QueueRuntimeState`
- 163: advanceRoom: `QueueRoom`
- 171: reuseWaitingQueueRequestOrForgetStale: `(Option[BattleQueueSnapshot], QueueRuntimeState)`
- 192: nextTicketId: `(TicketId, QueueRuntimeState)`
- 197: nextRoomId: `(RoomId, QueueRuntimeState)`
- 202: nextPlayerId: `(PlayerId, QueueRuntimeState)`
- 213: apply: `InMemoryBattleQueueService`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueServiceContracts.scala

- 19: join: `BattleQueueSnapshot`
- 22: status: `Either[BattleQueueStatusError, BattleQueueSnapshot]`
- 25: leave: `BattleQueueLeaveOutcome`
- 28: roomSnapshot: `Either[BattleRoomError, RealtimeRoomSnapshot]`
- 31: heartbeat: `Either[BattleRoomError, RealtimeRoomSnapshot]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueSessionLookupRules.scala

- 10: activeBattleSession: `Option[BattleSessionSeed]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleQueueTicketSnapshots.scala

- 11: snapshotForTicket: `Option[BattleQueueSnapshot]`
- 24: snapshotForWaitingTicket: `Option[BattleQueueSnapshot]`

### backend/src/main/scala/services/battle/microservices/queue/services/BattleRoomBootstrapper.scala

- 39: createSession: `BattleSessionDescriptor`
- 89: buildBotSeat: `BattleSessionBootstrapSeat`

### backend/src/main/scala/services/battle/microservices/results/services/BattleResultService.scala

- 40: listPlayableRecords: `Vector[BattleResultRecord]`
- 54: validateRecordHandle: `Either[BattleResultRecordValidationError, PlayerHandle]`
- 61: buildRecord: `BattleResultRecord`
- 84: nonEmpty: `Option[String]`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleCommandApplicationRules.scala

- 22: applyCommand: `CommandApplication`
- 48: battleSkillCommandEnvironment: `BattleSkillCommandEnvironment`
- 63: motionDestination: `BattleVector2`
- 76: battleInputEnvironment: `BattleInputEnvironment`
- 82: replacePlayer: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleEngine.scala

- 34: WorldSize: `BattleVector2`
- 40: withMap: `A`
- 43: initialPickups: `Vector[BattlePickupState]`
- 46: spawnPointFor: `BattleVector2`
- 49: createWeaponState: `BattleWeaponState`
- 52: captureReplayFrame: `BattleReplayFrameState`
- 60: advanceStateStep: `BattleAggregateState`
- 69: finishedAtForRoom: `EpochMillis`
- 72: lastClientCommandSeq: `ClientCommandSeq`
- 75: applyCommand: `CommandApplication`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleEventFactory.scala

- 11: battleEvent: `BattleEventState`
- 31: pickupEventId: `BattleEventId`
- 42: weaponPickupEventMessage: `String`
- 47: eventParticipant: `BattleEventParticipant`
- 54: eventMessage: `String`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleReplayFrameRecorder.scala

- 20: updateFrames: `Vector[BattleReplayFrameState]`
- 35: appendFrame: `Vector[BattleReplayFrameState]`
- 48: captureFrame: `BattleReplayFrameState`
- 96: shouldRecordIntervalFrame: `Boolean`
- 106: retainFrames: `Vector[BattleReplayFrameState]`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeFinalizationRules.scala

- 9: finalizeRuntimeStep: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeFinishRules.scala

- 10: isBattleFinished: `Boolean`
- 16: finishRuntimeState: `BattleAggregateState`
- 45: finishedAtForRoom: `EpochMillis`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleRuntimeStepRules.scala

- 13: advanceStateStep: `BattleAggregateState`

### backend/src/main/scala/services/battle/microservices/runtime/services/BattleTimeRules.scala

- 7: elapsedAt: `Long`
- 11: elapsedRateDeltaDouble: `Double`
- 15: elapsedRateDelta: `Int`
- 22: decrementInt: `Int`
- 26: decrementLong: `Long`

### backend/src/main/scala/services/battle/microservices/session/services/BattleCommandAcceptanceFactory.scala

- 16: ignored: `BattleCommandAccepted`
- 32: applied: `BattleCommandAccepted`
- 48: ignoredReason: `BattleCommandReason`

### backend/src/main/scala/services/battle/microservices/session/services/BattleFailureMessageFormatter.scala

- 5: throwableMessage: `String`

### backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionCompletionRules.scala

- 7: complete: `StoredBattle`

### backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionPreparationRules.scala

- 14: prepare: `BattleFinishProjectionPreparation`

### backend/src/main/scala/services/battle/microservices/session/services/BattleFinishProjectionStatusRules.scala

- 8: artifactStatusAfterProjection: `BattleArtifactStatus`
- 15: finishProjectionStatusAfter: `BattleFinishProjectionStatus`
- 32: readyOrFailedProjectionStatus: `BattleFinishProjectionStatus`

### backend/src/main/scala/services/battle/microservices/session/services/BattleIdGenerator.scala

- 9: nextBattleId: `BattleId`
- 14: nextBattleId: `BattleId`

### backend/src/main/scala/services/battle/microservices/session/services/BattleSessionStateFactory.scala

- 33: createInitialState: `BattleAggregateState`
- 69: bootstrapSeats: `Vector[BattleSessionBootstrapSeat]`
- 88: toPlayerState: `BattlePlayerState`

### backend/src/main/scala/services/battle/microservices/session/services/BattleStateService.scala

- 29: activeBattleSession: `Option[BattleSessionSeed]`
- 45: currentState: `Either[BattleStateReadError, BattleAggregateState]`
- 47: acceptCommand: `Either[BattleCommandSubmitError, BattleCommandAccepted]`
- 52: markBattleFinished: `Unit`
- 57: markBattleFinished: `Unit`
- 71: currentState: `Either[BattleStateReadError, BattleAggregateState]`
- 91: acceptCommand: `Either[BattleCommandSubmitError, BattleCommandAccepted]`
- 129: findOrInitialize: `Option[StoredBattle]`
- 138: advanceStoredBattle: `StoredBattle`
- 146: prepareProjection: `(StoredBattle, Option[BattleAggregateState])`
- 151: storeCommandSubmission: `CommandSubmission`
- 161: completeProjection: `BattleAggregateState`
- 177: projectFinishArtifacts: `BattleFinishProjectionOutcome`
- 190: apply: `InMemoryBattleStateService`
- 194: apply: `InMemoryBattleStateService`
- 198: apply: `InMemoryBattleStateService`
- 206: apply: `InMemoryBattleStateService`

### backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleAdvanceRules.scala

- 19: advance: `BattleStoredBattleAdvanceResult`
- 61: roomFinishedWhenTransitioned: `Option[BattleRoomFinishedNotification]`

### backend/src/main/scala/services/battle/microservices/session/services/BattleStoredBattleInitializationRules.scala

- 8: fromSeed: `StoredBattle`

### backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCatalog.scala

- 11: withMap: `A`
- 23: loadedMap: `BattleLoadedMapSpec`
- 26: activeMap: `BattleLoadedMapSpec`
- 31: WorldSize: `BattleVector2`
- 33: FloorTileSize: `Int`
- 34: BorderObstacleSize: `BattleVector2`
- 35: MotionStepSize: `Double`
- 36: MapId: `BattleMapId`
- 37: ThemeId: `String`
- 38: PlayerCollisionRadius: `Double`
- 39: ProjectileBirthClearance: `Double`
- 40: ProjectileShooterAdvantageRadius: `Double`
- 41: ArenaObstacles: `Vector[ArenaObstacle]`
- 42: SpawnPoints: `Vector[BattleVector2]`
- 43: PickupDefinitions: `Vector[BattlePickupDefinition]`
- 47: borderObstacles: `Vector[ArenaObstacle]`
- 87: innerObstacles: `Vector[ArenaObstacle]`

### backend/src/main/scala/services/battle/microservices/world/services/BattleArenaCollision.scala

- 7: firstSegmentWorldExitT: `Option[Double]`
- 37: firstSegmentObstacleEnterT: `Option[Double]`
- 54: firstSegmentAabbEnterT: `Option[Double]`
- 79: segmentAxisInterval: `Option[(Double, Double)]`
- 93: isPointInAabb: `Boolean`
- 102: segmentCircleHitT: `Option[Double]`
- 137: isBlockedPoint: `Boolean`
- 140: canPlayerOccupy: `Boolean`
- 143: hasArenaLineOfSight: `Boolean`
- 148: collidesWithArenaObstacles: `Boolean`
- 151: intersectsObstacle: `Boolean`
- 167: isInWorld: `Boolean`
- 173: isInWorld: `Boolean`
- 179: clampToWorld: `BattleVector2`

### backend/src/main/scala/services/battle/microservices/world/services/BattleGeometry.scala

- 6: clampDouble: `Double`
- 9: add: `BattleVector2`
- 12: subtract: `BattleVector2`
- 15: scale: `BattleVector2`
- 18: pointAtSegmentT: `BattleVector2`
- 26: perpendicular: `BattleVector2`
- 29: dot: `Double`
- 32: vectorLength: `Double`
- 35: distanceBetween: `Double`

### backend/src/main/scala/services/battle/microservices/world/services/BattleInitialLayout.scala

- 7: spawnPointFor: `BattleVector2`
- 12: initialPickups: `Vector[BattlePickupState]`

### backend/src/main/scala/services/battle/microservices/world/services/BattleMotionRules.scala

- 19: normalizeMovement: `BattleVector2`
- 25: findMotionDestination: `SteppedMotionResult`
- 50: resolveSteppedMotion: `SteppedMotionResult`

### backend/src/main/scala/services/bots/services/BotProfileService.scala

- 7: list: `Vector[BotProfileRecord]`
- 11: list: `Vector[BotProfileRecord]`
- 16: apply: `DefaultBotProfileService`
- 23: list: `Vector[BotProfileRecord]`
- 28: demo: `StaticBotProfileService`
- 31: apply: `StaticBotProfileService`

### backend/src/main/scala/services/forum/services/ForumService.scala

- 59: listTopics: `Vector[ForumTopicView]`
- 60: loadTopic: `Option[ForumTopicView]`
- 61: createTopic: `Either[ForumCreateTopicError, ForumTopicView]`
- 62: addReply: `Either[ForumTopicMutationError, ForumTopicView]`
- 63: setTopicVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 64: setReplyVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 68: listTopics: `Vector[ForumTopicView]`
- 71: loadTopic: `Option[ForumTopicView]`
- 74: createTopic: `Either[ForumCreateTopicError, ForumTopicView]`
- 82: addReply: `Either[ForumTopicMutationError, ForumTopicView]`
- 85: setTopicVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 88: setReplyVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 91: createParsedTopic: `ForumTopicView`
- 108: validateTitle: `Either[ForumCreateTopicError, ForumTitle]`
- 111: validateCreateBody: `Either[ForumCreateTopicError, ForumBody]`
- 114: validateTag: `Either[ForumCreateTopicError, ForumTag]`
- 117: validateCreateAuthor: `Either[ForumCreateTopicError, PlayerHandle]`
- 124: addParsedReply: `Either[ForumTopicMutationError, ForumTopicView]`
- 144: setParsedTopicVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 155: setParsedReplyVote: `Either[ForumTopicMutationError, ForumTopicView]`
- 167: toMutationError: `ForumTopicMutationError`
- 176: apply: `DefaultForumService`
- 181: apply: `DefaultForumService`

### backend/src/main/scala/services/governance/services/GovernanceMailFactory.scala

- 15: contributionMail: `GovernanceMailSnapshot`
- 29: reviewMail: `GovernanceMailSnapshot`
- 49: contributionMailExcerpt: `String`
- 60: reviewMailExcerpt: `String`
- 66: reviewTargetLabel: `String`
- 69: formatDelta: `String`

### backend/src/main/scala/services/governance/services/GovernanceServices.scala

- 40: list: `Vector[ContributionAdjustmentRecord]`
- 42: create: `ContributionAdjustmentSubmissionResult`
- 46: listReviewNotifications: `Vector[GovernanceReviewNotificationRecord]`
- 52: createReviewNotification: `GovernanceReviewNotificationSubmissionResult`
- 64: list: `Vector[ContributionAdjustmentRecord]`
- 67: create: `ContributionAdjustmentSubmissionResult`
- 86: listReviewNotifications: `Vector[GovernanceReviewNotificationRecord]`
- 93: createReviewNotification: `GovernanceReviewNotificationSubmissionResult`
- 115: persistMail: `Unit`
- 137: clampLimit: `Int`
- 142: apply: `DefaultGovernanceService`
- 151: apply: `DefaultGovernanceService`

### backend/src/main/scala/services/identity/services/BuiltinAdminIdentity.scala

- 16: isHandle: `Boolean`
- 19: account: `IdentityAccount`

### backend/src/main/scala/services/identity/services/IdentityService.scala

- 31: register: `Either[IdentityRegistrationError, IdentityAccount]`
- 32: issueSession: `Either[IdentitySessionError, IdentityAccount]`
- 33: current: `Either[IdentityCurrentSessionError, IdentityAccount]`
- 34: listActiveAccounts: `Vector[IdentityAccountSummary]`
- 56: register: `Either[IdentityRegistrationError, IdentityAccount]`
- 74: issueSession: `Either[IdentitySessionError, IdentityAccount]`
- 94: current: `Either[IdentityCurrentSessionError, IdentityAccount]`
- 105: listActiveAccounts: `Vector[IdentityAccountSummary]`
- 113: toSummary: `IdentityAccountSummary`
- 120: isPlayableStoredAccount: `Boolean`
- 123: authenticateStoredAccount: `Option[IdentityAccount]`
- 147: apply: `DefaultIdentityService`

### backend/src/main/scala/services/mail/services/MailService.scala

- 14: list: `Vector[MailRecord]`
- 15: markRead: `Either[MailReadError, MailRecord]`
- 19: list: `Vector[MailRecord]`
- 29: markRead: `Either[MailReadError, MailRecord]`
- 35: markExistingRead: `Either[MailReadError, MailRecord]`
- 38: welcomeMail: `MailRecord`
- 56: normalizedOwner: `Option[PlayerHandle]`
- 62: apply: `DefaultMailService`
- 67: apply: `DefaultMailService`

### backend/src/main/scala/services/replay/services/ReplayIdentifierPolicy.scala

- 8: isSafeReplayId: `Boolean`
- 11: isSafeIdentifier: `Boolean`

### backend/src/main/scala/services/replay/services/ReplayRecordFactory.scala

- 7: fromCommand: `ReplayRecord`
- 46: nonEmpty: `Option[String]`

### backend/src/main/scala/services/replay/services/ReplayService.scala

- 56: record: `Either[ReplayRecordError, ReplayRecord]`
- 57: list: `Vector[ReplayRecord]`
- 58: load: `Option[ReplayRecord]`
- 59: addComment: `Either[ReplayCommentError, ReplayCommentRecord]`
- 60: listComments: `Vector[ReplayCommentRecord]`
- 64: record: `Either[ReplayRecordError, ReplayRecord]`
- 76: list: `Vector[ReplayRecord]`
- 84: load: `Option[ReplayRecord]`
- 89: addComment: `Either[ReplayCommentError, ReplayCommentRecord]`
- 92: listComments: `Vector[ReplayCommentRecord]`
- 105: appendComment: `Either[ReplayCommentError, ReplayCommentRecord]`
- 127: isPlayable: `Boolean`
- 132: apply: `DefaultReplayService`
- 137: apply: `DefaultReplayService`

### backend/src/main/scala/services/social/services/FriendRequestMailFactory.scala

- 17: requestMail: `MailRecord`
- 38: responseMail: `MailRecord`
- 60: friendRequestMetadata: `FriendRequestMailMetadata`
- 71: mailStatusFor: `MailFriendRequestStatus`

### backend/src/main/scala/services/social/services/FriendRequestService.scala

- 32: friendRequest: `FriendRequestRecord`
- 38: notificationMail: `Option[MailRecord]`
- 49: friendRequest: `FriendRequestRecord`
- 55: notificationMail: `Option[MailRecord]`
- 63: create: `Either[FriendRequestCreateError, FriendRequestSubmissionResult]`
- 67: respond: `Either[FriendRequestRespondError, FriendRequestResponseResult]`
- 72: list: `Vector[FriendRequestRecord]`
- 73: find: `Option[FriendRequestRecord]`
- 82: create: `Either[FriendRequestCreateError, FriendRequestSubmissionResult]`
- 88: respond: `Either[FriendRequestRespondError, FriendRequestResponseResult]`
- 95: list: `Vector[FriendRequestRecord]`
- 103: find: `Option[FriendRequestRecord]`
- 106: createParsed: `Either[FriendRequestCreateError, FriendRequestSubmissionResult]`
- 130: respondParsed: `Either[FriendRequestRespondError, FriendRequestResponseResult]`
- 147: respondAsNormalizedActor: `Either[FriendRequestRespondError, FriendRequestResponseResult]`
- 164: normalizedHandle: `Option[PlayerHandle]`
- 170: apply: `DefaultFriendRequestService`
- 179: apply: `DefaultFriendRequestService`

### backend/src/main/scala/services/social/services/FriendRequestVisibilityRules.scala

- 8: canCreate: `Boolean`
- 11: isVisible: `Boolean`
- 14: isPlayable: `Boolean`

### backend/src/main/scala/system/services/HealthService.scala

- 8: current: `HealthResponse`
- 16: current: `HealthResponse`
- 26: apply: `StaticHealthService`

