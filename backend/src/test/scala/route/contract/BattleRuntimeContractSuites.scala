package route.contract

import java.lang.reflect.{InvocationHandler, Method as JavaMethod, Proxy}
import java.nio.file.{Files, Path, Paths}
import java.security.SecureRandom
import java.sql.Connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Header, Headers, HttpRoutes, Method, Request}
import org.typelevel.ci.CIString
import scala.jdk.CollectionConverters.*

import route.battle.BattleHttp4sRoutes
import route.bots.BotProfileHttp4sRoutes
import route.governance.GovernanceHttp4sRoutes
import route.health.{HealthHttp4sRoutes, HealthHttpModule}
import route.identity.IdentityHttp4sRoutes
import route.mail.MailHttp4sRoutes
import route.forum.ForumHttp4sRoutes
import route.replay.{ReplayHttp4sRoutes, ReplayHttpModule}
import route.social.SocialHttp4sRoutes
import services.{BackendRepositories, BackendRepositoryFactories}
import services.battle.database.results.{BattleResultRepository, FileBattleResultRepository, InMemoryBattleResultRepository}
import services.battle.objects.*
import services.bots.objects.*
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import services.forum.database.{FileForumRepository, InMemoryForumRepository}
import services.forum.objects.*
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import services.identity.api.IdentityAccountSummary
import services.identity.objects.{IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import services.identity.ports.{PasswordVerification, Pbkdf2PasswordHasher, Sha256PasswordHasher}
import services.mail.database.{FileMailRepository, InMemoryMailRepository, MailRepository}
import services.mail.objects.*
import services.replay.database.{FileReplayRepository, InMemoryReplayRepository, ReplayRepository}
import services.replay.objects.*
import services.social.database.{FileFriendRequestRepository, InMemoryFriendRequestRepository}
import services.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import services.battle.database.session.{
  BattleCommandOwnership,
  BattleCommandSubmitError,
  BattleSessionLookup,
  BattleSessionSeed,
  BattleRoomLifecycleSink,
  BattleStateReadError,
  BattleStateService,
  InMemoryBattleStateService
}
import services.battle.database.queue.{
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleQueueStatusError,
  BattleRoomError
}
import services.battle.database.projections.{
  BattleFinishProjectionFailureReporter,
  BattleMailPublisherPort,
  BattleReplayWriterPort,
  ConsoleBattleFinishProjectionFailureReporter,
  DefaultBattleFinishProjector
}
import services.battle.objects.result.{BattleFinishProjectionOutcome, BattleFinishProjector}
import services.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import services.mail.services.{MailReadError, MailService}
import services.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumCreateTopicError,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import services.governance.services.{
  ContributionAdjustmentCommand,
  ContributionAdjustmentService,
  ContributionAdjustmentSubmissionResult,
  GovernanceNotificationService,
  GovernanceReviewNotificationCommand,
  GovernanceReviewNotificationSubmissionResult
}
import services.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}
import services.bots.services.BotProfileService
import services.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}
import services.identity.objects.DisplayName
import system.objects.{HealthResponse, HealthStatus}
import system.objects.{ServiceName, ServicePort, UserId}
import system.database.PostgresSupport
import system.services.HealthService
import system.storage.*

private[contract] object BattleStateRuntimeContractTest:
  def run(): Unit =
    currentStateLazilyBootstrapsFromSessionLookup()
    projectileTerminalReasonWireValuesMatchLegacy()
    spawnPointsMatchFrontendBattleMap()
    acceptCommandEnforcesOwnershipAndBotBoundaries()
    acceptedCommandSequenceIsMonotonic()
    movementStopsAtArenaObstacle()
    walkingUsesFrontendBaseMoveSpeed()
    sprintConsumesAndRecoversStamina()
    pistolCooldownReloadAndPickupAreAuthoritative()
    pistolDamageWaitsForVisibleProjectileTravel()
    projectileObstacleTerminalUsesFirstIntersection()
    projectileLargeReadGapMatchesSteppedCollision()
    heldPrimaryContinuesPistolFireDuringRuntimeAdvance()
    fixedStepCatchUpAdvancesHeldFireAcrossLargeReadGap()
    projectilesDoNotExpireAtOldShortRange()
    projectileTerminalHistoryIsBounded()
    battleEventHistoryIsBounded()
    sameTickPickupContentionHasSingleWinner()
    medkitPickupRespawnsAfterTimer()
    medkitHealsDamagedPlayer()
    nonPistolWeaponsFireAuthoritatively()
    nonPistolActiveProjectilesDamageTargets()
    rocketSplashDamageReportsDirectTargetAndDamagesNearbyTargets()
    eliminationDoesNotRespawnAndFinishesBattle()
    eliminationClearsDeadPlayerRuntimeBeforeBattleFinish()
    skillCommandSuppressesPrimaryFire()
    noopSkillCommandSuppressesPrimaryFire()
    replayFramesCaptureRuntimeAndFinish()
    replayFrameHistoryIsBounded()
    botRuntimeControlMovesAimsAndFiresAfterOpeningDelay()
    emptyMagazineStartsAutomaticReload()
    finishedStateProjectsArtifactsOnce()
    finishedStateTracksPartialArtifactReadiness()
    throwingFinishProjectorDoesNotLeaveProjectionInProgress()
    ignoredFinishedCommandUsesStoredClientSequence()
    finishedStateMarksQueueRoomFinished()

  private val frontendSpawnPoints: Vector[BattleVector2] =
    Vector(
      BattleVector2(704.0, 800.0),
      BattleVector2(512.0, 544.0),
      BattleVector2(512.0, 1056.0),
      BattleVector2(1600.0, 320.0),
      BattleVector2(1600.0, 1280.0),
      BattleVector2(2048.0, 800.0)
    )

  private def currentStateLazilyBootstrapsFromSessionLookup(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    ContractAssertions.assertEquals(
      "runtime unknown battle",
      service.currentState(BattleId("missing")),
      Left(BattleStateReadError.BattleNotFound)
    )

    val state = battleState(service, "lazy bootstrap")
    assertLifecycleBooleanOptionInvariants("lazy bootstrap", state)
    ContractAssertions.assertEquals("runtime battle id", state.battleId, BattleId("battle-state-runtime"))
    ContractAssertions.assertEquals("runtime room id", state.roomId, RoomId("room-state-runtime"))
    ContractAssertions.assertEquals("runtime phase", state.phase, BattlePhase.Active)
    ContractAssertions.assertEquals("runtime artifact status", state.artifactStatus, BattleArtifactStatus.Pending)
    ContractAssertions.assertEquals("runtime player ids", state.players.map(_.playerId), Vector(PlayerId("alice"), PlayerId("bot-one")))
    ContractAssertions.assertEquals("runtime bot flags", state.players.map(_.isBot), Vector(false, true))
    ContractAssertions.assertEquals("runtime initial weapons", state.players.map(_.currentWeaponKind), Vector(WeaponKind.Pistol, WeaponKind.Pistol))

  private def projectileTerminalReasonWireValuesMatchLegacy(): Unit =
    ContractAssertions.assertEquals("runtime hit terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Hit), "hit")
    ContractAssertions.assertEquals("runtime blocked terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Blocked), "obstacle")
    ContractAssertions.assertEquals("runtime out-of-bounds terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.OutOfBounds), "world")
    ContractAssertions.assertEquals("runtime expired terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Expired), "ttl")

  private def spawnPointsMatchFrontendBattleMap(): Unit =
    val clock = TestClock(1_000L)
    val seats = frontendSpawnPoints.zipWithIndex.map { case (_, index) =>
      seat(
        playerId = PlayerId(s"player-$index"),
        heroId = HeroId(s"hero-$index"),
        handle = PlayerHandle(s"Player$index"),
        displayName = DisplayName(s"Player $index"),
        seat = SeatIndex(index),
        isBot = index != 0,
        spawnPointIndex = Some(SpawnPointIndex(index))
      )
    }
    val service = battleStateService(clock = clock, seed = sessionSeedWithSeats(seats))
    val state = battleState(service, "frontend spawn points")

    ContractAssertions.assertEquals("runtime frontend spawn positions", state.players.map(_.position), frontendSpawnPoints)

  private def acceptCommandEnforcesOwnershipAndBotBoundaries(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    ContractAssertions.assertEquals(
      "runtime wrong ticket",
      service.acceptCommand(command(PlayerId("alice"), TicketId("wrong-ticket"), 1L)),
      Left(BattleCommandSubmitError.CommandNotAuthorized)
    )
    ContractAssertions.assertEquals(
      "runtime bot command",
      service.acceptCommand(command(PlayerId("bot-one"), TicketId("ticket-bot"), 2L)),
      Left(BattleCommandSubmitError.BotCommandsNotSupported)
    )

    val accepted = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 3L))
      .fold(error => fail(s"valid command failed: $error"), identity)

    ContractAssertions.assertEquals("runtime accepted battle id", accepted.battleId, BattleId("battle-state-runtime"))
    ContractAssertions.assertEquals("runtime accepted seq", accepted.acceptedCommandSeq, ClientCommandSeq(3L))
    ContractAssertions.assertEquals("runtime accepted status", accepted.commandStatus, BattleCommandStatus.Applied)
    ContractAssertions.assertEquals("runtime accepted reason", accepted.commandReason, None)

    val alice = playerById(battleState(service, "accepted command state"), PlayerId("alice"))
    ContractAssertions.assertEquals("runtime state stores last command seq", alice.lastClientCommandSeq, ClientCommandSeq(3L))

  private def acceptedCommandSequenceIsMonotonic(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val first = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 10L))
      .fold(error => fail(s"first command failed: $error"), identity)
    ContractAssertions.assertEquals("runtime first accepted seq", first.acceptedCommandSeq, ClientCommandSeq(10L))

    val older = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L,
        movement = BattleCommandVector(-1.0, 0.0)
      )
    ).fold(error => fail(s"older command failed: $error"), identity)

    ContractAssertions.assertEquals("runtime older command still applies", older.commandStatus, BattleCommandStatus.Applied)
    ContractAssertions.assertEquals("runtime accepted seq does not move backwards", older.acceptedCommandSeq, ClientCommandSeq(10L))

    val alice = playerById(battleState(service, "monotonic command state"), PlayerId("alice"))
    ContractAssertions.assertEquals("runtime stored seq does not move backwards", alice.lastClientCommandSeq, ClientCommandSeq(10L))

  private def movementStopsAtArenaObstacle(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(0), battleMode = BattleMode.Autumn)
    )

    val initialAlice = playerById(battleState(service, "obstacle movement initial"), PlayerId("alice"))
    ContractAssertions.assertEquals("runtime fall west spawn", initialAlice.position, BattleVector2(700.0, 800.0))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 4L,
        movement = BattleCommandVector(0.0, 1.0)
      )
    ).fold(error => fail(s"movement command failed: $error"), identity)

    clock.now = 4_000L
    val movedAlice = playerById(battleState(service, "obstacle movement advanced"), PlayerId("alice"))

    ContractAssertions.assertEquals("runtime obstacle movement keeps x lane", movedAlice.position.x, 700.0)
    assert(
      movedAlice.position.y > initialAlice.position.y && movedAlice.position.y <= 1395.0,
      s"runtime expected alice to stop before fall tree trunk, got ${movedAlice.position}"
    )

  private def walkingUsesFrontendBaseMoveSpeed(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initialAlice = playerById(battleState(service, "walk initial"), PlayerId("alice"))
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 5L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"walk command failed: $error"), identity)

    clock.now = 2_000L
    val walkedAlice = playerById(battleState(service, "walk advanced"), PlayerId("alice"))

    assertClose("runtime walk distance", walkedAlice.position.x - initialAlice.position.x, 252.45, 0.1)
    ContractAssertions.assertEquals("runtime walk does not consume stamina", walkedAlice.stamina, Stamina(100))
    ContractAssertions.assertEquals("runtime walk is not sprint", walkedAlice.sprint, false)

  private def sprintConsumesAndRecoversStamina(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initialAlice = playerById(battleState(service, "sprint initial"), PlayerId("alice"))
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 6L,
        movement = BattleCommandVector(1.0, 0.0),
        sprint = true
      )
    ).fold(error => fail(s"sprint command failed: $error"), identity)

    clock.now = 2_000L
    val sprintedAlice = playerById(battleState(service, "sprint advanced"), PlayerId("alice"))

    ContractAssertions.assertEquals("runtime sprint remains effective while stamina exists", sprintedAlice.sprint, true)
    assertClose("runtime sprint drains precise stamina", sprintedAlice.stamina.value, 62.38, 0.001)
    assertClose("runtime sprint distance", sprintedAlice.position.x - initialAlice.position.x, 441.7875, 0.1)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L
      )
    ).fold(error => fail(s"idle command failed: $error"), identity)

    clock.now = 3_000L
    val recoveredAlice = playerById(battleState(service, "sprint recovered"), PlayerId("alice"))

    ContractAssertions.assertEquals("runtime idle clears effective sprint", recoveredAlice.sprint, false)
    assertClose("runtime idle recovers precise stamina", recoveredAlice.stamina.value, 86.14, 0.001)
    ContractAssertions.assertEquals("runtime idle keeps x position", recoveredAlice.position.x, sprintedAlice.position.x)

  private def pistolCooldownReloadAndPickupAreAuthoritative(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val pistolInitial = battleState(service, "pistol reload initial")
    val initialAlice = playerById(pistolInitial, PlayerId("alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L,
        primaryHeld = true
      )
    ).fold(error => fail(s"first shot failed: $error"), identity)

    val commandFrame = battleState(service, "after first shot command frame")
    val commandFrameAlice = playerById(commandFrame, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime first shot command records held primary", commandFrameAlice.primaryHeld, true)
    ContractAssertions.assertEquals("runtime first shot command does not consume ammo", commandFrameAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime first shot command does not create projectile", commandFrame.projectiles.exists(_.ownerHeroId == initialAlice.heroId), false)

    clock.now = 1_033L
    val afterFirstShotState = battleState(service, "after first shot runtime step")
    val afterFirstShotAlice = playerById(afterFirstShotState, PlayerId("alice"))
    val afterFirstShot = afterFirstShotAlice.weapons.head
    ContractAssertions.assertEquals("runtime first shot consumes ammo", afterFirstShot.ammoInMagazine, AmmoCount(11))
    ContractAssertions.assertEquals("runtime first shot sets cooldown", afterFirstShot.fireCooldownMs, CooldownMillis(260))
    assertClose("runtime pistol recoil moves shooter backward", initialAlice.position.x - afterFirstShotAlice.position.x, 3.6, 0.01)
    assertClose("runtime pistol recoil keeps lane", afterFirstShotAlice.position.y, initialAlice.position.y, 0.01)
    val firstProjectile = afterFirstShotState.projectiles.lastOption.getOrElse(fail("missing first projectile"))
    ContractAssertions.assertEquals("runtime pistol projectile damage", firstProjectile.damage, Damage(12))
    ContractAssertions.assertEquals("runtime pistol projectile radius", firstProjectile.radius, Radius(8.0))
    assertClose("runtime pistol projectile speed", vectorLengthForTest(firstProjectile.velocity), 1400.0, 0.1)
    assert(
      firstProjectile.ttlMs.value < 30000L && firstProjectile.ttlMs.value >= 29900L,
      s"runtime pistol projectile advances during birth tick, ttl=${firstProjectile.ttlMs}"
    )
    assertProjectileTravelAlignedWithVelocity("runtime pistol projectile", initialAlice.position, firstProjectile)
    assert(distanceBetweenForTest(initialAlice.position, firstProjectile.position) > 30.0, s"runtime pistol projectile advances from muzzle birth, got ${firstProjectile.position}")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 8L,
        primaryHeld = true
      )
    ).fold(error => fail(s"second shot during cooldown failed: $error"), identity)

    val duringCooldown = aliceWeapon(service, "during cooldown")
    ContractAssertions.assertEquals("runtime cooldown blocks immediate second shot", duringCooldown.ammoInMagazine, AmmoCount(11))
    ContractAssertions.assertEquals("runtime cooldown remains active", duringCooldown.fireCooldownMs, CooldownMillis(260))

    clock.now = 1_300L
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 9L,
        primaryHeld = true
      )
    ).fold(error => fail(s"shot after cooldown failed: $error"), identity)

    val afterCooldownShot = aliceWeapon(service, "after cooldown shot")
    ContractAssertions.assertEquals("runtime shot after cooldown consumes ammo", afterCooldownShot.ammoInMagazine, AmmoCount(10))
    assert(
      afterCooldownShot.fireCooldownMs.value > 0 && afterCooldownShot.fireCooldownMs.value <= 260,
      s"runtime shot after cooldown leaves active cooldown, got ${afterCooldownShot.fireCooldownMs}"
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 10L,
        reloadPressed = true
      )
    ).fold(error => fail(s"reload command failed: $error"), identity)

    val reloadCommandFrame = aliceWeapon(service, "reload command frame")
    ContractAssertions.assertEquals("runtime reload command records intent without immediate timer", reloadCommandFrame.reloadRemainingMs, CooldownMillis(0))

    clock.now = 1_333L
    val duringReload = aliceWeapon(service, "during reload")
    ContractAssertions.assertEquals("runtime reload starts", duringReload.reloadRemainingMs, CooldownMillis(1000))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 11L,
        primaryHeld = true
      )
    ).fold(error => fail(s"fire during reload failed: $error"), identity)

    val stillReloading = aliceWeapon(service, "still reloading")
    ContractAssertions.assertEquals("runtime reload blocks firing", stillReloading.ammoInMagazine, AmmoCount(10))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 12L,
        primaryHeld = false
      )
    ).fold(error => fail(s"release during reload failed: $error"), identity)

    clock.now = 2_500L
    val afterReload = aliceWeapon(service, "after reload")
    ContractAssertions.assertEquals("runtime reload fills magazine", afterReload.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime reload consumes reserve", afterReload.reserveAmmo, Some(AmmoCount(46)))
    ContractAssertions.assertEquals("runtime reload completes", afterReload.reloadRemainingMs, CooldownMillis(0))

  private def pistolDamageWaitsForVisibleProjectileTravel(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "pistol visible projectile initial")
    val alice = playerById(initial, PlayerId("alice"))
    val target = playerById(initial, PlayerId("bot-one"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 90L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"pistol visible projectile shot failed: $error"), identity)

    val commandFrame = battleState(service, "pistol visible projectile command frame")
    ContractAssertions.assertEquals(
      "runtime pistol command frame has no projectile",
      commandFrame.projectiles.exists(_.ownerHeroId == alice.heroId),
      false
    )

    clock.now = 1_033L
    val afterShot = battleState(service, "pistol visible projectile after shot")
    val afterShotTarget = playerById(afterShot, PlayerId("bot-one"))
    val projectile = afterShot.projectiles.lastOption.getOrElse(fail("missing visible pistol projectile"))

    ContractAssertions.assertEquals("runtime pistol first frame does not damage", afterShotTarget.hp, HitPoints(100))
    ContractAssertions.assertEquals("runtime pistol creates live projectile", projectile.projectileKind, ProjectileKind.PistolBullet)
    ContractAssertions.assertEquals("runtime pistol has no terminal in birth frame", afterShot.projectileTerminals.exists(_.projectileId == projectile.projectileId), false)
    assertProjectileTravelAlignedWithVelocity("runtime visible pistol projectile", alice.position, projectile)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 91L,
        primaryHeld = false
      )
    ).fold(error => fail(s"pistol visible projectile release failed: $error"), identity)

    clock.now = 1_300L
    val afterTravel = battleState(service, "pistol visible projectile after travel")
    val damagedTarget = playerById(afterTravel, PlayerId("bot-one"))
    val terminal = afterTravel.projectileTerminals.lastOption.getOrElse(fail("missing pistol hit terminal after travel"))

    ContractAssertions.assertEquals("runtime pistol projectile damages after travel", damagedTarget.hp, HitPoints(88))
    ContractAssertions.assertEquals("runtime pistol terminal kind", terminal.projectileKind, ProjectileKind.PistolBullet)
    ContractAssertions.assertEquals("runtime pistol terminal reason", terminal.reason, ProjectileTerminalReason.Hit)
    ContractAssertions.assertEquals("runtime pistol terminal target", terminal.targetPlayerId, Some(PlayerId("bot-one")))
    assertClose("runtime pistol contact point", distanceBetweenForTest(terminal.terminalPosition, damagedTarget.position), 32.0, 0.5)
    assert(
      distanceBetweenForTest(terminal.start, terminal.terminalPosition) < distanceBetweenForTest(terminal.start, damagedTarget.position),
      s"runtime pistol terminal should stop at target contact, terminal=${terminal.terminalPosition}, target=${damagedTarget.position}"
    )
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"runtime pistol hit terminal should preserve full segment end beyond hit point, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )

  private def projectileObstacleTerminalUsesFirstIntersection(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(2),
        botSpawnPointIndex = SpawnPointIndex(4),
        secondIsBot = false,
        battleMode = BattleMode.Autumn
      )
    )
    val initial = battleState(service, "exact projectile block initial")
    val alice = playerById(initial, PlayerId("alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 198L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"exact block shot failed: $error"), identity)

    clock.now = 1_033L
    battleState(service, "exact projectile first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 199L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = false
      )
    ).fold(error => fail(s"exact block release failed: $error"), identity)

    clock.now = 1_700L
    val blocked = battleState(service, "exact projectile block terminal")
    val terminal = blocked.projectileTerminals
      .find(terminal => terminal.ownerHeroId == alice.heroId && terminal.projectileKind == ProjectileKind.PistolBullet)
      .getOrElse(fail("missing exact blocked pistol terminal"))

    ContractAssertions.assertEquals("runtime exact projectile block reason", terminal.reason, ProjectileTerminalReason.Blocked)
    assertClose("runtime exact projectile block terminal x", terminal.terminalPosition.x, 2488.0, 0.001)
    assertClose("runtime exact projectile block terminal y", terminal.terminalPosition.y, 330.0, 0.001)
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"runtime blocked terminal should preserve full segment end beyond blocker, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )

  private def projectileLargeReadGapMatchesSteppedCollision(): Unit =
    val largeGap = pistolHitOutcomeAfterReads(Vector(1_300L), "projectile large read gap")
    val stepped = pistolHitOutcomeAfterReads(
      Vector(1_066L, 1_099L, 1_132L, 1_165L, 1_198L, 1_231L, 1_264L, 1_297L, 1_300L),
      "projectile stepped reads"
    )

    ContractAssertions.assertEquals("runtime large-gap projectile target hp", largeGap.targetHp, HitPoints(88))
    ContractAssertions.assertEquals("runtime stepped projectile target hp", stepped.targetHp, HitPoints(88))
    ContractAssertions.assertEquals("runtime large-gap projectile terminal reason", largeGap.terminal.reason, ProjectileTerminalReason.Hit)
    ContractAssertions.assertEquals("runtime stepped projectile terminal reason", stepped.terminal.reason, ProjectileTerminalReason.Hit)
    ContractAssertions.assertEquals("runtime large-gap projectile terminal target", largeGap.terminal.targetPlayerId, Some(PlayerId("bot-one")))
    ContractAssertions.assertEquals("runtime stepped projectile terminal target", stepped.terminal.targetPlayerId, Some(PlayerId("bot-one")))
    ContractAssertions.assertEquals("runtime large-gap consumed hit projectile", largeGap.liveOwnerProjectiles, 0)
    ContractAssertions.assertEquals("runtime stepped consumed hit projectile", stepped.liveOwnerProjectiles, 0)
    assertClose("runtime large-gap terminal x matches stepped", largeGap.terminal.terminalPosition.x, stepped.terminal.terminalPosition.x, 0.001)
    assertClose("runtime large-gap terminal y matches stepped", largeGap.terminal.terminalPosition.y, stepped.terminal.terminalPosition.y, 0.001)
    assert(
      largeGap.terminal.end.x > largeGap.terminal.terminalPosition.x,
      s"runtime large-gap terminal should preserve segment end beyond hit, terminal=${largeGap.terminal.terminalPosition}, end=${largeGap.terminal.end}"
    )
    assert(
      stepped.terminal.end.x > stepped.terminal.terminalPosition.x,
      s"runtime stepped terminal should preserve segment end beyond hit, terminal=${stepped.terminal.terminalPosition}, end=${stepped.terminal.end}"
    )

  private def heldPrimaryContinuesPistolFireDuringRuntimeAdvance(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)
    battleState(service, "held pistol initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 95L,
        primaryHeld = true
      )
    ).fold(error => fail(s"held pistol first command failed: $error"), identity)

    val afterCommand = battleState(service, "held pistol command frame")
    val afterCommandAlice = playerById(afterCommand, PlayerId("alice"))
    val afterCommandWeapon = afterCommandAlice.weapons.head
    ContractAssertions.assertEquals("runtime held pistol command keeps primary held", afterCommandAlice.primaryHeld, true)
    ContractAssertions.assertEquals("runtime held pistol command does not fire before runtime step", afterCommandWeapon.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime held pistol command creates no projectile", afterCommand.projectiles.count(_.projectileKind == ProjectileKind.PistolBullet), 0)

    clock.now = 1_300L
    val afterFirstCooldown = battleState(service, "held pistol after first cooldown")
    val afterFirstCooldownAlice = playerById(afterFirstCooldown, PlayerId("alice"))
    val afterFirstCooldownWeapon = afterFirstCooldownAlice.weapons.head
    assert(
      afterFirstCooldownWeapon.ammoInMagazine == AmmoCount(10),
      s"runtime held pistol should fire second shot: ammo=${afterFirstCooldownWeapon.ammoInMagazine}, cooldown=${afterFirstCooldownWeapon.fireCooldownMs}, primaryHeld=${afterFirstCooldownAlice.primaryHeld}, tick=${afterFirstCooldown.tick}, elapsed=${afterFirstCooldown.elapsedMs}"
    )
    assert(
      afterFirstCooldown.projectiles.count(_.projectileKind == ProjectileKind.PistolBullet) >= 2,
      s"runtime expected at least two live/visible pistol projectiles, got ${afterFirstCooldown.projectiles.map(_.projectileId)}"
    )

    clock.now = 1_600L
    val afterSecondCooldown = battleState(service, "held pistol after second cooldown")
    val afterSecondCooldownWeapon = playerById(afterSecondCooldown, PlayerId("alice")).weapons.head
    ContractAssertions.assertEquals("runtime held pistol fires third shot", afterSecondCooldownWeapon.ammoInMagazine, AmmoCount(9))

  private def fixedStepCatchUpAdvancesHeldFireAcrossLargeReadGap(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(8),
        secondIsBot = false
      )
    )
    battleState(service, "fixed-step held fire initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 99L,
        primaryHeld = true
      )
    ).fold(error => fail(s"fixed-step held pistol command failed: $error"), identity)

    clock.now = 2_000L
    val afterGap = battleState(service, "fixed-step held fire after one second gap")
    val alice = playerById(afterGap, PlayerId("alice"))
    val pistol = alice.weapons.head
    val alicePistolProjectiles =
      afterGap.projectiles.count(projectile => projectile.ownerHeroId == alice.heroId && projectile.projectileKind == ProjectileKind.PistolBullet) +
        afterGap.projectileTerminals.count(terminal => terminal.ownerHeroId == alice.heroId && terminal.projectileKind == ProjectileKind.PistolBullet)

    assert(
      pistol.ammoInMagazine.value <= 8,
      s"runtime expected fixed-step catch-up to fire across multiple pistol cooldowns, ammo=${pistol.ammoInMagazine}"
    )
    assert(
      alicePistolProjectiles >= 4,
      s"runtime expected at least four pistol projectiles/terminals after fixed-step catch-up, got $alicePistolProjectiles"
    )
    assert(afterGap.tick.value >= 30L, s"runtime expected battle tick to reach one second, got ${afterGap.tick}")

  private def projectilesDoNotExpireAtOldShortRange(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(2)
      )
    )
    battleState(service, "long range projectile initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 96L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"long range pistol shot failed: $error"), identity)

    clock.now = 1_033L
    battleState(service, "long range projectile first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 97L,
        primaryHeld = false
      )
    ).fold(error => fail(s"long range pistol release failed: $error"), identity)

    clock.now = 2_000L
    val afterOldShortRange = battleState(service, "long range projectile after old ttl")
    val ownerProjectiles = afterOldShortRange.projectiles.filter(projectile =>
      projectile.ownerHeroId == HeroId("hero-alice") && projectile.projectileKind == ProjectileKind.PistolBullet
    )
    ContractAssertions.assertEquals("runtime projectile remains live past old short ttl", ownerProjectiles.length, 1)
    ContractAssertions.assertEquals(
      "runtime projectile did not emit expired terminal",
      afterOldShortRange.projectileTerminals.exists(_.reason == ProjectileTerminalReason.Expired),
      false
    )
    assert(afterOldShortRange.projectileTerminals.forall(_.targetPlayerId.isEmpty), "runtime long range projectile should not hit the off-lane bot")

  private def projectileTerminalHistoryIsBounded(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(4),
        botSpawnPointIndex = SpawnPointIndex(1),
        secondIsBot = false
      )
    )
    battleState(service, "terminal retention initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 130L,
        movement = BattleCommandVector(-1.0, 0.0)
      )
    ).fold(error => fail(s"shotgun pickup movement failed: $error"), identity)

    clock.now = 1_300L
    val pickedUp = battleState(service, "terminal retention shotgun pickup")
    val alice = playerById(pickedUp, PlayerId("alice"))
    val shotgunIndex = alice.weapons.indexWhere(_.weaponKind == WeaponKind.Shotgun)
    assert(shotgunIndex >= 0, "runtime shotgun pickup should add Shotgun")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 131L,
        switchWeaponIndex = Some(shotgunIndex)
      )
    ).fold(error => fail(s"terminal retention switch to shotgun failed: $error"), identity)

    var firstTerminalProjectileIds = Set.empty[ProjectileId]
    (0 until 14).foreach { shot =>
      val fireSeq = 140L + shot.toLong * 2L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = fireSeq,
          aim = BattleCommandVector(1.0, 0.0),
          primaryHeld = true
        )
      ).fold(error => fail(s"terminal retention shotgun shot $shot failed: $error"), identity)

      clock.now = clock.now + 33L
      battleState(service, s"terminal retention shotgun shot $shot runtime step")
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = fireSeq + 1L,
          aim = BattleCommandVector(1.0, 0.0),
          primaryHeld = false
        )
      ).fold(error => fail(s"terminal retention shotgun release $shot failed: $error"), identity)

      clock.now = clock.now + 1_367L
      val advanced = battleState(service, s"terminal retention after shot $shot")
      if shot == 0 then
        firstTerminalProjectileIds = advanced.projectileTerminals.map(_.projectileId).toSet
        ContractAssertions.assertEquals("runtime first shotgun shot emits five terminals", firstTerminalProjectileIds.size, 5)
    }

    val retained = battleState(service, "terminal retention final")
    ContractAssertions.assertEquals("runtime projectile terminals are capped", retained.projectileTerminals.length, 64)
    ContractAssertions.assertEquals(
      "runtime oldest shotgun terminals are pruned",
      retained.projectileTerminals.exists(terminal => firstTerminalProjectileIds.contains(terminal.projectileId)),
      false
    )

  private def battleEventHistoryIsBounded(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(240_000L),
      seed = sessionSeed(secondIsBot = false)
    )
    battleState(service, "event retention initial")

    var firstEventId: Option[BattleEventId] = None
    (0 until 14).foreach { index =>
      clock.now = 1_100L + index.toLong * 10_050L
      val state = battleState(service, s"event retention pickup $index")
      val latestEvent = state.events.lastOption.getOrElse(fail(s"missing pickup event $index"))
      ContractAssertions.assertEquals(s"runtime event retention kind $index", latestEvent.eventKind, BattleEventKind.Pickup)
      if index == 0 then firstEventId = Some(latestEvent.eventId)
    }

    val retained = battleState(service, "event retention final")
    ContractAssertions.assertEquals("runtime battle events are capped", retained.events.length, 12)
    ContractAssertions.assertEquals(
      "runtime oldest battle event is pruned",
      retained.events.exists(event => firstEventId.contains(event.eventId)),
      false
    )

  private def sameTickPickupContentionHasSingleWinner(): Unit =
    val clock = TestClock(1_100L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(0),
        secondIsBot = false
      )
    )

    val contested = battleState(service, "same tick pickup contention")
    assertLifecycleBooleanOptionInvariants("same tick pickup contention", contested)
    val alice = playerById(contested, PlayerId("alice"))
    val other = playerById(contested, PlayerId("bot-one"))
    val gatlingPickup = contested.pickups.find(_.pickupId == PickupId("pickup-gatling-1")).getOrElse(fail("missing contested gatling pickup"))
    val pickupEvents = contested.events.filter(event => event.eventKind == BattleEventKind.Pickup && event.eventId.value.contains("pickup-gatling-1"))

    ContractAssertions.assertEquals("runtime contention gives first player gatling", alice.weapons.exists(_.weaponKind == WeaponKind.Gatling), true)
    ContractAssertions.assertEquals("runtime contention does not duplicate gatling", other.weapons.exists(_.weaponKind == WeaponKind.Gatling), false)
    ContractAssertions.assertEquals("runtime contention has exactly one weapon winner", contested.players.count(_.weapons.exists(_.weaponKind == WeaponKind.Gatling)), 1)
    ContractAssertions.assertEquals("runtime contention consumes pickup once", gatlingPickup.available, false)
    assert(gatlingPickup.respawnMs.value > 0L, s"runtime contention pickup should have respawn timer, got ${gatlingPickup.respawnMs}")
    ContractAssertions.assertEquals("runtime contention emits one pickup event", pickupEvents.length, 1)
    ContractAssertions.assertEquals("runtime contention event source is winner", pickupEvents.head.source.playerId, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime contention event target is winner", pickupEvents.head.target.playerId, PlayerId("alice"))

  private def medkitPickupRespawnsAfterTimer(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(2),
        secondIsBot = false
      )
    )

    battleState(service, "medkit pickup initial")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 13L,
        movement = BattleCommandVector(-1.0, 0.0)
      )
    ).fold(error => fail(s"medkit pickup movement failed: $error"), identity)

    clock.now = 2_000L
    val pickedUp = battleState(service, "medkit picked up")
    assertLifecycleBooleanOptionInvariants("after medkit pickup", pickedUp)
    val medkit = pickedUp.pickups.find(_.pickupId == PickupId("pickup-medkit-2")).getOrElse(fail("missing medkit pickup"))
    val healEvent = pickedUp.events.lastOption.getOrElse(fail("missing medkit pickup event"))

    ContractAssertions.assertEquals("runtime medkit consumed", medkit.available, false)
    assert(
      medkit.respawnMs.value > 0L && medkit.respawnMs.value <= 10000L,
      s"runtime medkit respawn timer should be active and no greater than full duration, got ${medkit.respawnMs}"
    )
    ContractAssertions.assertEquals("runtime medkit event kind", healEvent.eventKind, BattleEventKind.Heal)
    ContractAssertions.assertEquals("runtime medkit event target", healEvent.target.playerId, PlayerId("alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 14L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"medkit move-away command failed: $error"), identity)

    clock.now = 3_000L
    battleState(service, "medkit moved away")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 15L,
        movement = BattleCommandVector(0.0, 0.0)
      )
    ).fold(error => fail(s"medkit stop command failed: $error"), identity)

    clock.now = 13_500L
    val respawned = battleState(service, "medkit respawned")
    val respawnedMedkit = respawned.pickups.find(_.pickupId == PickupId("pickup-medkit-2")).getOrElse(fail("missing respawned medkit"))

    ContractAssertions.assertEquals("runtime medkit becomes available after respawn", respawnedMedkit.available, true)
    ContractAssertions.assertEquals("runtime medkit clears respawn timer", respawnedMedkit.respawnMs, DurationMillis(0L))

  private def medkitHealsDamagedPlayer(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "medkit heal initial")
    val alice = playerById(initial, PlayerId("alice"))
    val target = playerById(initial, PlayerId("bot-one"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 19L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"medkit heal damage shot failed: $error"), identity)

    clock.now = 1_033L
    battleState(service, "medkit heal shot spawned")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 20L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"medkit heal damage release failed: $error"), identity)

    clock.now = 1_400L
    val damaged = battleState(service, "medkit heal damaged")
    val damagedTarget = playerById(damaged, PlayerId("bot-one"))
    ContractAssertions.assertEquals("runtime medkit heal setup damages target", damagedTarget.hp, HitPoints(88))

    val medkitContactPoints = Vector(
      BattleVector2(900.0, 900.0),
      BattleVector2(1120.0, 960.0)
    )
    var moveSeq = 21L
    var healed = battleState(service, "medkit heal before pickup movement")
    medkitContactPoints.foreach { contactPoint =>
      var step = 0
      while step < 42 && healed.pickups.exists(pickup => pickup.pickupId == PickupId("pickup-medkit-1") && pickup.available) do
        val movingTarget = playerById(healed, PlayerId("bot-one"))
        service.acceptCommand(
          command(
            playerId = PlayerId("bot-one"),
            ticketId = TicketId("ticket-bot-one"),
            seq = moveSeq,
            movement = BattleCommandVector(contactPoint.x - movingTarget.position.x, contactPoint.y - movingTarget.position.y)
          )
        ).fold(error => fail(s"medkit heal pickup movement failed: $error"), identity)
        moveSeq += 1L
        clock.now += 120L
        healed = battleState(service, s"medkit heal pickup movement $moveSeq")
        step += 1
    }
    val healedTarget = playerById(healed, PlayerId("bot-one"))
    val medkitPickup = healed.pickups.find(_.pickupId == PickupId("pickup-medkit-1")).getOrElse(fail("missing medkit heal pickup"))

    ContractAssertions.assertEquals("runtime medkit heals and clamps to max hp", healedTarget.hp, HitPoints(100))
    ContractAssertions.assertEquals("runtime medkit heal consumes pickup", medkitPickup.available, false)
    ContractAssertions.assertEquals("runtime medkit heal event kind", healed.events.lastOption.map(_.eventKind), Some(BattleEventKind.Heal))

  private def nonPistolWeaponsFireAuthoritatively(): Unit =
    val gatlingClock = TestClock(1_100L)
    val gatlingService = battleStateService(clock = gatlingClock)
    val gatlingInitial = battleState(gatlingService, "gatling initial pickup")
    val gatlingInitialAlice = playerById(gatlingInitial, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime spawn pickup adds gatling", gatlingInitialAlice.weapons.exists(_.weaponKind == WeaponKind.Gatling), true)

    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 21L,
        switchWeaponIndex = Some(1)
      )
    ).fold(error => fail(s"switch to gatling failed: $error"), identity)
    val gatlingReady = battleState(gatlingService, "gatling ready before fire")
    val gatlingReadyAlice = playerById(gatlingReady, PlayerId("alice"))
    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 22L,
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling fire failed: $error"), identity)

    gatlingClock.now = 1_133L
    val afterGatlingFire = battleState(gatlingService, "after gatling fire")
    val gatlingAlice = playerById(afterGatlingFire, PlayerId("alice"))
    val gatlingWeapon = gatlingAlice.weapons(gatlingAlice.currentWeaponIndex)
    val gatlingProjectile = afterGatlingFire.projectiles.lastOption.getOrElse(fail("missing gatling projectile"))
    ContractAssertions.assertEquals("runtime gatling current kind", gatlingAlice.currentWeaponKind, WeaponKind.Gatling)
    ContractAssertions.assertEquals("runtime gatling adds heat", gatlingWeapon.heat, BattleWeaponHeat(8))
    ContractAssertions.assertEquals("runtime gatling reserve is zero", gatlingWeapon.reserveAmmo, Some(AmmoCount(0)))
    ContractAssertions.assertEquals("runtime gatling cooldown", gatlingWeapon.fireCooldownMs, CooldownMillis(72))
    ContractAssertions.assertEquals("runtime gatling projectile kind", gatlingProjectile.projectileKind, ProjectileKind.GatlingBullet)
    ContractAssertions.assertEquals("runtime gatling projectile damage", gatlingProjectile.damage, Damage(5))
    ContractAssertions.assertEquals("runtime gatling projectile radius", gatlingProjectile.radius, Radius(7.0))
    assert(
      gatlingProjectile.ttlMs.value < 30000L && gatlingProjectile.ttlMs.value >= 29900L,
      s"runtime gatling projectile should retain long authoritative lifetime after birth tick, ttl=${gatlingProjectile.ttlMs}"
    )
    assertClose("runtime gatling single projectile has no spread x", gatlingProjectile.velocity.x, 980.0, 0.001)
    assertClose("runtime gatling single projectile has no spread y", gatlingProjectile.velocity.y, 0.0, 0.001)
    assertProjectileTravelAlignedWithVelocity("runtime gatling projectile", gatlingReadyAlice.position, gatlingProjectile)
    assertClose("runtime gatling recoil moves shooter backward", gatlingReadyAlice.position.x - gatlingAlice.position.x, 1.44, 0.01)
    assertClose("runtime gatling recoil keeps lane", gatlingAlice.position.y, gatlingReadyAlice.position.y, 0.01)
    assert(distanceBetweenForTest(gatlingAlice.position, gatlingProjectile.position) > 29.0, s"runtime gatling projectile advances from muzzle birth, got ${gatlingProjectile.position}")

    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 23L,
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling cooldown fire failed: $error"), identity)
    ContractAssertions.assertEquals("runtime gatling cooldown blocks second projectile", battleState(gatlingService, "gatling cooldown").projectiles.length, 1)

    gatlingClock.now = 1_400L
    val afterGatlingHeld = battleState(gatlingService, "gatling held runtime fire")
    val gatlingHeldAlice = playerById(afterGatlingHeld, PlayerId("alice"))
    val gatlingHeldWeapon = gatlingHeldAlice.weapons(gatlingHeldAlice.currentWeaponIndex)
    val gatlingHeldProjectiles = afterGatlingHeld.projectiles.filter(_.projectileKind == ProjectileKind.GatlingBullet)
    assert(gatlingHeldProjectiles.length >= 2, s"runtime expected held Gatling to create more projectiles, got ${gatlingHeldProjectiles.length}")
    ContractAssertions.assertEquals("runtime gatling held projectile ids are unique", gatlingHeldProjectiles.map(_.projectileId).distinct.length, gatlingHeldProjectiles.length)
    assert(gatlingHeldWeapon.heat.value >= 8, s"runtime expected held Gatling heat to include runtime shot, got ${gatlingHeldWeapon.heat}")

    val holsteredHeatBefore = gatlingHeldWeapon.heat
    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 24L,
        primaryHeld = false,
        switchWeaponIndex = Some(0)
      )
    ).fold(error => fail(s"switch away from gatling failed: $error"), identity)

    gatlingClock.now = 3_400L
    val afterHolsteredCooldown = battleState(gatlingService, "gatling holstered heat cooldown")
    val holsteredAlice = playerById(afterHolsteredCooldown, PlayerId("alice"))
    val holsteredGatling = holsteredAlice.weapons.find(_.weaponKind == WeaponKind.Gatling).getOrElse(fail("missing holstered Gatling"))
    ContractAssertions.assertEquals("runtime gatling switched back to pistol", holsteredAlice.currentWeaponKind, WeaponKind.Pistol)
    assert(
      holsteredGatling.heat.value < holsteredHeatBefore.value,
      s"runtime expected holstered Gatling heat to cool, before=$holsteredHeatBefore, after=${holsteredGatling.heat}"
    )
    ContractAssertions.assertEquals("runtime holstered Gatling cools to zero", holsteredGatling.heat, BattleWeaponHeat(0))

    val rocketClock = TestClock(1_000L)
    val rocketService = battleStateService(
      clock = rocketClock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(3),
        secondIsBot = false
      )
    )
    battleState(rocketService, "rocket initial")
    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 24L,
        movement = BattleCommandVector(-5.0, -1.0)
      )
    ).fold(error => fail(s"rocket pickup move failed: $error"), identity)
    rocketClock.now = 2_300L
    val rocketPickedUp = battleState(rocketService, "rocket picked up")
    val rocketAlice = playerById(rocketPickedUp, PlayerId("alice"))
    val rocketIndex = rocketAlice.weapons.indexWhere(_.weaponKind == WeaponKind.RocketLauncher)
    assert(rocketIndex >= 0, "runtime rocket pickup should add RocketLauncher")

    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 25L,
        switchWeaponIndex = Some(rocketIndex)
      )
    ).fold(error => fail(s"switch to rocket failed: $error"), identity)
    val rocketReady = battleState(rocketService, "rocket ready before fire")
    val rocketReadyAlice = playerById(rocketReady, PlayerId("alice"))
    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 26L,
        primaryHeld = true
      )
    ).fold(error => fail(s"rocket fire failed: $error"), identity)

    rocketClock.now = 2_333L
    val afterRocketFire = battleState(rocketService, "after rocket fire")
    val rocketFireAlice = playerById(afterRocketFire, PlayerId("alice"))
    val rocketWeapon = rocketFireAlice.weapons(rocketFireAlice.currentWeaponIndex)
    val rocketProjectile = afterRocketFire.projectiles.lastOption.getOrElse(fail("missing rocket projectile"))
    ContractAssertions.assertEquals("runtime rocket consumes shell", rocketWeapon.ammoInMagazine, AmmoCount(0))
    ContractAssertions.assertEquals("runtime rocket keeps reserve before reload", rocketWeapon.reserveAmmo, Some(AmmoCount(3)))
    ContractAssertions.assertEquals("runtime rocket cooldown", rocketWeapon.fireCooldownMs, CooldownMillis(160))
    ContractAssertions.assertEquals("runtime rocket auto reload starts", rocketWeapon.reloadRemainingMs, CooldownMillis(2500))
    ContractAssertions.assertEquals("runtime rocket projectile kind", rocketProjectile.projectileKind, ProjectileKind.Rocket)
    ContractAssertions.assertEquals("runtime rocket projectile damage", rocketProjectile.damage, Damage(60))
    ContractAssertions.assertEquals("runtime rocket projectile radius", rocketProjectile.radius, Radius(14.0))
    ContractAssertions.assertEquals("runtime rocket splash radius", rocketProjectile.splashRadius, Radius(132.0))
    assertClose("runtime rocket projectile speed", vectorLengthForTest(rocketProjectile.velocity), 340.0, 0.001)
    assert(
      rocketProjectile.ttlMs.value < 30000L && rocketProjectile.ttlMs.value >= 29900L,
      s"runtime rocket projectile should retain long authoritative lifetime after birth tick, ttl=${rocketProjectile.ttlMs}"
    )
    assertProjectileTravelAlignedWithVelocity("runtime rocket projectile", rocketReadyAlice.position, rocketProjectile)
    assertClose("runtime rocket recoil moves shooter backward", rocketReadyAlice.position.x - rocketFireAlice.position.x, 21.6, 0.01)
    assertClose("runtime rocket recoil keeps lane", rocketFireAlice.position.y, rocketReadyAlice.position.y, 0.01)
    assert(distanceBetweenForTest(rocketFireAlice.position, rocketProjectile.position) > 36.0, s"runtime rocket projectile advances from muzzle birth, got ${rocketProjectile.position}")

    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 27L,
        reloadPressed = true
      )
    ).fold(error => fail(s"rocket reload failed: $error"), identity)
    val rocketReloading = battleState(rocketService, "rocket reload state")
    val rocketReloadingAlice = playerById(rocketReloading, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime rocket reload remains active", rocketReloadingAlice.weapons(rocketReloadingAlice.currentWeaponIndex).reloadRemainingMs, CooldownMillis(2500))
    rocketClock.now = 4_900L
    val rocketReloaded = battleState(rocketService, "rocket reloaded")
    val rocketReloadedAlice = playerById(rocketReloaded, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime rocket reload fills magazine", rocketReloadedAlice.weapons(rocketReloadedAlice.currentWeaponIndex).ammoInMagazine, AmmoCount(1))
    ContractAssertions.assertEquals("runtime rocket reload consumes reserve", rocketReloadedAlice.weapons(rocketReloadedAlice.currentWeaponIndex).reserveAmmo, Some(AmmoCount(2)))

    val shotgunClock = TestClock(1_000L)
    val shotgunService = battleStateService(
      clock = shotgunClock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(4))
    )
    battleState(shotgunService, "shotgun initial")
    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 28L,
        movement = BattleCommandVector(-1.0, 0.0)
      )
    ).fold(error => fail(s"shotgun pickup move failed: $error"), identity)
    shotgunClock.now = 1_300L
    val shotgunPickedUp = battleState(shotgunService, "shotgun picked up")
    val shotgunAlice = playerById(shotgunPickedUp, PlayerId("alice"))
    val shotgunIndex = shotgunAlice.weapons.indexWhere(_.weaponKind == WeaponKind.Shotgun)
    assert(shotgunIndex >= 0, "runtime shotgun pickup should add Shotgun")

    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 29L,
        switchWeaponIndex = Some(shotgunIndex)
      )
    ).fold(error => fail(s"switch to shotgun failed: $error"), identity)
    val shotgunReady = battleState(shotgunService, "shotgun ready before fire")
    val shotgunReadyAlice = playerById(shotgunReady, PlayerId("alice"))
    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 30L,
        primaryHeld = true
      )
    ).fold(error => fail(s"shotgun fire failed: $error"), identity)

    shotgunClock.now = 1_333L
    val afterShotgunFire = battleState(shotgunService, "after shotgun fire")
    val shotgunFireAlice = playerById(afterShotgunFire, PlayerId("alice"))
    val shotgunWeapon = shotgunFireAlice.weapons(shotgunFireAlice.currentWeaponIndex)
    val shotgunProjectiles = afterShotgunFire.projectiles.filter(_.projectileKind == ProjectileKind.ShotgunPellet)
    ContractAssertions.assertEquals("runtime shotgun consumes shell", shotgunWeapon.ammoInMagazine, AmmoCount(5))
    ContractAssertions.assertEquals("runtime shotgun cooldown", shotgunWeapon.fireCooldownMs, CooldownMillis(760))
    ContractAssertions.assertEquals("runtime shotgun pellet count", shotgunProjectiles.length, 5)
    ContractAssertions.assertEquals("runtime shotgun pellet damage", shotgunProjectiles.head.damage, Damage(8))
    assert(shotgunProjectiles.forall(_.radius == Radius(7.0)), s"runtime shotgun pellet radii should match content, got ${shotgunProjectiles.map(_.radius)}")
    shotgunProjectiles.foreach { projectile =>
      assertClose("runtime shotgun pellet speed", vectorLengthForTest(projectile.velocity), 720.0, 0.001)
      assertProjectileTravelAlignedWithVelocity("runtime shotgun pellet", shotgunReadyAlice.position, projectile)
    }
    assertClose("runtime shotgun recoil moves shooter backward", shotgunReadyAlice.position.x - shotgunFireAlice.position.x, 14.4, 0.01)
    assertClose("runtime shotgun recoil keeps lane", shotgunFireAlice.position.y, shotgunReadyAlice.position.y, 0.01)
    assert(
      shotgunProjectiles.forall(projectile => projectile.ttlMs.value < 30000L && projectile.ttlMs.value >= 29900L),
      s"runtime shotgun pellets advance during birth tick, ttls=${shotgunProjectiles.map(_.ttlMs)}"
    )
    shotgunProjectiles.zipWithIndex.foreach { case (projectile, index) =>
      assert(distanceBetweenForTest(shotgunFireAlice.position, projectile.position) > 29.0, s"runtime shotgun pellet $index advances from muzzle birth, got ${projectile.position}")
    }

  private def nonPistolActiveProjectilesDamageTargets(): Unit =
    val clock = TestClock(1_100L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(8),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "gatling damage initial")
    val alice = playerById(initial, PlayerId("alice"))
    val bot = playerById(initial, PlayerId("bot-one"))
    val gatlingIndex = alice.weapons.indexWhere(_.weaponKind == WeaponKind.Gatling)
    assert(gatlingIndex >= 0, "runtime spawn pickup should add Gatling for damage test")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 31L,
        switchWeaponIndex = Some(gatlingIndex)
      )
    ).fold(error => fail(s"switch to gatling for damage failed: $error"), identity)
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 32L,
        aim = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling damage fire failed: $error"), identity)

    clock.now = 1_133L
    battleState(service, "gatling damage first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 33L,
        aim = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"gatling damage release failed: $error"), identity)

    clock.now = 1_400L
    val afterHit = battleState(service, "gatling projectile hit")
    val damagedBot = playerById(afterHit, PlayerId("bot-one"))
    val terminal = afterHit.projectileTerminals.lastOption.getOrElse(fail("missing gatling hit terminal"))

    ContractAssertions.assertEquals("runtime gatling projectile damages target", damagedBot.hp, HitPoints(95))
    ContractAssertions.assertEquals("runtime gatling projectile terminal kind", terminal.projectileKind, ProjectileKind.GatlingBullet)
    ContractAssertions.assertEquals("runtime gatling projectile terminal reason", terminal.reason, ProjectileTerminalReason.Hit)
    ContractAssertions.assertEquals("runtime gatling projectile terminal target", terminal.targetPlayerId, Some(PlayerId("bot-one")))
    ContractAssertions.assertEquals("runtime gatling projectile terminal hp before", terminal.hpBefore, Some(HitPoints(100)))
    ContractAssertions.assertEquals("runtime gatling projectile terminal hp after", terminal.hpAfter, Some(HitPoints(95)))
    ContractAssertions.assertEquals("runtime gatling projectile terminal damage", terminal.damage, Some(Damage(5)))
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"runtime gatling hit terminal should preserve full segment end beyond hit point, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )

  private def rocketSplashDamageReportsDirectTargetAndDamagesNearbyTargets(): Unit =
    val clock = TestClock(1_000L)
    val seats = Vector(
      seat(
        playerId = PlayerId("alice"),
        heroId = HeroId("hero-alice"),
        handle = PlayerHandle("Alice"),
        displayName = DisplayName("Alice"),
        seat = SeatIndex(0),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(3))
      ),
      seat(
        playerId = PlayerId("direct"),
        heroId = HeroId("hero-direct"),
        handle = PlayerHandle("Direct"),
        displayName = DisplayName("Direct"),
        seat = SeatIndex(1),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(0))
      ),
      seat(
        playerId = PlayerId("splash"),
        heroId = HeroId("hero-splash"),
        handle = PlayerHandle("Splash"),
        displayName = DisplayName("Splash"),
        seat = SeatIndex(2),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(0))
      )
    )
    val service = battleStateService(
      clock = clock,
      seed = sessionSeedWithSeats(
        seats,
        battleMode = BattleMode.Autumn,
        commandOwnership = Vector(
          BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")),
          BattleCommandOwnership(PlayerId("direct"), TicketId("ticket-direct")),
          BattleCommandOwnership(PlayerId("splash"), TicketId("ticket-splash"))
        )
      )
    )

    battleState(service, "rocket splash initial")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 34L,
        movement = BattleCommandVector(-5.0, -1.0)
      )
    ).fold(error => fail(s"rocket splash pickup movement failed: $error"), identity)

    clock.now = 2_300L
    val pickedUp = battleState(service, "rocket splash picked up")
    val rocketAlice = playerById(pickedUp, PlayerId("alice"))
    val directBefore = playerById(pickedUp, PlayerId("direct"))
    val rocketIndex = rocketAlice.weapons.indexWhere(_.weaponKind == WeaponKind.RocketLauncher)
    assert(rocketIndex >= 0, "runtime rocket splash setup should pick up RocketLauncher")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 35L,
        switchWeaponIndex = Some(rocketIndex)
      )
    ).fold(error => fail(s"rocket splash switch failed: $error"), identity)
    val rocketReady = battleState(service, "rocket splash ready")
    val rocketReadyAlice = playerById(rocketReady, PlayerId("alice"))
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 36L,
        aim = BattleCommandVector(directBefore.position.x - rocketReadyAlice.position.x, directBefore.position.y - rocketReadyAlice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"rocket splash fire failed: $error"), identity)

    clock.now = 2_333L
    val afterFire = battleState(service, "rocket splash after fire")
    assert(afterFire.projectiles.exists(_.projectileKind == ProjectileKind.Rocket), "runtime rocket splash should create a live rocket before impact")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 37L,
        aim = BattleCommandVector(directBefore.position.x - rocketReadyAlice.position.x, directBefore.position.y - rocketReadyAlice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"rocket splash release failed: $error"), identity)

    clock.now = 3_500L
    val afterImpact = battleState(service, "rocket splash after impact")
    val directAfter = playerById(afterImpact, PlayerId("direct"))
    val splashAfter = playerById(afterImpact, PlayerId("splash"))
    val terminal = afterImpact.projectileTerminals
      .find(_.projectileKind == ProjectileKind.Rocket)
      .getOrElse(fail("missing rocket splash terminal"))

    ContractAssertions.assertEquals("runtime rocket splash direct target damaged", directAfter.hp, HitPoints(40))
    ContractAssertions.assertEquals("runtime rocket splash nearby target damaged", splashAfter.hp, HitPoints(40))
    ContractAssertions.assertEquals("runtime rocket splash battle remains active", afterImpact.phase, BattlePhase.Active)
    ContractAssertions.assertEquals("runtime rocket splash terminal reason", terminal.reason, ProjectileTerminalReason.Hit)
    ContractAssertions.assertEquals("runtime rocket splash terminal target", terminal.targetPlayerId, Some(PlayerId("direct")))
    ContractAssertions.assertEquals("runtime rocket splash terminal hp before", terminal.hpBefore, Some(HitPoints(100)))
    ContractAssertions.assertEquals("runtime rocket splash terminal hp after", terminal.hpAfter, Some(HitPoints(40)))
    ContractAssertions.assertEquals("runtime rocket splash terminal damage", terminal.damage, Some(Damage(60)))

  private def eliminationDoesNotRespawnAndFinishesBattle(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "elimination finish initial")
    val alice = playerById(initial, PlayerId("alice"))
    val bot = playerById(initial, PlayerId("bot-one"))
    val aimAtBot = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y)

    (0 until 9).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 260L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 14L + shot,
          aim = aimAtBot,
          primaryHeld = true
        )
      ).fold(error => fail(s"lethal shot $shot failed: $error"), identity)
    }

    clock.now = 3_700L
    val finished = battleState(service, "elimination finished")
    assertLifecycleBooleanOptionInvariants("after finished elimination", finished)
    val eliminatedBot = playerById(finished, PlayerId("bot-one"))

    ContractAssertions.assertEquals("runtime battle finishes after one survivor remains", finished.phase, BattlePhase.Finished)
    ContractAssertions.assertEquals("runtime survivor wins", finished.winnerPlayerId, Some(PlayerId("alice")))
    ContractAssertions.assertEquals("runtime eliminated bot dead", eliminatedBot.alive, false)
    ContractAssertions.assertEquals("runtime eliminated bot hp", eliminatedBot.hp, HitPoints(0))
    ContractAssertions.assertEquals("runtime eliminated bot has no respawn timer", eliminatedBot.respawnMs, DurationMillis(0L))
    ContractAssertions.assertEquals("runtime no respawn event emitted", finished.events.exists(_.eventKind == BattleEventKind.Respawn), false)

    clock.now = 5_000L
    val later = battleState(service, "elimination no-respawn wait")
    assertLifecycleBooleanOptionInvariants("after no-respawn wait", later)
    val laterBot = playerById(later, PlayerId("bot-one"))

    ContractAssertions.assertEquals("runtime battle remains finished", later.phase, BattlePhase.Finished)
    ContractAssertions.assertEquals("runtime eliminated bot stays dead", laterBot.alive, false)
    ContractAssertions.assertEquals("runtime eliminated bot still has no respawn timer", laterBot.respawnMs, DurationMillis(0L))

  private def eliminationClearsDeadPlayerRuntimeBeforeBattleFinish(): Unit =
    val clock = TestClock(1_000L)
    val seats = Vector(
      seat(
        playerId = PlayerId("alice"),
        heroId = HeroId("hero-alice"),
        handle = PlayerHandle("Alice"),
        displayName = DisplayName("Alice"),
        seat = SeatIndex(0),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(0))
      ),
      seat(
        playerId = PlayerId("target"),
        heroId = HeroId("hero-target"),
        handle = PlayerHandle("Target"),
        displayName = DisplayName("Target"),
        seat = SeatIndex(1),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(3))
      ),
      seat(
        playerId = PlayerId("bystander"),
        heroId = HeroId("hero-bystander"),
        handle = PlayerHandle("Bystander"),
        displayName = DisplayName("Bystander"),
        seat = SeatIndex(2),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(1))
      )
    )
    val service = battleStateService(
      clock = clock,
      seed = sessionSeedWithSeats(
        seats,
        battleMode = BattleMode.Autumn,
        commandOwnership = Vector(
          BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")),
          BattleCommandOwnership(PlayerId("target"), TicketId("ticket-target"))
        )
      )
    )
    val initial = battleState(service, "dead runtime cleanup initial")
    val alice = playerById(initial, PlayerId("alice"))
    val target = playerById(initial, PlayerId("target"))
    val aimAtTarget = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y)

    service.acceptCommand(
      command(
        playerId = PlayerId("target"),
        ticketId = TicketId("ticket-target"),
        seq = 1L,
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(target.position.x, target.position.y))
      )
    ).fold(error => fail(s"cleanup target freeze command failed: $error"), identity)
    service.acceptCommand(
      command(
        playerId = PlayerId("target"),
        ticketId = TicketId("ticket-target"),
        seq = 2L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"cleanup target fire command failed: $error"), identity)

    (0 until 12).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 260L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 10L + shot,
          aim = aimAtTarget,
          primaryHeld = true
        )
      ).fold(error => fail(s"cleanup setup shot $shot failed: $error"), identity)
    }

    clock.now = 9_000L
    val afterElimination = battleState(service, "dead runtime cleanup after elimination")
    assertLifecycleBooleanOptionInvariants("active battle after target elimination", afterElimination)
    val eliminatedTarget = playerById(afterElimination, PlayerId("target"))
    val eliminatedWeapon = eliminatedTarget.weapons(eliminatedTarget.currentWeaponIndex)

    ContractAssertions.assertEquals("runtime battle continues with bystander alive", afterElimination.phase, BattlePhase.Active)
    assert(
      !eliminatedTarget.alive,
      s"runtime expected target eliminated, hp=${eliminatedTarget.hp}, projectiles=${afterElimination.projectiles.length}, terminals=${afterElimination.projectileTerminals.takeRight(5)}"
    )
    ContractAssertions.assertEquals("runtime target hp zero", eliminatedTarget.hp, HitPoints(0))
    ContractAssertions.assertEquals("runtime target movement cleared", eliminatedTarget.movement, BattleVector2(0.0, 0.0))
    ContractAssertions.assertEquals("runtime target sprint cleared", eliminatedTarget.sprint, false)
    ContractAssertions.assertEquals("runtime target primary cleared", eliminatedTarget.primaryHeld, false)
    ContractAssertions.assertEquals("runtime target reload cleared", eliminatedTarget.reloadPressed, false)
    ContractAssertions.assertEquals("runtime target respawn remains disabled", eliminatedTarget.respawnMs, DurationMillis(0L))
    assert(eliminatedTarget.skills.forall(_.activeMs == DurationMillis(0L)), s"runtime expected eliminated target active skills cleared, got ${eliminatedTarget.skills}")
    ContractAssertions.assertEquals("runtime target weapon fire cooldown cleared", eliminatedWeapon.fireCooldownMs, CooldownMillis(0))
    ContractAssertions.assertEquals("runtime target weapon reload runtime cleared", eliminatedWeapon.reloadRemainingMs, CooldownMillis(0))

    val projectileCountBeforeIgnoredDeadCommand = afterElimination.projectiles.length
    val terminalCountBeforeIgnoredDeadCommand = afterElimination.projectileTerminals.length
    val ignoredDeadCommand = service.acceptCommand(
      command(
        playerId = PlayerId("target"),
        ticketId = TicketId("ticket-target"),
        seq = 99L,
        movement = BattleCommandVector(1.0, 0.0),
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true,
        sprint = true,
        reloadPressed = true,
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(eliminatedTarget.position.x + 120.0, eliminatedTarget.position.y))
      )
    ).fold(error => fail(s"dead target command failed: $error"), identity)

    ContractAssertions.assertEquals("runtime dead target command ignored", ignoredDeadCommand.commandStatus, BattleCommandStatus.Ignored)
    ContractAssertions.assertEquals("runtime dead target command reason", ignoredDeadCommand.commandReason, Some(BattleCommandReason.PlayerDead))
    ContractAssertions.assertEquals("runtime dead target command keeps stored seq", ignoredDeadCommand.acceptedCommandSeq, eliminatedTarget.lastClientCommandSeq)

    val afterIgnoredDeadCommand = battleState(service, "dead runtime cleanup after ignored dead command")
    val ignoredDeadTarget = playerById(afterIgnoredDeadCommand, PlayerId("target"))
    val ignoredDeadWeapon = ignoredDeadTarget.weapons(ignoredDeadTarget.currentWeaponIndex)
    ContractAssertions.assertEquals("runtime battle remains active after ignored dead command", afterIgnoredDeadCommand.phase, BattlePhase.Active)
    ContractAssertions.assertEquals("runtime ignored dead target stays eliminated", ignoredDeadTarget.alive, false)
    ContractAssertions.assertEquals("runtime ignored dead movement remains cleared", ignoredDeadTarget.movement, BattleVector2(0.0, 0.0))
    ContractAssertions.assertEquals("runtime ignored dead sprint remains cleared", ignoredDeadTarget.sprint, false)
    ContractAssertions.assertEquals("runtime ignored dead primary remains cleared", ignoredDeadTarget.primaryHeld, false)
    ContractAssertions.assertEquals("runtime ignored dead reload remains cleared", ignoredDeadTarget.reloadPressed, false)
    ContractAssertions.assertEquals("runtime ignored dead command does not change ammo", ignoredDeadWeapon.ammoInMagazine, eliminatedWeapon.ammoInMagazine)
    ContractAssertions.assertEquals("runtime ignored dead command creates no projectile", afterIgnoredDeadCommand.projectiles.length, projectileCountBeforeIgnoredDeadCommand)
    ContractAssertions.assertEquals("runtime ignored dead command creates no terminal", afterIgnoredDeadCommand.projectileTerminals.length, terminalCountBeforeIgnoredDeadCommand)

  private def skillCommandSuppressesPrimaryFire(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock, seed = sessionSeed(secondIsBot = false))
    battleState(service, "skill fire suppression initial")

    val accepted = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 190L,
        movement = BattleCommandVector(1.0, 0.0),
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true,
        castDash = true
      )
    ).fold(error => fail(s"skill fire suppression command failed: $error"), identity)

    ContractAssertions.assertEquals("runtime skill fire suppression dash outcome", accepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val afterCommand = battleState(service, "skill fire suppression after command")
    val afterCommandAlice = playerById(afterCommand, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime skill command clears primary", afterCommandAlice.primaryHeld, false)
    ContractAssertions.assertEquals("runtime skill command does not consume ammo", afterCommandAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime skill command creates no projectile immediately", afterCommand.projectiles, Vector.empty)

    clock.now = 1_033L
    val afterTick = battleState(service, "skill fire suppression after tick")
    val afterTickAlice = playerById(afterTick, PlayerId("alice"))
    ContractAssertions.assertEquals("runtime skill keeps full ammo after tick", afterTickAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime skill creates no projectile on tick", afterTick.projectiles, Vector.empty)

  private def noopSkillCommandSuppressesPrimaryFire(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock, seed = sessionSeed(secondIsBot = false))
    battleState(service, "blink missing target fire suppression initial")
    assertNoopSkillSuppressesPrimaryFire(
      label = "runtime blink missing target",
      service = service,
      clock = clock,
      request = command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 191L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true,
        castBlink = true
      ),
      expectedReason = SkillOutcomeReason.MissingTarget
    )

  private def replayFramesCaptureRuntimeAndFinish(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(2_200L),
      seed = sessionSeed(secondIsBot = false)
    )
    val initial = battleState(service, "replay capture initial")
    ContractAssertions.assertEquals("runtime initial replay frame count", initial.replayFrames.length, 1)
    ContractAssertions.assertEquals("runtime initial replay frame elapsed", initial.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    assert(initial.replayFrames.head.pickups.nonEmpty, "runtime initial replay frame should capture pickups")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 700L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"replay capture fire failed: $error"), identity)

    clock.now = 1_033L
    val eventFrameState = battleState(service, "replay capture event frame")
    assert(
      eventFrameState.replayFrames.exists(_.elapsedMs == ElapsedMillis(33L)),
      s"runtime expected pickup event replay frame at 33ms, got ${eventFrameState.replayFrames.map(_.elapsedMs.value)}"
    )
    assert(
      eventFrameState.replayFrames.exists(_.projectiles.nonEmpty),
      "runtime event replay frame should capture live projectile state"
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 701L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = false
      )
    ).fold(error => fail(s"replay capture release failed: $error"), identity)

    clock.now = 2_050L
    val sampled = battleState(service, "replay capture interval frame")
    assert(
      sampled.replayFrames.exists(_.elapsedMs.value >= 1_000L),
      s"runtime expected interval replay frame after 1000ms, got ${sampled.replayFrames.map(_.elapsedMs.value)}"
    )

    clock.now = 3_300L
    val finished = battleState(service, "replay capture final frame")
    ContractAssertions.assertEquals("runtime replay capture finished phase", finished.phase, BattlePhase.Finished)
    ContractAssertions.assertEquals("runtime replay capture first frame preserved", finished.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    ContractAssertions.assertEquals("runtime replay capture final frame elapsed", finished.replayFrames.last.elapsedMs, ElapsedMillis(2_200L))
    ContractAssertions.assertEquals("runtime replay capture final frame clears projectiles", finished.replayFrames.last.projectiles, Vector.empty)

  private def replayFrameHistoryIsBounded(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(60_000L),
      seed = sessionSeed(secondIsBot = false)
    )
    battleState(service, "replay retention initial")

    (1 to 40).foreach { step =>
      clock.now = 1_000L + step.toLong * 1_000L
      battleState(service, s"replay retention step $step")
    }

    val retained = battleState(service, "replay retention final")
    ContractAssertions.assertEquals("runtime replay frame history is capped", retained.replayFrames.length, 32)
    ContractAssertions.assertEquals("runtime replay frame retention preserves initial", retained.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    assert(
      !retained.replayFrames.exists(_.elapsedMs == ElapsedMillis(1_000L)),
      s"runtime old replay interval frame should be pruned, got ${retained.replayFrames.map(_.elapsedMs.value)}"
    )

  private def botRuntimeControlMovesAimsAndFiresAfterOpeningDelay(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7)
      )
    )

    val initial = battleState(service, "bot control initial")
    val initialBot = playerById(initial, PlayerId("bot-one"))

    clock.now = 5_900L
    val beforeOpeningDelay = battleState(service, "bot control before opening fire delay")
    val movedBot = playerById(beforeOpeningDelay, PlayerId("bot-one"))
    assert(
      distanceBetweenForTest(initialBot.position, movedBot.position) > 10.0,
      s"runtime expected bot to move without client commands, initial=${initialBot.position}, moved=${movedBot.position}"
    )
    assert(vectorLengthForTest(movedBot.movement) > 0.9, s"runtime expected bot movement intent, got ${movedBot.movement}")
    assert(vectorLengthForTest(movedBot.aim) > 0.9, s"runtime expected bot aim intent, got ${movedBot.aim}")
    ContractAssertions.assertEquals(
      "runtime bot does not fire before opening fire delay",
      beforeOpeningDelay.projectiles.exists(_.ownerHeroId == movedBot.heroId),
      false
    )
    ContractAssertions.assertEquals("runtime bot primary held before opening fire delay", movedBot.primaryHeld, false)

    clock.now = 6_200L
    val afterOpeningDelay = battleState(service, "bot control after opening fire delay")
    val firingBot = playerById(afterOpeningDelay, PlayerId("bot-one"))
    val firingAlice = playerById(afterOpeningDelay, PlayerId("alice"))
    val botProjectiles = afterOpeningDelay.projectiles.filter(_.ownerHeroId == firingBot.heroId)
    val botTerminals = afterOpeningDelay.projectileTerminals.filter(_.ownerHeroId == firingBot.heroId)
    val firedProjectileKind =
      botProjectiles.headOption.map(_.projectileKind).orElse(botTerminals.headOption.map(_.projectileKind))
    assert(
      botProjectiles.nonEmpty || botTerminals.nonEmpty,
      s"runtime expected bot to fire after opening fire delay, elapsed=${afterOpeningDelay.elapsedMs.value}, primaryHeld=${firingBot.primaryHeld}, distance=${distanceBetweenForTest(firingBot.position, firingAlice.position)}, bot=${firingBot.position}, target=${firingAlice.position}, active=${botProjectiles.length}, terminals=${botTerminals.length}, weapon=${firingBot.weapons.headOption}"
    )
    ContractAssertions.assertEquals("runtime bot primary held after opening fire delay", firingBot.primaryHeld, true)
    ContractAssertions.assertEquals("runtime bot projectile kind", firedProjectileKind, Some(ProjectileKind.PistolBullet))
    val firingBotWeapon = firingBot.weapons.headOption.getOrElse(fail("missing firing bot weapon"))
    assert(firingBotWeapon.ammoInMagazine.value > 0, s"runtime bot should still have ammo after early firing window, weapon=$firingBotWeapon")
    assert(
      firingBotWeapon.ammoInMagazine.value < firingBotWeapon.magazineSize.value,
      s"runtime bot should have spent ammo after firing, weapon=$firingBotWeapon"
    )
    ContractAssertions.assertEquals("runtime bot does not reload until magazine is empty", firingBotWeapon.reloadRemainingMs, CooldownMillis(0))
    ContractAssertions.assertEquals(
      "runtime bot external commands remain rejected",
      service.acceptCommand(command(PlayerId("bot-one"), TicketId("ticket-alice"), 98L)),
      Left(BattleCommandSubmitError.BotCommandsNotSupported)
    )

  private def emptyMagazineStartsAutomaticReload(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)
    battleState(service, "auto reload initial")

    (0 until 12).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 300L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 100L + shot,
          primaryHeld = true
        )
      ).fold(error => fail(s"auto reload shot $shot failed: $error"), identity)
    }

    val emptied = aliceWeapon(service, "empty magazine")
    ContractAssertions.assertEquals("runtime last shot empties magazine", emptied.ammoInMagazine, AmmoCount(0))
    assert(
      emptied.reloadRemainingMs.value > 0 && emptied.reloadRemainingMs.value <= 1000,
      s"runtime empty magazine starts reload, got ${emptied.reloadRemainingMs}"
    )
    ContractAssertions.assertEquals("runtime reserve waits until reload completes", emptied.reserveAmmo, Some(AmmoCount(48)))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 120L,
        primaryHeld = false
      )
    ).fold(error => fail(s"auto reload release failed: $error"), identity)

    clock.now = 5_400L
    val reloaded = aliceWeapon(service, "auto reload completed")
    ContractAssertions.assertEquals("runtime auto reload fills magazine", reloaded.ammoInMagazine, AmmoCount(12))
    ContractAssertions.assertEquals("runtime auto reload consumes reserve", reloaded.reserveAmmo, Some(AmmoCount(36)))
    ContractAssertions.assertEquals("runtime auto reload clears timer", reloaded.reloadRemainingMs, CooldownMillis(0))

  private def finishedStateProjectsArtifactsOnce(): Unit =
    val clock = TestClock(1_000L)
    val projector = RecordingProjector(BattleFinishProjectionOutcome.Projected)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(1_000L), finishProjector = projector)

    val active = battleState(service, "projection active")
    ContractAssertions.assertEquals("runtime initial phase", active.phase, BattlePhase.Active)
    ContractAssertions.assertEquals("runtime projector before finish", projector.projectedStates.length, 0)

    clock.now = 2_500L
    val finished = battleState(service, "projection finished")

    ContractAssertions.assertEquals("runtime finished phase", finished.phase, BattlePhase.Finished)
    ContractAssertions.assertEquals("runtime finished elapsed is capped", finished.elapsedMs, ElapsedMillis(1_000L))
    ContractAssertions.assertEquals("runtime finished artifact status", finished.artifactStatus, BattleArtifactStatus.Ready)
    ContractAssertions.assertEquals("runtime timeout finish does not invent winner", finished.winnerPlayerId, None)
    ContractAssertions.assertEquals("runtime projector called once", projector.projectedStates.length, 1)
    ContractAssertions.assertEquals("runtime projected state is finished", projector.projectedStates.head.phase, BattlePhase.Finished)
    ContractAssertions.assertEquals("runtime projected artifact is pending", projector.projectedStates.head.artifactStatus, BattleArtifactStatus.Pending)

    val reread = battleState(service, "projection reread")
    ContractAssertions.assertEquals("runtime reread keeps artifacts ready", reread.artifactStatus, BattleArtifactStatus.Ready)
    ContractAssertions.assertEquals("runtime projector is not called again", projector.projectedStates.length, 1)

  private def finishedStateTracksPartialArtifactReadiness(): Unit =
    val resultOnly = finishedStateAfterProjection(BattleFinishProjectionOutcome.ResultProjectedReplayFailed("replay unavailable"))
    ContractAssertions.assertEquals("runtime result-only artifact status", resultOnly.artifactStatus, BattleArtifactStatus.ResultOnlyReady)
    ContractAssertions.assertEquals("runtime result-only result ready", BattleArtifactStatus.isResultReady(resultOnly.artifactStatus), true)
    ContractAssertions.assertEquals("runtime result-only replay not ready", BattleArtifactStatus.isReplayReady(resultOnly.artifactStatus), false)

    val replayOnly = finishedStateAfterProjection(BattleFinishProjectionOutcome.ResultFailedReplayProjected("result unavailable"))
    ContractAssertions.assertEquals("runtime replay-only artifact status", replayOnly.artifactStatus, BattleArtifactStatus.ReplayOnlyReady)
    ContractAssertions.assertEquals("runtime replay-only result not ready", BattleArtifactStatus.isResultReady(replayOnly.artifactStatus), false)
    ContractAssertions.assertEquals("runtime replay-only replay ready", BattleArtifactStatus.isReplayReady(replayOnly.artifactStatus), true)

  private def throwingFinishProjectorDoesNotLeaveProjectionInProgress(): Unit =
    val clock = TestClock(1_000L)
    val projector = ThrowOnceThenProjector(BattleFinishProjectionOutcome.Projected)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(1_000L), finishProjector = projector)

    battleState(service, "throwing projection initial")
    clock.now = 2_500L
    val firstFinished = battleState(service, "throwing projection first finished")
    val retried = battleState(service, "throwing projection retry")

    ContractAssertions.assertEquals("runtime throwing projector first read stays pending", firstFinished.artifactStatus, BattleArtifactStatus.Pending)
    ContractAssertions.assertEquals("runtime throwing projector retried projection", retried.artifactStatus, BattleArtifactStatus.Ready)
    ContractAssertions.assertEquals("runtime throwing projector attempted twice", projector.attempts, 2)

  private def ignoredFinishedCommandUsesStoredClientSequence(): Unit =
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(1_000L))

    battleState(service, "ignored finished command initial")
    val applied = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 41L))
      .fold(error => fail(s"active command failed: $error"), identity)
    ContractAssertions.assertEquals("runtime active command seq", applied.acceptedCommandSeq, ClientCommandSeq(41L))

    clock.now = 2_500L
    val finished = battleState(service, "ignored finished command finished")
    ContractAssertions.assertEquals("runtime ignored command test finished phase", finished.phase, BattlePhase.Finished)

    val ignored = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 99L))
      .fold(error => fail(s"finished command failed: $error"), identity)
    ContractAssertions.assertEquals("runtime finished command ignored", ignored.commandStatus, BattleCommandStatus.Ignored)
    ContractAssertions.assertEquals("runtime finished command reason", ignored.commandReason, Some(BattleCommandReason.BattleFinished))
    ContractAssertions.assertEquals("runtime ignored command keeps stored seq", ignored.acceptedCommandSeq, ClientCommandSeq(41L))

  private def finishedStateMarksQueueRoomFinished(): Unit =
    val clock = TestClock(1_000L)
    val lifecycleSink = RecordingRoomLifecycleSink()
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(1_000L),
      finishProjector = RecordingProjector(BattleFinishProjectionOutcome.Projected),
      roomLifecycleSink = lifecycleSink
    )

    battleState(service, "room finish lifecycle initial")
    clock.now = 2_500L
    battleState(service, "room finish lifecycle finished")
    battleState(service, "room finish lifecycle reread")

    ContractAssertions.assertEquals(
      "runtime room finish lifecycle notifications",
      lifecycleSink.finishedRooms,
      Vector(RoomId("room-state-runtime") -> EpochMillis(2_000L))
    )

  private def finishedStateAfterProjection(outcome: BattleFinishProjectionOutcome): BattleAggregateState =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(1_000L),
      finishProjector = RecordingProjector(outcome)
    )

    battleState(service, "partial artifact initial")
    clock.now = 2_500L
    battleState(service, "partial artifact finished")

  private final case class ProjectileHitOutcome(
    targetHp: HitPoints,
    terminal: BattleProjectileTerminalState,
    liveOwnerProjectiles: Int
  )

  private def pistolHitOutcomeAfterReads(readTimes: Vector[Long], context: String): ProjectileHitOutcome =
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )
    val initial = battleState(service, s"$context initial")
    val alice = playerById(initial, PlayerId("alice"))
    val target = playerById(initial, PlayerId("bot-one"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 101L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"$context shot failed: $error"), identity)

    clock.now = 1_033L
    battleState(service, s"$context projectile birth")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 102L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"$context release failed: $error"), identity)

    val finalState = readTimes.foldLeft(battleState(service, s"$context after release")) { case (_, readTime) =>
      clock.now = readTime
      battleState(service, s"$context read $readTime")
    }
    val finalTarget = playerById(finalState, PlayerId("bot-one"))
    val terminal = finalState.projectileTerminals
      .find(terminal => terminal.ownerHeroId == alice.heroId && terminal.projectileKind == ProjectileKind.PistolBullet)
      .getOrElse(fail(s"missing pistol hit terminal for $context"))
    val liveOwnerProjectiles = finalState.projectiles.count(projectile =>
      projectile.ownerHeroId == alice.heroId && projectile.projectileKind == ProjectileKind.PistolBullet
    )

    ProjectileHitOutcome(finalTarget.hp, terminal, liveOwnerProjectiles)

  private def battleStateService(
    clock: TestClock,
    battleDuration: DurationMillis = DurationMillis(60_000L),
    finishProjector: BattleFinishProjector = RecordingProjector(BattleFinishProjectionOutcome.NotConfigured),
    roomLifecycleSink: BattleRoomLifecycleSink = RecordingRoomLifecycleSink(),
    seed: BattleSessionSeed = sessionSeed()
  ): InMemoryBattleStateService =
    new InMemoryBattleStateService(
      sessionLookup = FixedBattleSessionLookup(seed),
      currentTimeMillis = clock.millis,
      battleDuration = battleDuration,
      finishProjector = finishProjector,
      roomLifecycleSink = roomLifecycleSink
    )

  private def sessionSeed(
    aliceSpawnPointIndex: SpawnPointIndex = SpawnPointIndex(0),
    botSpawnPointIndex: SpawnPointIndex = SpawnPointIndex(1),
    secondIsBot: Boolean = true,
    battleMode: BattleMode = BattleMode.Autumn
  ): BattleSessionSeed =
    BattleSessionSeed(
      roomId = RoomId("room-state-runtime"),
      descriptor = BattleSessionDescriptor(
        battleId = BattleId("battle-state-runtime"),
        battleMode = battleMode,
        startedAt = EpochMillis(1_000L),
        serverTime = EpochMillis(1_000L),
        roster = Vector.empty,
        capacity = BattleCapacity(2),
        bootstrap = Some(
          BattleSessionBootstrap(
            Vector(
              seat(
                playerId = PlayerId("alice"),
                heroId = HeroId("hero-alice"),
                handle = PlayerHandle("Alice"),
                displayName = DisplayName("Alice"),
                seat = SeatIndex(0),
                isBot = false,
                spawnPointIndex = Some(aliceSpawnPointIndex)
              ),
              seat(
                playerId = PlayerId("bot-one"),
                heroId = HeroId("hero-bot-one"),
                handle = PlayerHandle("Bot 1"),
                displayName = DisplayName("Bot 1"),
                seat = SeatIndex(1),
                isBot = secondIsBot,
                spawnPointIndex = Some(botSpawnPointIndex)
              )
            )
          )
        )
      ),
      commandOwnership =
        if secondIsBot then Vector(BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")))
        else
          Vector(
            BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")),
            BattleCommandOwnership(PlayerId("bot-one"), TicketId("ticket-bot-one"))
          )
    )

  private def sessionSeedWithSeats(
    seats: Vector[BattleSessionBootstrapSeat],
    battleMode: BattleMode = BattleMode.Default,
    commandOwnership: Vector[BattleCommandOwnership] = Vector.empty
  ): BattleSessionSeed =
    BattleSessionSeed(
      roomId = RoomId("room-state-runtime"),
      descriptor = BattleSessionDescriptor(
        battleId = BattleId("battle-state-runtime"),
        battleMode = battleMode,
        startedAt = EpochMillis(1_000L),
        serverTime = EpochMillis(1_000L),
        roster = Vector.empty,
        capacity = BattleCapacity(seats.length),
        bootstrap = Some(BattleSessionBootstrap(seats))
      ),
      commandOwnership =
        if commandOwnership.nonEmpty then commandOwnership
        else seats.headOption.map(seat => BattleCommandOwnership(seat.playerId, TicketId("ticket-alice"))).toVector
    )

  private def seat(
    playerId: PlayerId,
    heroId: HeroId,
    handle: PlayerHandle,
    displayName: DisplayName,
    seat: SeatIndex,
    isBot: Boolean,
    spawnPointIndex: Option[SpawnPointIndex] = None
  ): BattleSessionBootstrapSeat =
    BattleSessionBootstrapSeat(
      seat = seat,
      playerId = playerId,
      heroId = heroId,
      handle = handle,
      displayName = displayName,
      joinedAt = EpochMillis(1_000L),
      participantKind = BattleParticipantKind.fromBotFlag(isBot),
      spawnPointIndex = spawnPointIndex.getOrElse(SpawnPointIndex(seat.value)),
      rating = Some(Rating(1200)),
      avatar = None,
      skin = None
    )

  private def command(
    playerId: PlayerId,
    ticketId: TicketId,
    seq: Long,
    movement: BattleCommandVector = BattleCommandVector(0.0, 0.0),
    aim: BattleCommandVector = BattleCommandVector(1.0, 0.0),
    primaryHeld: Boolean = false,
    sprint: Boolean = false,
    reloadPressed: Boolean = false,
    castDash: Boolean = false,
    castBlink: Boolean = false,
    castFreeze: Boolean = false,
    pointerWorld: Option[BattleCommandVector] = None,
    switchWeaponDirection: BattleWeaponSwitchDirection = BattleWeaponSwitchDirection.NoSwitch,
    switchWeaponIndex: Option[Int] = None
  ): BattleCommandRequest =
    BattleCommandRequest(
      battleId = BattleId("battle-state-runtime"),
      playerId = playerId,
      ticketId = ticketId,
      clientTick = BattleTick(0L),
      clientCommandSeq = ClientCommandSeq(seq),
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = sprint,
      reloadPressed = reloadPressed,
      skillIntents = BattleCommandSkillIntents.fromLegacyFlags(
        castDash = castDash,
        castBlink = castBlink,
        castFreeze = castFreeze
      ),
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex.flatMap(BattleWeaponSwitchIndex.fromWire)
    )

  private def battleState(service: InMemoryBattleStateService, context: String): BattleAggregateState =
    service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found for $context: $error"), identity)

  private def playerById(state: BattleAggregateState, playerId: PlayerId): BattlePlayerState =
    state.players.find(_.playerId == playerId).getOrElse(fail(s"missing player ${playerId.value}"))

  private def aliceWeapon(service: InMemoryBattleStateService, context: String): BattleWeaponState =
    val state = battleState(service, context)
    val alice = playerById(state, PlayerId("alice"))
    alice.weapons.headOption.getOrElse(fail(s"missing pistol for $context"))

  private def assertLifecycleBooleanOptionInvariants(label: String, state: BattleAggregateState): Unit =
    state.players.foreach { player =>
      if player.alive then
        ContractAssertions.assertEquals(s"$label ${player.playerId.value} alive has no eliminatedAtMs", player.eliminatedAtMs, None)
        ContractAssertions.assertEquals(s"$label ${player.playerId.value} alive has no respawn timer", player.respawnMs, DurationMillis(0L))
      else
        assert(player.eliminatedAtMs.nonEmpty, s"$label ${player.playerId.value} eliminated player must carry eliminatedAtMs")
        ContractAssertions.assertEquals(s"$label ${player.playerId.value} eliminated has no respawn timer", player.respawnMs, DurationMillis(0L))
    }

    state.pickups.foreach { pickup =>
      if pickup.available then
        ContractAssertions.assertEquals(s"$label ${pickup.pickupId.value} available pickup has no respawn timer", pickup.respawnMs, DurationMillis(0L))
      else
        assert(
          pickup.respawnMs.value > 0L,
          s"$label ${pickup.pickupId.value} unavailable pickup must have positive respawn timer, got ${pickup.respawnMs}"
        )
    }

  private def assertNoopSkillSuppressesPrimaryFire(
    label: String,
    service: InMemoryBattleStateService,
    clock: TestClock,
    request: BattleCommandRequest,
    expectedReason: SkillOutcomeReason
  ): Unit =
    val before = battleState(service, s"$label before noop skill")
    val beforeAlice = playerById(before, PlayerId("alice"))
    val beforeAmmo = beforeAlice.weapons.head.ammoInMagazine
    val beforeProjectileCount = before.projectiles.length
    val beforeTerminalCount = before.projectileTerminals.length

    val accepted = service.acceptCommand(request)
      .fold(error => fail(s"$label command failed: $error"), identity)

    ContractAssertions.assertEquals(s"$label outcome", accepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    ContractAssertions.assertEquals(s"$label reason", accepted.outcomes.headOption.flatMap(_.reason), Some(expectedReason))

    val afterCommand = battleState(service, s"$label after command")
    val afterCommandAlice = playerById(afterCommand, PlayerId("alice"))
    ContractAssertions.assertEquals(s"$label command clears primary held", afterCommandAlice.primaryHeld, false)
    ContractAssertions.assertEquals(s"$label command does not consume ammo", afterCommandAlice.weapons.head.ammoInMagazine, beforeAmmo)
    ContractAssertions.assertEquals(s"$label command creates no live projectile", afterCommand.projectiles.length, beforeProjectileCount)
    ContractAssertions.assertEquals(s"$label command creates no projectile terminal", afterCommand.projectileTerminals.length, beforeTerminalCount)

    clock.now += 33L
    val afterTick = battleState(service, s"$label after tick")
    val afterTickAlice = playerById(afterTick, PlayerId("alice"))
    ContractAssertions.assertEquals(s"$label tick keeps primary released", afterTickAlice.primaryHeld, false)
    ContractAssertions.assertEquals(s"$label tick does not consume ammo", afterTickAlice.weapons.head.ammoInMagazine, beforeAmmo)
    ContractAssertions.assertEquals(s"$label tick creates no live projectile", afterTick.projectiles.length, beforeProjectileCount)
    ContractAssertions.assertEquals(s"$label tick creates no projectile terminal", afterTick.projectileTerminals.length, beforeTerminalCount)

  private def assertProjectileTravelAlignedWithVelocity(
    label: String,
    ownerPosition: BattleVector2,
    projectile: BattleProjectileState
  ): Unit =
    val offset = BattleVector2(projectile.position.x - ownerPosition.x, projectile.position.y - ownerPosition.y)
    val offsetLength = distanceBetweenForTest(projectile.position, ownerPosition)
    val velocityLength = math.hypot(projectile.velocity.x, projectile.velocity.y)
    assert(offsetLength > 0.0, s"$label offset should be non-zero")
    assert(velocityLength > 0.0, s"$label velocity should be non-zero")
    val normalizedCross = math.abs(offset.x * projectile.velocity.y - offset.y * projectile.velocity.x) / (offsetLength * velocityLength)
    assert(
      normalizedCross <= 0.001,
      s"$label position and velocity should be aligned, cross=$normalizedCross, offset=$offset, velocity=${projectile.velocity}"
    )

  private def distanceBetweenForTest(left: BattleVector2, right: BattleVector2): Double =
    math.hypot(left.x - right.x, left.y - right.y)

  private def vectorLengthForTest(vector: BattleVector2): Double =
    math.hypot(vector.x, vector.y)

  private def assertClose(label: String, actual: Double, expected: Double, tolerance: Double): Unit =
    assert(math.abs(actual - expected) <= tolerance, s"$label: expected $expected +/- $tolerance, got $actual")

  private final case class TestClock(var now: Long):
    def millis(): Long = now

  private final case class FixedBattleSessionLookup(seed: BattleSessionSeed) extends BattleSessionLookup:
    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      Option.when(seed.descriptor.battleId == battleId)(seed)

  private final case class RecordingProjector(outcome: BattleFinishProjectionOutcome) extends BattleFinishProjector:
    var projectedStates: Vector[BattleAggregateState] = Vector.empty

    override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
      projectedStates = projectedStates :+ state
      outcome

  private final case class ThrowOnceThenProjector(outcome: BattleFinishProjectionOutcome) extends BattleFinishProjector:
    var attempts: Int = 0

    override def project(state: BattleAggregateState): BattleFinishProjectionOutcome =
      attempts += 1
      if attempts == 1 then throw RuntimeException("projection boom")
      else outcome

  private final case class RecordingRoomLifecycleSink() extends BattleRoomLifecycleSink:
    var finishedRooms: Vector[(RoomId, EpochMillis)] = Vector.empty

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      if !finishedRooms.exists(_._1 == roomId) then finishedRooms = finishedRooms :+ (roomId -> finishedAt)

  private def fail(message: String): Nothing =
    throw AssertionError(message)

private[contract] object BattleFinishProjectionContractTest:
  def run(): Unit =
    nonFinishedStateWritesNothing()
    finishedStateWritesResultsMailsAndReplay()
    previousRatingLookupIgnoresCurrentBattleRecords()
    failedProjectionReportsExplicitMessage()
    replayWriteFailureReportsResultOnlyReady()
    resultWriteFailureReportsReplayOnlyReady()
    resultReadyStateSkipsResultWriteAndRetriesReplay()
    replayReadyStateSkipsReplayWriteAndRetriesResult()

  private def finishProjector(
    battleResults: BattleResultRepository,
    replays: ReplayRepository,
    mails: MailRepository,
    reporter: BattleFinishProjectionFailureReporter = ConsoleBattleFinishProjectionFailureReporter
  ): DefaultBattleFinishProjector =
    new DefaultBattleFinishProjector(
      battleResults,
      replayWriter(replays),
      mailPublisher(mails),
      reporter
    )

  private def replayWriter(replays: ReplayRepository): BattleReplayWriterPort =
    new BattleReplayWriterPort {
      override def saveReplay(record: ReplayRecord): Unit =
        replays.saveReplay(record)
    }

  private def mailPublisher(mails: MailRepository): BattleMailPublisherPort =
    new BattleMailPublisherPort {
      override def publish(mail: MailRecord): Unit =
        mails.save(mail)
    }

  private def nonFinishedStateWritesNothing(): Unit =
    val battleResults = InMemoryBattleResultRepository()
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = finishProjector(battleResults, replays, mails)
    val state = finishedBattleState().copy(phase = BattlePhase.Active)

    ContractAssertions.assertEquals("non-finished outcome", projector.project(state), BattleFinishProjectionOutcome.NotConfigured)
    ContractAssertions.assertEquals("non-finished results", battleResults.list(None, None, 20), Vector.empty)
    ContractAssertions.assertEquals("non-finished replays", replays.listReplays(20), Vector.empty)
    ContractAssertions.assertEquals("non-finished alice mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
    ContractAssertions.assertEquals("non-finished bob mails", mails.listByOwner(PlayerHandle("Bob")), Vector.empty)

  private def finishedStateWritesResultsMailsAndReplay(): Unit =
    val fixture = projectedFixture()
    val state = fixture.state
    val results = fixture.battleResults.list(None, Some(state.battleId), 20)

    ContractAssertions.assertEquals("finished projection outcome", fixture.outcome, BattleFinishProjectionOutcome.Projected)
    ContractAssertions.assertEquals("result handles exclude bot", results.map(_.handle.value), Vector("Alice", "Bob"))
    ContractAssertions.assertEquals(
      "result placements count bot",
      results.map(_.placement),
      Vector(Some(BattlePlacement.unsafe(2)), Some(BattlePlacement.unsafe(3)))
    )
    ContractAssertions.assertEquals("result scores", results.map(_.score.value), Vector(9, 7))
    ContractAssertions.assertEquals("alice previous rating", resultFor(results, "alice").ratingBefore, Rating(1500))
    ContractAssertions.assertEquals("alice rating after", resultFor(results, "alice").ratingAfter, Rating(1514))
    ContractAssertions.assertEquals("bob default rating", resultFor(results, "bob").ratingBefore, Rating(1200))
    ContractAssertions.assertEquals("bob rating after", resultFor(results, "bob").ratingAfter, Rating(1206))
    ContractAssertions.assertEquals("alice result label", resultFor(results, "alice").resultLabel.value, "存活结算")
    ContractAssertions.assertEquals("bob result label", resultFor(results, "bob").resultLabel.value, "淘汰结算")
    ContractAssertions.assertEquals("alice mode label", resultFor(results, "alice").modeLabel.value, "权威对战")
    ContractAssertions.assertEquals("alice current loadout omitted", resultFor(results, "alice").currentLoadout, None)

    assertOwnerMail(
      "alice",
      fixture.mails.listByOwner(PlayerHandle("Alice")),
      Vector(
        MailKind.Battle -> s"mail-battle-${state.battleId.value}:alice",
        MailKind.Reward -> s"mail-rating-${state.battleId.value}:alice"
      )
    )
    assertOwnerMail(
      "bob",
      fixture.mails.listByOwner(PlayerHandle("Bob")),
      Vector(
        MailKind.Battle -> s"mail-battle-${state.battleId.value}:bob",
        MailKind.Reward -> s"mail-rating-${state.battleId.value}:bob"
      )
    )

    val replay = fixture.replays
      .findReplayById(ReplayId(state.battleId.value))
      .getOrElse(throw AssertionError("expected replay to be saved"))
    ContractAssertions.assertEquals("replay owner", replay.handle, PlayerHandle("Alice"))
    ContractAssertions.assertEquals("replay settlement handles", replay.settlements.map(_.handle.value), Vector("Alice", "Bob"))
    ContractAssertions.assertEquals("replay result label", replay.resultLabel, "胜者已决")
    ContractAssertions.assertEquals("replay cover label", replay.coverLabel, "服务器战报")
    ContractAssertions.assertEquals("replay settlement ratings", replay.settlements.map(_.ratingAfter), Vector(Some(Rating(1514)), Some(Rating(1206))))
    ContractAssertions.assertEquals("replay frame count", replay.frameCount.value, 3)
    ContractAssertions.assertEquals("replay playback flag", replay.playbackAvailable, true)
    ContractAssertions.assertContains("replay saved captured frame", replay.framesJson.value, """"elapsedMs":1000""")
    ContractAssertions.assertContains("replay saved pickup", replay.framesJson.value, """"id":"pickup-finish-write"""")
    ContractAssertions.assertContains("replay saved medkit", replay.framesJson.value, """"kind":"medkit"""")

  private def previousRatingLookupIgnoresCurrentBattleRecords(): Unit =
    val fixture = projectedFixture()
    val alice = resultFor(fixture.battleResults.list(Some(PlayerHandle("Alice")), None, 20), "alice")

    ContractAssertions.assertEquals("current battle seed is ignored for previous rating", alice.ratingBefore, Rating(1500))
    ContractAssertions.assertEquals("current battle seed is overwritten by projection", alice.ratingAfter, Rating(1514))

  private def failedProjectionReportsExplicitMessage(): Unit =
    val state = finishedBattleState()
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = finishProjector(
      ThrowingBattleResultRepository(IllegalStateException("repository unavailable")),
      replays,
      mails,
      reporter
    )

    projector.project(state) match {
      case BattleFinishProjectionOutcome.Failed(message) =>
        ContractAssertions.assertContains("failure outcome class", message, "IllegalStateException")
        ContractAssertions.assertContains("failure outcome detail", message, "repository unavailable")
        ContractAssertions.assertEquals("failure reporter records", reporter.reports, Vector(state.battleId -> message))
      case other =>
        throw AssertionError(s"expected failed projection outcome, got $other")
    }

    ContractAssertions.assertEquals("failed projection replays", replays.listReplays(20), Vector.empty)
    ContractAssertions.assertEquals("failed projection alice mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)

  private def replayWriteFailureReportsResultOnlyReady(): Unit =
    val state = finishedBattleState()
    val battleResults = InMemoryBattleResultRepository()
    val replays = SaveFailingReplayRepository(IllegalStateException("replay unavailable"))
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = finishProjector(battleResults, replays, mails, reporter)

    projector.project(state) match {
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) =>
        ContractAssertions.assertContains("replay failure outcome detail", message, "replay unavailable")
        ContractAssertions.assertEquals("replay failure reporter count", reporter.reports.length, 1)
        ContractAssertions.assertContains("replay failure reporter label", reporter.reports.head._2, "replay")
      case other =>
        throw AssertionError(s"expected result-only projection outcome, got $other")
    }

    ContractAssertions.assertEquals(
      "result-only projection writes results",
      battleResults.list(None, Some(state.battleId), 20).map(_.handle.value),
      Vector("Alice", "Bob")
    )
    ContractAssertions.assertEquals("result-only projection writes no replay", replays.listReplays(20), Vector.empty)
    ContractAssertions.assertEquals("result-only projection writes alice mail", mails.listByOwner(PlayerHandle("Alice")).nonEmpty, true)

  private def resultWriteFailureReportsReplayOnlyReady(): Unit =
    val state = finishedBattleState()
    val battleResults = SaveFailingBattleResultRepository(IllegalStateException("result unavailable"))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = finishProjector(battleResults, replays, mails, reporter)

    projector.project(state) match {
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) =>
        ContractAssertions.assertContains("result failure outcome detail", message, "result unavailable")
        ContractAssertions.assertEquals("result failure reporter count", reporter.reports.length, 1)
        ContractAssertions.assertContains("result failure reporter label", reporter.reports.head._2, "result")
      case other =>
        throw AssertionError(s"expected replay-only projection outcome, got $other")
    }

    ContractAssertions.assertEquals("replay-only projection writes no result", battleResults.records, Vector.empty)
    ContractAssertions.assertEquals("replay-only projection writes no mail", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
    ContractAssertions.assertEquals(
      "replay-only projection writes replay",
      replays.findReplayById(ReplayId(state.battleId.value)).isDefined,
      true
    )

  private def resultReadyStateSkipsResultWriteAndRetriesReplay(): Unit =
    val state = finishedBattleState().copy(artifactStatus = BattleArtifactStatus.ResultOnlyReady)
    val battleResults = SaveFailingBattleResultRepository(IllegalStateException("result should not be rewritten"))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = finishProjector(battleResults, replays, mails)

    ContractAssertions.assertEquals("result-ready replay retry outcome", projector.project(state), BattleFinishProjectionOutcome.Projected)
    ContractAssertions.assertEquals("result-ready retry writes replay", replays.findReplayById(ReplayId(state.battleId.value)).isDefined, true)
    ContractAssertions.assertEquals("result-ready retry writes no result mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)

  private def replayReadyStateSkipsReplayWriteAndRetriesResult(): Unit =
    val state = finishedBattleState().copy(artifactStatus = BattleArtifactStatus.ReplayOnlyReady)
    val battleResults = InMemoryBattleResultRepository()
    val replays = SaveFailingReplayRepository(IllegalStateException("replay should not be rewritten"))
    val mails = InMemoryMailRepository()
    val projector = finishProjector(battleResults, replays, mails)

    ContractAssertions.assertEquals("replay-ready result retry outcome", projector.project(state), BattleFinishProjectionOutcome.Projected)
    ContractAssertions.assertEquals(
      "replay-ready retry writes results",
      battleResults.list(None, Some(state.battleId), 20).map(_.handle.value),
      Vector("Alice", "Bob")
    )
    ContractAssertions.assertEquals("replay-ready retry writes alice mail", mails.listByOwner(PlayerHandle("Alice")).nonEmpty, true)

  private final case class ProjectionFixture(
    state: BattleAggregateState,
    battleResults: InMemoryBattleResultRepository,
    replays: InMemoryReplayRepository,
    mails: InMemoryMailRepository,
    outcome: BattleFinishProjectionOutcome
  )

  private final case class ThrowingBattleResultRepository(error: RuntimeException) extends BattleResultRepository:
    override def save(record: BattleResultRecord): BattleResultRecord =
      throw error

    override def list(
      handle: Option[PlayerHandle],
      battleId: Option[BattleId],
      limit: Int
    ): Vector[BattleResultRecord] =
      throw error

  private final class RecordingFailureReporter extends BattleFinishProjectionFailureReporter:
    private var recordedReports: Vector[(BattleId, String)] =
      Vector.empty

    override def reportFailure(battleId: BattleId, message: String): Unit =
      recordedReports = recordedReports :+ (battleId -> message)

    def reports: Vector[(BattleId, String)] =
      recordedReports

  private final case class SaveFailingBattleResultRepository(error: RuntimeException) extends BattleResultRepository:
    override def save(record: BattleResultRecord): BattleResultRecord =
      throw error

    override def list(
      handle: Option[PlayerHandle],
      battleId: Option[BattleId],
      limit: Int
    ): Vector[BattleResultRecord] =
      Vector.empty

    def records: Vector[BattleResultRecord] =
      Vector.empty

  private final case class SaveFailingReplayRepository(error: RuntimeException) extends ReplayRepository:
    private val delegate = InMemoryReplayRepository()

    override def saveReplay(record: ReplayRecord): ReplayRecord =
      throw error

    override def listReplays(limit: Int): Vector[ReplayRecord] =
      delegate.listReplays(limit)

    override def findReplayById(replayId: ReplayId): Option[ReplayRecord] =
      delegate.findReplayById(replayId)

    override def nextCommentId(): ReplayCommentId =
      delegate.nextCommentId()

    override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord =
      delegate.saveComment(record)

    override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
      delegate.listComments(replayId, limit)

  private def projectedFixture(): ProjectionFixture =
    val state = finishedBattleState()
    val battleResults = InMemoryBattleResultRepository()
    battleResults.save(previousResult(BattleId("battle-before"), PlayerHandle("Alice"), Rating(1500), EpochMillis(1709999990000L)))
    battleResults.save(previousResult(state.battleId, PlayerHandle("Alice"), Rating(2000), EpochMillis(1710000001000L)))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = finishProjector(battleResults, replays, mails)

    ProjectionFixture(
      state = state,
      battleResults = battleResults,
      replays = replays,
      mails = mails,
      outcome = projector.project(state)
    )

  private def previousResult(
    battleId: BattleId,
    handle: PlayerHandle,
    ratingAfter: Rating,
    finishedAt: EpochMillis
  ): BattleResultRecord =
    BattleResultRecord(
      battleId = battleId,
      handle = handle,
      displayName = DisplayName(handle.value),
      finishedAt = finishedAt,
      finishedAtLabel = "Previous",
      durationMs = DurationMillis(1000L),
      score = Score(1),
      placement = Some(BattlePlacement.unsafe(1)),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      ratingBefore = Rating(ratingAfter.value - 1),
      ratingDelta = RatingDelta(1),
      ratingAfter = ratingAfter,
      resultLabel = BattleResultLabel.fromWire("Previous"),
      modeLabel = BattleModeLabel.fromWire("Authoritative"),
      mapLabel = BattleMapLabel.fromWire("Arena"),
      highlightLine = BattleHighlightLine.fromWire("Previous result"),
      playersLine = BattlePlayersLine.fromWire(handle.value),
      timelineHint = BattleTimelineHint.fromWire("Previous"),
      currentLoadout = Some("Pistol")
    )

  private def finishedBattleState(): BattleAggregateState =
    val bot = player("bot-zero", "BotZero", "Bot Zero", 1, isBot = true, alive = true, 90, KillCount(1), WeaponKind.Gatling)
    val alice = player("alice", "Alice", "Alice", 2, isBot = false, alive = true, 70, KillCount(3), WeaponKind.RocketLauncher)
    val bob = player("bob", "Bob", "Bob", 0, isBot = false, alive = false, 999, KillCount(8), WeaponKind.Shotgun)
    val replayPickup = pickup("pickup-finish-write", PickupKind.Medkit, None, BattleVector2(96.0, 128.0))
    val replayFrames = Vector(
      replayFrame(0L, Vector(bob, alice, bot), pickups = Vector(replayPickup)),
      replayFrame(1000L, Vector(bob, alice, bot), pickups = Vector(replayPickup)),
      replayFrame(1800L, Vector(bob, alice, bot), pickups = Vector(replayPickup))
    )

    BattleAggregateState(
      battleId = BattleId("battle-finish-write"),
      roomId = RoomId("room-finish-write"),
      mapId = BattleMode.mapId(BattleMode.Default),
      phase = BattlePhase.Finished,
      serverTime = EpochMillis(1710000000000L),
      startedAt = EpochMillis(1709999998200L),
      durationMs = DurationMillis(1800L),
      elapsedMs = ElapsedMillis(1800L),
      endsAt = EpochMillis(1710000000000L),
      worldSize = BattleVector2(1280.0, 720.0),
      tick = BattleTick(60L),
      artifactStatus = BattleArtifactStatus.Pending,
      players = Vector(bob, alice, bot),
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = Vector(replayPickup),
      replayFrames = replayFrames,
      events = Vector(
        BattleEventState(
          eventId = BattleEventId("event-1"),
          eventKind = BattleEventKind.Kill,
          elapsedMs = ElapsedMillis(1200L),
          message = "Alice eliminated Bob.",
          source = participant(alice),
          target = participant(bob)
        )
      ),
      winnerPlayerId = Some(bot.playerId),
      winnerHeroId = Some(bot.heroId)
    )

  private def player(
    id: String,
    handle: String,
    displayName: String,
    seat: Int,
    isBot: Boolean,
    alive: Boolean,
    score: Int,
    kills: KillCount,
    weaponKind: WeaponKind
  ): BattlePlayerState =
    BattlePlayerState(
      playerId = PlayerId(id),
      heroId = HeroId(s"hero-$id"),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      seat = SeatIndex(seat),
      participantKind = BattleParticipantKind.fromBotFlag(isBot),
      position = BattleVector2(seat.toDouble * 10.0, seat.toDouble * 5.0),
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = BattleVector2(0.0, 0.0),
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lastClientCommandSeq = ClientCommandSeq(0L),
      currentWeaponIndex = 0,
      weapons = Vector(weapon(weaponKind)),
      currentWeaponKind = weaponKind,
      hp = HitPoints(if alive then 100 else 0),
      maxHp = HitPoints(100),
      stamina = Stamina(100),
      maxStamina = Stamina(100),
      score = Score(score),
      kills = kills,
      skills = Vector.empty,
      lifeState = BattlePlayerLifeState.fromAliveFlag(
        alive,
        Option.when(!alive)(ElapsedMillis(1600L)),
        DurationMillis(0L)
      )
    )

  private def weapon(weaponKind: WeaponKind): BattleWeaponState =
    BattleWeaponState(
      weaponKind = weaponKind,
      ammoInMagazine = AmmoCount(5),
      magazineSize = AmmoCount(5),
      reserveAmmo = Some(AmmoCount(20)),
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = BattleWeaponHeat(0),
      thermalState = BattleWeaponThermalState.Ready
    )

  private def pickup(
    pickupId: String,
    pickupKind: PickupKind,
    weaponKind: Option[WeaponKind],
    position: BattleVector2
  ): BattlePickupState =
    BattlePickupState(
      pickupId = PickupId(pickupId),
      pickupKind = pickupKind,
      weaponKind = weaponKind,
      position = position,
      pickupAvailability = BattlePickupAvailability.Available
    )

  private def replayFrame(
    elapsedMs: Long,
    players: Vector[BattlePlayerState],
    pickups: Vector[BattlePickupState] = Vector.empty
  ): BattleReplayFrameState =
    BattleReplayFrameState(
      elapsedMs = ElapsedMillis(elapsedMs),
      heroes = players.map { player =>
        BattleReplayHeroFrameState(
          playerId = player.playerId,
          heroId = player.heroId,
          handle = player.handle,
          displayName = player.displayName,
          seat = player.seat,
          position = player.position,
          hp = player.hp,
          maxHp = player.maxHp,
          lifeState = BattleReplayHeroLifeState.fromAliveFlag(player.alive, player.eliminatedAtMs),
          score = player.score,
          facing = player.facing,
          currentWeaponKind = player.currentWeaponKind
        )
      },
      projectiles = Vector.empty,
      pickups = pickups.map { pickup =>
        BattleReplayPickupFrameState(
          pickupId = pickup.pickupId,
          pickupKind = pickup.pickupKind,
          weaponKind = pickup.weaponKind,
          position = pickup.position,
          pickupAvailability = pickup.pickupAvailability
        )
      }
    )

  private def participant(player: BattlePlayerState): BattleEventParticipant =
    BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    )

  private def resultFor(records: Vector[BattleResultRecord], handleKey: String): BattleResultRecord =
    records
      .find(_.handle.key == handleKey)
      .getOrElse(throw AssertionError(s"missing result for $handleKey"))

  private def assertOwnerMail(
    label: String,
    records: Vector[MailRecord],
    expectedKindsAndIds: Vector[(MailKind, String)]
  ): Unit =
    val sorted = records.sortBy(_.id.value)
    ContractAssertions.assertEquals(s"$label mail ids", sorted.map(record => record.kind -> record.id.value), expectedKindsAndIds)
    ContractAssertions.assertEquals(s"$label mail unread flags", sorted.map(_.unread), Vector.fill(expectedKindsAndIds.length)(true))
    ContractAssertions.assertEquals(
      s"$label mail source battle ids",
      sorted.map(_.sourceBattleId),
      Vector.fill(expectedKindsAndIds.length)(Some("battle-finish-write"))
    )
    sorted.foreach { record =>
      ContractAssertions.assertEquals(s"$label mail source label ${record.id.value}", record.sourceLabel, Some("View replay"))
      ContractAssertions.assertContains(
        s"$label mail source path ${record.id.value}",
        record.sourcePath.getOrElse(""),
        "/replay/battle-finish-write"
      )
    }
