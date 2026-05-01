package slaydemo.backend.battle.services

import slaydemo.backend.battle.database.{BattleResultRepository, InMemoryBattleResultRepository}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.mail.objects.{MailKind, MailRecord}
import slaydemo.backend.replay.database.{InMemoryReplayRepository, ReplayRepository}
import slaydemo.backend.replay.objects.{ReplayCommentId, ReplayCommentRecord, ReplayId, ReplayRecord}

object BattleFinishProjectionWriteContractTest {
  def main(args: Array[String]): Unit = {
    nonFinishedStateWritesNothing()
    finishedStateWritesResultsMailsAndReplay()
    previousRatingLookupIgnoresCurrentBattleRecords()
    failedProjectionReportsExplicitMessage()
    replayWriteFailureReportsResultOnlyReady()
    resultWriteFailureReportsReplayOnlyReady()
    resultReadyStateSkipsResultWriteAndRetriesReplay()
    replayReadyStateSkipsReplayWriteAndRetriesResult()

    println("BattleFinishProjectionWrite contract checks passed")
  }

  private def nonFinishedStateWritesNothing(): Unit = {
    val battleResults = InMemoryBattleResultRepository()
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = DefaultBattleFinishProjector(battleResults, replays, mails)
    val state = finishedBattleState().copy(phase = BattlePhase.Active)

    assertEquals("non-finished outcome", projector.project(state), BattleFinishProjectionOutcome.NotConfigured)
    assertEquals("non-finished results", battleResults.list(None, None, 20), Vector.empty)
    assertEquals("non-finished replays", replays.listReplays(20), Vector.empty)
    assertEquals("non-finished alice mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
    assertEquals("non-finished bob mails", mails.listByOwner(PlayerHandle("Bob")), Vector.empty)
  }

  private def finishedStateWritesResultsMailsAndReplay(): Unit = {
    val fixture = projectedFixture()
    val state = fixture.state
    val results = fixture.battleResults.list(None, Some(state.battleId), 20)

    assertEquals("finished projection outcome", fixture.outcome, BattleFinishProjectionOutcome.Projected)
    assertEquals("result handles exclude bot", results.map(_.handle.value), Vector("Alice", "Bob"))
    assertEquals("result placements count bot", results.map(_.placement), Vector(Some(2), Some(3)))
    assertEquals("result scores", results.map(_.score.value), Vector(9, 7))
    assertEquals("alice previous rating", resultFor(results, "alice").ratingBefore, Rating(1500))
    assertEquals("alice rating after", resultFor(results, "alice").ratingAfter, Rating(1514))
    assertEquals("bob default rating", resultFor(results, "bob").ratingBefore, Rating(1200))
    assertEquals("bob rating after", resultFor(results, "bob").ratingAfter, Rating(1206))
    assertEquals("alice result label", resultFor(results, "alice").resultLabel, "存活结算")
    assertEquals("bob result label", resultFor(results, "bob").resultLabel, "淘汰结算")
    assertEquals("alice mode label", resultFor(results, "alice").modeLabel, "权威对战")
    assertEquals("alice current loadout omitted", resultFor(results, "alice").currentLoadout, None)

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
      .getOrElse(fail("expected replay to be saved"))
    assertEquals("replay owner", replay.handle, PlayerHandle("Alice"))
    assertEquals("replay settlement handles", replay.settlements.map(_.handle.value), Vector("Alice", "Bob"))
    assertEquals("replay result label", replay.resultLabel, "胜者已决")
    assertEquals("replay cover label", replay.coverLabel, "服务器战报")
    assertEquals("replay settlement ratings", replay.settlements.map(_.ratingAfter), Vector(Some(Rating(1514)), Some(Rating(1206))))
    assertEquals("replay frame count", replay.frameCount, 3)
    assertEquals("replay playback flag", replay.playbackAvailable, true)
    assertContains("replay saved captured frame", replay.framesJson, "\"elapsedMs\":1000")
  }

  private def previousRatingLookupIgnoresCurrentBattleRecords(): Unit = {
    val fixture = projectedFixture()
    val alice = resultFor(fixture.battleResults.list(Some(PlayerHandle("Alice")), None, 20), "alice")

    assertEquals("current battle seed is ignored for previous rating", alice.ratingBefore, Rating(1500))
    assertEquals("current battle seed is overwritten by projection", alice.ratingAfter, Rating(1514))
  }

  private def failedProjectionReportsExplicitMessage(): Unit = {
    val state = finishedBattleState()
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = new DefaultBattleFinishProjector(
      ThrowingBattleResultRepository(IllegalStateException("repository unavailable")),
      replays,
      mails,
      reporter
    )

    projector.project(state) match {
      case BattleFinishProjectionOutcome.Failed(message) =>
        assertContains("failure outcome class", message, "IllegalStateException")
        assertContains("failure outcome detail", message, "repository unavailable")
        assertEquals("failure reporter records", reporter.reports, Vector(state.battleId -> message))
      case other =>
        fail(s"expected failed projection outcome, got $other")
    }

    assertEquals("failed projection replays", replays.listReplays(20), Vector.empty)
    assertEquals("failed projection alice mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
  }

  private def replayWriteFailureReportsResultOnlyReady(): Unit = {
    val state = finishedBattleState()
    val battleResults = InMemoryBattleResultRepository()
    val replays = SaveFailingReplayRepository(IllegalStateException("replay unavailable"))
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = new DefaultBattleFinishProjector(battleResults, replays, mails, reporter)

    projector.project(state) match {
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) =>
        assertContains("replay failure outcome detail", message, "replay unavailable")
        assertEquals("replay failure reporter count", reporter.reports.length, 1)
        assertContains("replay failure reporter label", reporter.reports.head._2, "replay")
      case other =>
        fail(s"expected result-only projection outcome, got $other")
    }

    assertEquals(
      "result-only projection writes results",
      battleResults.list(None, Some(state.battleId), 20).map(_.handle.value),
      Vector("Alice", "Bob")
    )
    assertEquals("result-only projection writes no replay", replays.listReplays(20), Vector.empty)
    assertEquals("result-only projection writes alice mail", mails.listByOwner(PlayerHandle("Alice")).nonEmpty, true)
  }

  private def resultWriteFailureReportsReplayOnlyReady(): Unit = {
    val state = finishedBattleState()
    val battleResults = SaveFailingBattleResultRepository(IllegalStateException("result unavailable"))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val reporter = RecordingFailureReporter()
    val projector = new DefaultBattleFinishProjector(battleResults, replays, mails, reporter)

    projector.project(state) match {
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) =>
        assertContains("result failure outcome detail", message, "result unavailable")
        assertEquals("result failure reporter count", reporter.reports.length, 1)
        assertContains("result failure reporter label", reporter.reports.head._2, "result")
      case other =>
        fail(s"expected replay-only projection outcome, got $other")
    }

    assertEquals("replay-only projection writes no result", battleResults.records, Vector.empty)
    assertEquals("replay-only projection writes no mail", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
    assertEquals(
      "replay-only projection writes replay",
      replays.findReplayById(ReplayId(state.battleId.value)).isDefined,
      true
    )
  }

  private def resultReadyStateSkipsResultWriteAndRetriesReplay(): Unit = {
    val state = finishedBattleState().copy(artifactStatus = BattleArtifactStatus.ResultOnlyReady)
    val battleResults = SaveFailingBattleResultRepository(IllegalStateException("result should not be rewritten"))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = new DefaultBattleFinishProjector(battleResults, replays, mails)

    assertEquals("result-ready replay retry outcome", projector.project(state), BattleFinishProjectionOutcome.Projected)
    assertEquals("result-ready retry writes replay", replays.findReplayById(ReplayId(state.battleId.value)).isDefined, true)
    assertEquals("result-ready retry writes no result mails", mails.listByOwner(PlayerHandle("Alice")), Vector.empty)
  }

  private def replayReadyStateSkipsReplayWriteAndRetriesResult(): Unit = {
    val state = finishedBattleState().copy(artifactStatus = BattleArtifactStatus.ReplayOnlyReady)
    val battleResults = InMemoryBattleResultRepository()
    val replays = SaveFailingReplayRepository(IllegalStateException("replay should not be rewritten"))
    val mails = InMemoryMailRepository()
    val projector = new DefaultBattleFinishProjector(battleResults, replays, mails)

    assertEquals("replay-ready result retry outcome", projector.project(state), BattleFinishProjectionOutcome.Projected)
    assertEquals(
      "replay-ready retry writes results",
      battleResults.list(None, Some(state.battleId), 20).map(_.handle.value),
      Vector("Alice", "Bob")
    )
    assertEquals("replay-ready retry writes alice mail", mails.listByOwner(PlayerHandle("Alice")).nonEmpty, true)
  }

  private final case class ProjectionFixture(
    state: BattleAggregateState,
    battleResults: InMemoryBattleResultRepository,
    replays: InMemoryReplayRepository,
    mails: InMemoryMailRepository,
    outcome: BattleFinishProjectionOutcome
  )

  private final case class ThrowingBattleResultRepository(error: RuntimeException) extends BattleResultRepository {
    override def save(record: BattleResultRecord): BattleResultRecord =
      throw error

    override def list(
      handle: Option[PlayerHandle],
      battleId: Option[BattleId],
      limit: Int
    ): Vector[BattleResultRecord] =
      throw error
  }

  private final class RecordingFailureReporter extends BattleFinishProjectionFailureReporter {
    private var recordedReports: Vector[(BattleId, String)] =
      Vector.empty

    override def reportFailure(battleId: BattleId, message: String): Unit =
      recordedReports = recordedReports :+ (battleId -> message)

    def reports: Vector[(BattleId, String)] =
      recordedReports
  }

  private final case class SaveFailingBattleResultRepository(error: RuntimeException) extends BattleResultRepository {
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
  }

  private final case class SaveFailingReplayRepository(error: RuntimeException) extends ReplayRepository {
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
  }

  private def projectedFixture(): ProjectionFixture = {
    val state = finishedBattleState()
    val battleResults = InMemoryBattleResultRepository()
    battleResults.save(previousResult(BattleId("battle-before"), PlayerHandle("Alice"), Rating(1500), EpochMillis(1709999990000L)))
    battleResults.save(previousResult(state.battleId, PlayerHandle("Alice"), Rating(2000), EpochMillis(1710000001000L)))
    val replays = InMemoryReplayRepository()
    val mails = InMemoryMailRepository()
    val projector = DefaultBattleFinishProjector(battleResults, replays, mails)

    ProjectionFixture(
      state = state,
      battleResults = battleResults,
      replays = replays,
      mails = mails,
      outcome = projector.project(state)
    )
  }

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
      placement = Some(1),
      aliveAtEnd = true,
      ratingBefore = Rating(ratingAfter.value - 1),
      ratingDelta = 1,
      ratingAfter = ratingAfter,
      resultLabel = "Previous",
      modeLabel = "Authoritative",
      mapLabel = "Arena",
      highlightLine = "Previous result",
      playersLine = handle.value,
      timelineHint = "Previous",
      currentLoadout = Some("Pistol")
    )

  private def finishedBattleState(): BattleAggregateState = {
    val bot = player(
      id = "bot-zero",
      handle = "BotZero",
      displayName = "Bot Zero",
      seat = 1,
      isBot = true,
      alive = true,
      score = 90,
      kills = 1,
      weaponKind = WeaponKind.Gatling
    )
    val alice = player(
      id = "alice",
      handle = "Alice",
      displayName = "Alice",
      seat = 2,
      isBot = false,
      alive = true,
      score = 70,
      kills = 3,
      weaponKind = WeaponKind.RocketLauncher
    )
    val bob = player(
      id = "bob",
      handle = "Bob",
      displayName = "Bob",
      seat = 0,
      isBot = false,
      alive = false,
      score = 999,
      kills = 8,
      weaponKind = WeaponKind.Shotgun
    )
    val replayPickup = pickup("pickup-finish-write", PickupKind.Medkit, None, BattleVector2(96.0, 128.0))
    val replayFrames = Vector(
      replayFrame(0L, Vector(bob, alice, bot), pickups = Vector(replayPickup)),
      replayFrame(1000L, Vector(bob, alice, bot), pickups = Vector(replayPickup)),
      replayFrame(1800L, Vector(bob, alice, bot), pickups = Vector(replayPickup))
    )

    BattleAggregateState(
      battleId = BattleId("battle-finish-write"),
      roomId = RoomId("room-finish-write"),
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
  }

  private def player(
    id: String,
    handle: String,
    displayName: String,
    seat: Int,
    isBot: Boolean,
    alive: Boolean,
    score: Int,
    kills: Int,
    weaponKind: WeaponKind
  ): BattlePlayerState =
    BattlePlayerState(
      playerId = PlayerId(id),
      heroId = HeroId(s"hero-$id"),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      seat = SeatIndex(seat),
      isBot = isBot,
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
      alive = alive,
      eliminatedAtMs = Option.when(!alive)(ElapsedMillis(1600L)),
      respawnMs = DurationMillis(0L)
    )

  private def weapon(weaponKind: WeaponKind): BattleWeaponState =
    BattleWeaponState(
      weaponKind = weaponKind,
      ammoInMagazine = AmmoCount(5),
      magazineSize = AmmoCount(5),
      reserveAmmo = Some(AmmoCount(20)),
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = 0,
      overheated = false,
      overheatRemainingMs = CooldownMillis(0)
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
      available = true,
      respawnMs = DurationMillis(0L)
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
          alive = player.alive,
          score = player.score,
          facing = player.facing,
          currentWeaponKind = player.currentWeaponKind,
          eliminatedAtMs = player.eliminatedAtMs
        )
      },
      projectiles = Vector.empty,
      pickups = pickups.map { pickup =>
        BattleReplayPickupFrameState(
          pickupId = pickup.pickupId,
          pickupKind = pickup.pickupKind,
          weaponKind = pickup.weaponKind,
          position = pickup.position,
          available = pickup.available,
          respawnMs = pickup.respawnMs
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
      .getOrElse(fail(s"missing result for $handleKey"))

  private def assertOwnerMail(
    label: String,
    records: Vector[MailRecord],
    expectedKindsAndIds: Vector[(MailKind, String)]
  ): Unit = {
    val sorted = records.sortBy(_.id.value)
    assertEquals(s"$label mail ids", sorted.map(record => record.kind -> record.id.value), expectedKindsAndIds)
    assertEquals(s"$label mail unread flags", sorted.map(_.unread), Vector.fill(expectedKindsAndIds.length)(true))
    assertEquals(s"$label mail source battle ids", sorted.map(_.sourceBattleId), Vector.fill(expectedKindsAndIds.length)(Some("battle-finish-write")))
    sorted.foreach { record =>
      assertEquals(s"$label mail source label ${record.id.value}", record.sourceLabel, Some("View replay"))
      assertContains(s"$label mail source path ${record.id.value}", record.sourcePath.getOrElse(""), "/replay/battle-finish-write")
    }
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, text: String, expectedPart: String): Unit =
    assert(text.contains(expectedPart), s"$label: expected to find $expectedPart in $text")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
