package slaydemo.backend

import slaydemo.backend.battle.objects.{
  BattleCapacity,
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
  InMemoryBattleQueueService
}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

object BattleQueueRuntimeContractTest {
  def main(args: Array[String]): Unit = {
    sameQueueRequestIsIdempotent()
    sameHandleDifferentRequestUsesFreshWaitingRoom()
    leaveRemovesWaitingTicket()
    fullRoomWaitsForCountdownBeforeStarting()
    deadlineStartsBattleWithLegacyBotProfiles()
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

  private def sameHandleDifferentRequestUsesFreshWaitingRoom(): Unit = {
    val queue = queueService()
    val first = queue.join(joinCommand("Alice", "request-alice-1"))
    val second = queue.join(joinCommand("Alice", "request-alice-2"))

    assert(first.roomId != second.roomId, s"expected different rooms, got ${first.roomId} and ${second.roomId}")
    assertEquals("first room keeps one participant", queue.roomSnapshot(first.roomId).map(_.participants.length), Right(1))
    assertEquals("second room keeps one participant", queue.roomSnapshot(second.roomId).map(_.participants.length), Right(1))
  }

  private def leaveRemovesWaitingTicket(): Unit = {
    val queue = queueService()
    val snapshot = queue.join(joinCommand("Alice", "request-leave"))

    assertEquals("leave succeeds", queue.leave(snapshot.ticketId), BattleQueueLeaveOutcome.LeftQueue)
    assertEquals("status after leave is not found", queue.status(snapshot.ticketId), Left(BattleQueueStatusError.TicketNotFound))
    assertEquals("leaving again is idempotently not found", queue.leave(snapshot.ticketId), BattleQueueLeaveOutcome.TicketNotFound)
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
    currentTimeMillis: () => Long = () => 1_000L
  ): InMemoryBattleQueueService =
    new InMemoryBattleQueueService(
      capacity = capacity,
      matchmakingDuration = matchmakingDuration,
      currentTimeMillis = currentTimeMillis
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

  private final case class TestClock(var now: Long) {
    def millis(): Long = now
  }
}
