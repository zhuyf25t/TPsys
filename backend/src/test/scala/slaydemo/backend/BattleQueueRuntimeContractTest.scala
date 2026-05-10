package slaydemo.backend

import slaydemo.backend.battle.objects.{
  BattleCapacity,
  BattleId,
  DurationMillis,
  EpochMillis,
  MatchmakingRoomPhase,
  QueueRequestId,
  Rating,
  TicketId
}
import slaydemo.backend.battle.services.{
  BattleQueueJoinCommand,
  BattleQueueLeaveOutcome,
  BattleQueueStatusError,
  InMemoryBattleQueueService,
  RealtimeRoomHeartbeatCommand
}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

object BattleQueueRuntimeContractTest {
  def main(args: Array[String]): Unit = {
    sameQueueRequestIsIdempotent()
    joinAndHeartbeatNormalizeHandles()
    sameHandleDifferentRequestUsesFreshWaitingRoom()
    sameQueueRequestAfterActiveRoomCreatesFreshTicket()
    sameQueueRequestAfterFinishedRoomCreatesFreshTicket()
    leaveRemovesWaitingTicket()
    leaveDoesNotRemoveActiveTicket()
    leaveDoesNotRemoveFinishedTicket()
    fullRoomWaitsForCountdownBeforeStarting()
    deadlineStartsBattleWithLegacyBotProfiles()
    battleIdUsesInjectedGenerator()
    freshServiceInstancesDoNotReuseBattleIdForSameRoomCounter()
    markBattleFinishedUpdatesStatusAndRoomSnapshot()

    println("BattleQueue runtime contract checks passed")
  }

  private def sameQueueRequestIsIdempotent(): Unit = {
    val queue = queueService()
    val command = joinCommand("Alice", "request-alice")

    val first = queue.join(command)
    val second = queue.join(command)

    assertEquals("same request returns same ticket", second.ticketId, first.ticketId)
    assertEquals("same request returns same room", second.roomId, first.roomId)
    assertEquals("same request does not duplicate participants", second.participants.map(_.handle.value), Vector("Alice"))
    assertEquals("status finds idempotent ticket", queue.status(first.ticketId).map(_.ticketId), Right(first.ticketId))
  }

  private def joinAndHeartbeatNormalizeHandles(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(currentTimeMillis = clock.millis)
    val joined = queue.join(joinCommand(" Alice ", "request-normalized-alice"))

    assertEquals("join stores normalized handle", joined.participants.map(_.handle), Vector(PlayerHandle("Alice")))
    assertEquals("status uses normalized handle", queue.status(joined.ticketId).map(_.participants.map(_.handle)), Right(Vector(PlayerHandle("Alice"))))

    clock.now = 1_500L
    val room = queue
      .heartbeat(
        RealtimeRoomHeartbeatCommand(
          roomId = Some(joined.roomId),
          ticketId = None,
          handle = Some(PlayerHandle(" ALICE "))
        )
      )
      .fold(error => fail(s"normalized heartbeat failed: $error"), value => value)

    assertEquals("heartbeat matches normalized handle", room.participants.map(_.lastSeen), Vector(EpochMillis(1_500L)))
  }

  private def sameHandleDifferentRequestUsesFreshWaitingRoom(): Unit = {
    val queue = queueService()
    val first = queue.join(joinCommand("Alice", "request-alice-1"))
    val second = queue.join(joinCommand("Alice", "request-alice-2"))

    assert(first.roomId != second.roomId, s"expected different rooms, got ${first.roomId} and ${second.roomId}")
    assertEquals("first room keeps one participant", queue.roomSnapshot(first.roomId).map(_.participants.length), Right(1))
    assertEquals("second room keeps one participant", queue.roomSnapshot(second.roomId).map(_.participants.length), Right(1))
  }

  private def sameQueueRequestAfterActiveRoomCreatesFreshTicket(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-stale-active-alice"))
    val bob = queue.join(joinCommand("Bob", "request-stale-active-bob"))

    clock.now = bob.startsAt.value
    val active = queue.status(alice.ticketId).fold(error => fail(s"missing active status: $error"), value => value)
    val retry = queue.join(joinCommand("Alice", "request-stale-active-alice"))

    assertEquals("old room is active", active.phase, MatchmakingRoomPhase.Active)
    assert(retry.ticketId != alice.ticketId, s"expected fresh ticket, got stale ${retry.ticketId}")
    assert(retry.roomId != alice.roomId, s"expected fresh room, got stale ${retry.roomId}")
    assertEquals("retry starts in waiting phase", retry.phase, MatchmakingRoomPhase.Waiting)
    assertEquals("retry has only Alice in the fresh room", retry.participants.map(_.handle.value), Vector("Alice"))
    assertEquals("old ticket remains active", queue.status(alice.ticketId).map(_.phase), Right(MatchmakingRoomPhase.Active))
  }

  private def sameQueueRequestAfterFinishedRoomCreatesFreshTicket(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-stale-finished-alice"))
    val bob = queue.join(joinCommand("Bob", "request-stale-finished-bob"))
    val finishedAt = EpochMillis(12_345L)

    clock.now = bob.startsAt.value
    queue.status(alice.ticketId).fold(error => fail(s"missing active status before finish: $error"), value => value)
    queue.markBattleFinished(alice.roomId, finishedAt)
    val retry = queue.join(joinCommand("Alice", "request-stale-finished-alice"))

    assert(retry.ticketId != alice.ticketId, s"expected fresh ticket, got stale ${retry.ticketId}")
    assert(retry.roomId != alice.roomId, s"expected fresh room, got stale ${retry.roomId}")
    assertEquals("retry starts waiting after finished room", retry.phase, MatchmakingRoomPhase.Waiting)
    assertEquals("retry has only Alice after finished room", retry.participants.map(_.handle.value), Vector("Alice"))
    assertEquals("old ticket remains finished", queue.status(alice.ticketId).map(_.phase), Right(MatchmakingRoomPhase.Finished))
    assertEquals("old ticket keeps finished timestamp", queue.status(alice.ticketId).map(_.finishedAt), Right(Some(finishedAt)))
  }

  private def leaveRemovesWaitingTicket(): Unit = {
    val queue = queueService()
    val snapshot = queue.join(joinCommand("Alice", "request-leave"))

    assertEquals("leave succeeds", queue.leave(snapshot.ticketId), BattleQueueLeaveOutcome.LeftQueue)
    assertEquals("status after leave is not found", queue.status(snapshot.ticketId), Left(BattleQueueStatusError.TicketNotFound))
    assertEquals("leaving again is idempotently not found", queue.leave(snapshot.ticketId), BattleQueueLeaveOutcome.TicketNotFound)
  }

  private def leaveDoesNotRemoveActiveTicket(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-active-leave-alice"))
    val bob = queue.join(joinCommand("Bob", "request-active-leave-bob"))

    clock.now = bob.startsAt.value
    assertEquals("active before leave", queue.status(alice.ticketId).map(_.phase), Right(MatchmakingRoomPhase.Active))
    assertEquals("active leave is not a queue leave", queue.leave(alice.ticketId), BattleQueueLeaveOutcome.NotWaiting)
    assertEquals("active ticket remains queryable", queue.status(alice.ticketId).map(_.phase), Right(MatchmakingRoomPhase.Active))
    assertEquals("active room still keeps both participants", queue.roomSnapshot(alice.roomId).map(_.participants.map(_.handle.value)), Right(Vector("Alice", "Bob")))
  }

  private def leaveDoesNotRemoveFinishedTicket(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-finished-leave-alice"))
    val bob = queue.join(joinCommand("Bob", "request-finished-leave-bob"))
    val finishedAt = EpochMillis(12_345L)

    clock.now = bob.startsAt.value
    queue.status(alice.ticketId).fold(error => fail(s"missing active status before finish: $error"), value => value)
    queue.markBattleFinished(alice.roomId, finishedAt)

    assertEquals("finished leave is not a queue leave", queue.leave(alice.ticketId), BattleQueueLeaveOutcome.NotWaiting)
    assertEquals("finished ticket remains queryable", queue.status(alice.ticketId).map(_.phase), Right(MatchmakingRoomPhase.Finished))
    assertEquals("finished timestamp remains", queue.status(alice.ticketId).map(_.finishedAt), Right(Some(finishedAt)))
    assertEquals("finished room still keeps both participants", queue.roomSnapshot(alice.roomId).map(_.participants.map(_.handle.value)), Right(Vector("Alice", "Bob")))
  }

  private def fullRoomWaitsForCountdownBeforeStarting(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-active-alice"))
    val bob = queue.join(joinCommand("Bob", "request-active-bob"))

    assertEquals("first snapshot was waiting", alice.phase, MatchmakingRoomPhase.Waiting)
    assertEquals("full room still waits for countdown", bob.phase, MatchmakingRoomPhase.Waiting)
    assertEquals("waiting full room has two participants", bob.participants.map(_.handle.value), Vector("Alice", "Bob"))
    assertEquals("waiting full room has no battle session", bob.battleSession.nonEmpty, false)

    clock.now = bob.startsAt.value
    val active = queue.status(bob.ticketId).fold(error => fail(s"missing active status: $error"), value => value)

    assertEquals("deadline activates room", active.phase, MatchmakingRoomPhase.Active)
    assertEquals("active room has battle session", active.battleSession.nonEmpty, true)
    assertEquals("battle session starts at scheduled countdown", active.battleSession.map(_.startedAt), Some(bob.startsAt))
    assertEquals(
      "battle session bootstrap seats are both humans",
      active.battleSession.flatMap(_.bootstrap).map(_.seats.map(_.isBot)),
      Some(Vector(false, false))
    )
  }

  private def deadlineStartsBattleWithLegacyBotProfiles(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(
      capacity = BattleCapacity(4),
      matchmakingDuration = DurationMillis(5_000L),
      currentTimeMillis = clock.millis
    )
    val alice = queue.join(joinCommand("Alice", "request-bots-alice"))

    assertEquals("single participant waits for deadline", alice.phase, MatchmakingRoomPhase.Waiting)

    clock.now = 6_000L
    val active = queue.status(alice.ticketId).fold(error => fail(s"missing active status: $error"), value => value)
    val seats = active.battleSession
      .flatMap(_.bootstrap)
      .map(_.seats)
      .getOrElse(fail("missing bootstrap seats"))
    val botSeats = seats.filter(_.isBot)
    val expectedProfiles = DemoBotProfiles.all.take(3)

    assertEquals("deadline activates room", active.phase, MatchmakingRoomPhase.Active)
    assertEquals("bootstrap seat bot flags", seats.map(_.isBot), Vector(false, true, true, true))
    assertEquals("legacy bot player ids", botSeats.map(_.playerId.value), Vector("bot-seat-1", "bot-seat-2", "bot-seat-3"))
    assertEquals("legacy bot hero ids", botSeats.map(_.heroId.value), Vector("bot-1", "bot-2", "bot-3"))
    assertEquals("legacy bot handles", botSeats.map(_.handle), expectedProfiles.map(_.handle))
    assertEquals("legacy bot display names", botSeats.map(_.displayName), expectedProfiles.map(_.displayName))
    assertEquals("legacy bot ratings", botSeats.map(_.rating.map(_.value)), expectedProfiles.map(profile => Some(profile.initialRating.value)))
    assertEquals("legacy bot avatars", botSeats.map(_.avatar), expectedProfiles.map(profile => Some(profile.skin.avatarKey.value)))
    assertEquals("legacy bot skins", botSeats.map(_.skin), expectedProfiles.map(profile => Some(profile.skin.avatarKey.value)))
    assertEquals("legacy bot joinedAt", botSeats.map(_.joinedAt), Vector(EpochMillis(0L), EpochMillis(0L), EpochMillis(0L)))
  }

  private def battleIdUsesInjectedGenerator(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(
      capacity = BattleCapacity(1),
      currentTimeMillis = clock.millis,
      newBattleId = fixedBattleIds("battle-from-generator")
    )
    val alice = queue.join(joinCommand("Alice", "request-generated-battle-id"))

    clock.now = alice.startsAt.value
    val active = queue.status(alice.ticketId).fold(error => fail(s"missing generated-id active status: $error"), value => value)

    assertEquals("battle id comes from injected generator", active.battleSession.map(_.battleId), Some(BattleId("battle-from-generator")))
  }

  private def freshServiceInstancesDoNotReuseBattleIdForSameRoomCounter(): Unit = {
    val firstClock = TestClock(1_000L)
    val secondClock = TestClock(1_000L)
    val firstQueue = queueService(
      capacity = BattleCapacity(1),
      currentTimeMillis = firstClock.millis,
      newBattleId = fixedBattleIds("battle-service-one")
    )
    val secondQueue = queueService(
      capacity = BattleCapacity(1),
      currentTimeMillis = secondClock.millis,
      newBattleId = fixedBattleIds("battle-service-two")
    )
    val firstWaiting = firstQueue.join(joinCommand("Alice", "request-service-one"))
    val secondWaiting = secondQueue.join(joinCommand("Alice", "request-service-two"))

    firstClock.now = firstWaiting.startsAt.value
    secondClock.now = secondWaiting.startsAt.value
    val firstActive =
      firstQueue.status(firstWaiting.ticketId).fold(error => fail(s"missing first active status: $error"), value => value)
    val secondActive =
      secondQueue.status(secondWaiting.ticketId).fold(error => fail(s"missing second active status: $error"), value => value)
    val firstBattleId = firstActive.battleSession.map(_.battleId).getOrElse(fail("missing first battle session"))
    val secondBattleId = secondActive.battleSession.map(_.battleId).getOrElse(fail("missing second battle session"))

    assertEquals("fresh services restart their room counters", firstWaiting.roomId, secondWaiting.roomId)
    assert(firstBattleId != secondBattleId, s"expected distinct battle ids, got $firstBattleId")
    assert(firstBattleId.value != s"battle-${firstWaiting.roomId.value}", s"first battle id was derived from room id: $firstBattleId")
    assert(secondBattleId.value != s"battle-${secondWaiting.roomId.value}", s"second battle id was derived from room id: $secondBattleId")
  }

  private def markBattleFinishedUpdatesStatusAndRoomSnapshot(): Unit = {
    val clock = TestClock(1_000L)
    val queue = queueService(capacity = BattleCapacity(2), currentTimeMillis = clock.millis)
    val alice = queue.join(joinCommand("Alice", "request-finish-alice"))
    val bob = queue.join(joinCommand("Bob", "request-finish-bob"))
    val finishedAt = EpochMillis(12_345L)

    clock.now = bob.startsAt.value
    queue.status(bob.ticketId).fold(error => fail(s"missing active status before finish: $error"), value => value)
    queue.markBattleFinished(bob.roomId, finishedAt)

    val status = queue.status(alice.ticketId).fold(error => fail(s"missing status after finish: $error"), value => value)
    val room = queue.roomSnapshot(bob.roomId).fold(error => fail(s"missing room after finish: $error"), value => value)
    assertEquals("finished status phase", status.phase, MatchmakingRoomPhase.Finished)
    assertEquals("finished status timestamp", status.finishedAt, Some(finishedAt))
    assertEquals("finished room phase", room.phase, MatchmakingRoomPhase.Finished)
    assertEquals("finished room timestamp", room.finishedAt, Some(finishedAt))
    assertEquals("finished room keeps battle session", room.battleSession.nonEmpty, true)
  }

  private def queueService(
    capacity: BattleCapacity = BattleCapacity(4),
    matchmakingDuration: DurationMillis = DurationMillis(5_000L),
    currentTimeMillis: () => Long = () => 1_000L,
    newBattleId: () => BattleId = sequentialBattleIds()
  ): InMemoryBattleQueueService =
    new InMemoryBattleQueueService(
      capacity = capacity,
      matchmakingDuration = matchmakingDuration,
      currentTimeMillis = currentTimeMillis,
      newBattleId = newBattleId
    )

  private def joinCommand(handle: String, requestId: String): BattleQueueJoinCommand =
    BattleQueueJoinCommand(
      handle = PlayerHandle(handle),
      sessionToken = SessionToken(s"session-${handle.toLowerCase}"),
      queueRequestId = Some(QueueRequestId(requestId)),
      rating = Some(Rating(1200)),
      avatar = Some("avatar"),
      skin = Some("blue")
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)

  private def fixedBattleIds(values: String*): () => BattleId = {
    var remaining = values.toVector
    () =>
      remaining match {
        case head +: tail =>
          remaining = tail
          BattleId(head)
        case _ =>
          fail("fixed battle id generator exhausted")
      }
  }

  private def sequentialBattleIds(prefix: String = "battle-contract"): () => BattleId = {
    var next = 1L
    () => {
      val battleId = BattleId(f"$prefix-$next%06d")
      next += 1L
      battleId
    }
  }

  private final case class TestClock(var now: Long) {
    def millis(): Long = now
  }
}
