package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle, SessionToken}

enum BattleQueueStatusError {
  case TicketNotFound
}

enum BattleRoomError {
  case MissingRoomId
  case RoomNotFound
}

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case TicketNotFound
}

trait BattleRoomLifecycleSink {
  def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit
}

object NoopBattleRoomLifecycleSink extends BattleRoomLifecycleSink {
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit = ()
}

trait BattleQueueService extends BattleSessionLookup with BattleRoomLifecycleSink {
  def join(command: BattleQueueJoinCommand): BattleQueueSnapshot
  def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot]
  def leave(ticketId: TicketId): BattleQueueLeaveOutcome
  def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot]
  def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot]
}

final case class BattleQueueJoinCommand(
  handle: PlayerHandle,
  sessionToken: SessionToken,
  queueRequestId: Option[QueueRequestId],
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class RealtimeRoomHeartbeatCommand(
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle]
)

final class InMemoryBattleQueueService(
  capacity: BattleCapacity,
  matchmakingDuration: DurationMillis,
  currentTimeMillis: () => Long
) extends BattleQueueService {
  private val lock = Object()
  private var rooms: Map[RoomId, QueueRoom] = Map.empty
  private var tickets: Map[TicketId, TicketRecord] = Map.empty
  private var queueRequests: Map[QueueRequestId, TicketId] = Map.empty
  private var nextTicketNumber: Long = 1L
  private var nextRoomNumber: Long = 1L
  private var nextPlayerNumber: Long = 1L
  private val heroSlotIds: Vector[HeroId] =
    Vector("player-1", "bot-1", "bot-2", "bot-3", "bot-4", "bot-5").map(HeroId.apply)

  override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)

      command.queueRequestId.flatMap(queueRequests.get).flatMap(snapshotForTicket(_, now)) match {
        case Some(snapshot) =>
          snapshot
        case None =>
          val room = selectJoinRoom(command.handle, command.queueRequestId, now)
          val ticketId = nextTicketId()
          val playerId = nextPlayerId()
          val participant = BattleQueueParticipant(
            playerId = playerId,
            handle = command.handle,
            joinedAt = now,
            lastSeen = now,
            rating = command.rating,
            avatar = command.avatar.flatMap(normalizeOptionalText),
            skin = command.skin.flatMap(normalizeOptionalText)
          )
          val entry = QueueParticipantEntry(
            ticketId = ticketId,
            playerId = playerId,
            queueRequestId = command.queueRequestId,
            participant = participant
          )
          val updatedRoom = advanceRoom(room.copy(participants = room.participants :+ entry), now)
          rooms = rooms.updated(updatedRoom.roomId, updatedRoom)
          tickets = tickets.updated(
            ticketId,
            TicketRecord(
              ticketId = ticketId,
              playerId = playerId,
              roomId = updatedRoom.roomId,
              queueRequestId = command.queueRequestId
            )
          )
          command.queueRequestId.foreach(id => queueRequests = queueRequests.updated(id, ticketId))
          toQueueSnapshot(updatedRoom, entry, now)
      }
    }
  }

  override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      snapshotForTicket(ticketId, now).toRight(BattleQueueStatusError.TicketNotFound)
    }
  }

  override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
    lock.synchronized {
      tickets.get(ticketId) match {
        case None =>
          BattleQueueLeaveOutcome.TicketNotFound
        case Some(record) =>
          tickets = tickets.removed(ticketId)
          record.queueRequestId.foreach(id => queueRequests = queueRequests.removed(id))
          rooms.get(record.roomId).foreach { room =>
            val updatedParticipants = room.participants.filterNot(_.ticketId == ticketId)
            if updatedParticipants.isEmpty && room.phase == MatchmakingRoomPhase.Waiting then
              rooms = rooms.removed(record.roomId)
            else
              rooms = rooms.updated(record.roomId, room.copy(participants = updatedParticipants))
          }
          BattleQueueLeaveOutcome.LeftQueue
      }
    }

  override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      rooms.get(roomId).map(toRoomSnapshot(_, now)).toRight(BattleRoomError.RoomNotFound)
    }
  }

  override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      val roomId = request.roomId.orElse(request.ticketId.flatMap(tickets.get).map(_.roomId))
      roomId match {
        case None =>
          Left(BattleRoomError.MissingRoomId)
        case Some(id) =>
          rooms.get(id) match {
            case None =>
              Left(BattleRoomError.RoomNotFound)
            case Some(room) =>
              val updatedRoom = updateHeartbeat(room, request, now)
              rooms = rooms.updated(updatedRoom.roomId, updatedRoom)
              Right(toRoomSnapshot(updatedRoom, now))
          }
      }
      }
    }

  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
    lock.synchronized {
      rooms.get(roomId).foreach { room =>
        val nextRoom = room.copy(
          phase = MatchmakingRoomPhase.Finished,
          finishedAt = room.finishedAt.orElse(Some(finishedAt))
        )
        rooms = rooms.updated(roomId, nextRoom)
      }
    }

  override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      rooms.valuesIterator.flatMap { room =>
        room.battleSession.filter(_.battleId == battleId).map { session =>
          BattleSessionSeed(
            roomId = room.roomId,
            descriptor = session.copy(serverTime = now),
            commandOwnership = room.participants.map(entry => BattleCommandOwnership(entry.playerId, entry.ticketId))
          )
        }
      }.toVector.headOption
    }
  }

  private def selectJoinRoom(handle: PlayerHandle, queueRequestId: Option[QueueRequestId], now: EpochMillis): QueueRoom = {
    val openRooms = rooms.values
      .filter(room => room.phase == MatchmakingRoomPhase.Waiting && room.participants.length < room.capacity.value)
      .toVector
      .sortBy(_.createdAt.value)

    val shouldStartFreshRoom = queueRequestId.exists(_ =>
      openRooms.exists(room => room.participants.exists(entry => sameHandleKey(entry.participant.handle, handle.key)))
    )

    if shouldStartFreshRoom then createRoom(now)
    else openRooms.headOption.getOrElse(createRoom(now))
  }

  private def createRoom(now: EpochMillis): QueueRoom = {
    val roomId = nextRoomId()
    val room = QueueRoom(
      roomId = roomId,
      createdAt = now,
      startsAt = EpochMillis(now.value + matchmakingDuration.value),
      deadline = EpochMillis(now.value + matchmakingDuration.value),
      participants = Vector.empty,
      capacity = capacity,
      durationMs = matchmakingDuration,
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )
    rooms = rooms.updated(roomId, room)
    room
  }

  private def advanceRooms(now: EpochMillis): Unit =
    rooms = rooms.view.mapValues(room => advanceRoom(room, now)).toMap

  private def advanceRoom(room: QueueRoom, now: EpochMillis): QueueRoom =
    if room.phase == MatchmakingRoomPhase.Waiting &&
      room.participants.nonEmpty &&
      now.value >= room.deadline.value
    then startRoom(room, now)
    else room

  private def startRoom(room: QueueRoom, now: EpochMillis): QueueRoom = {
    val roster = room.participants.zipWithIndex.map { case (entry, index) =>
      val participant = entry.participant
      BattleSessionRosterEntry(
        seat = SeatIndex(index),
        playerId = entry.playerId,
        handle = participant.handle,
        joinedAt = participant.joinedAt,
        rating = participant.rating,
        avatar = participant.avatar,
        skin = participant.skin
      )
    }
    val humanSeats = room.participants.zipWithIndex.map { case (entry, index) =>
      val participant = entry.participant
      BattleSessionBootstrapSeat(
        seat = SeatIndex(index),
        playerId = entry.playerId,
        heroId = HeroId(s"hero-${entry.playerId.value}"),
        handle = participant.handle,
        displayName = DisplayName(participant.handle.value),
        joinedAt = participant.joinedAt,
        isBot = false,
        spawnPointIndex = SpawnPointIndex(index),
        rating = participant.rating,
        avatar = participant.avatar,
        skin = participant.skin
      )
    }
    val botSeats =
      (room.participants.length until room.capacity.value).toVector.map(buildBotSeat)
    val session = BattleSessionDescriptor(
      battleId = BattleId(s"battle-${room.roomId.value}"),
      startedAt = room.startsAt,
      serverTime = now,
      roster = roster,
      capacity = room.capacity,
      bootstrap = Some(BattleSessionBootstrap(humanSeats ++ botSeats))
    )

    room.copy(phase = MatchmakingRoomPhase.Active, battleSession = Some(session))
  }

  private def buildBotSeat(index: Int): BattleSessionBootstrapSeat = {
    val profile = Option.when(index > 0)(index - 1).flatMap(DemoBotProfiles.all.lift)
    val handle = profile.map(_.handle).getOrElse(PlayerHandle(s"Bot $index"))
    val avatar = profile.map(_.skin.avatarKey.value)

    BattleSessionBootstrapSeat(
      seat = SeatIndex(index),
      playerId = PlayerId(s"bot-seat-$index"),
      heroId = heroSlotIds.lift(index).getOrElse(HeroId(s"bot-$index")),
      handle = handle,
      displayName = profile.map(_.displayName).getOrElse(DisplayName(handle.value)),
      joinedAt = EpochMillis(0L),
      isBot = true,
      spawnPointIndex = SpawnPointIndex(index),
      rating = profile.map(profile => Rating(profile.initialRating.value)),
      avatar = avatar,
      skin = avatar
    )
  }

  private def snapshotForTicket(ticketId: TicketId, now: EpochMillis): Option[BattleQueueSnapshot] =
    for
      record <- tickets.get(ticketId)
      room <- rooms.get(record.roomId)
      entry <- room.participants.find(_.ticketId == ticketId)
    yield toQueueSnapshot(room, entry, now)

  private def toQueueSnapshot(
    room: QueueRoom,
    entry: QueueParticipantEntry,
    now: EpochMillis
  ): BattleQueueSnapshot =
    BattleQueueSnapshot(
      ticketId = entry.ticketId,
      playerId = entry.playerId,
      roomId = room.roomId,
      createdAt = entry.participant.joinedAt,
      startsAt = room.startsAt,
      deadline = room.deadline,
      serverTime = now,
      participants = room.participants.map(_.participant),
      capacity = room.capacity,
      durationMs = room.durationMs,
      phase = room.phase,
      finishedAt = room.finishedAt,
      battleSession = room.battleSession.map(_.copy(serverTime = now))
    )

  private def toRoomSnapshot(room: QueueRoom, now: EpochMillis): RealtimeRoomSnapshot =
    RealtimeRoomSnapshot(
      roomId = room.roomId,
      serverTime = now,
      participants = room.participants.map(_.participant),
      capacity = room.capacity,
      phase = room.phase,
      finishedAt = room.finishedAt,
      battleSession = room.battleSession.map(_.copy(serverTime = now))
    )

  private def updateHeartbeat(
    room: QueueRoom,
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): QueueRoom = {
    val participants = room.participants.map { entry =>
      val ticketMatches = request.ticketId.contains(entry.ticketId)
      val handleMatches = request.handle.exists(handle => sameHandleKey(entry.participant.handle, handle.key))

      if ticketMatches || handleMatches then
        entry.copy(participant = entry.participant.copy(lastSeen = now))
      else entry
    }

    room.copy(participants = participants)
  }

  private def nextTicketId(): TicketId = {
    val id = TicketId(f"ticket-$nextTicketNumber%06d")
    nextTicketNumber += 1L
    id
  }

  private def nextRoomId(): RoomId = {
    val id = RoomId(f"room-$nextRoomNumber%06d")
    nextRoomNumber += 1L
    id
  }

  private def nextPlayerId(): PlayerId = {
    val id = PlayerId(f"player-$nextPlayerNumber%06d")
    nextPlayerNumber += 1L
    id
  }

  private def normalizeQueueRequestId(value: String): Option[QueueRequestId] =
    normalizeOptionalText(value).map(QueueRequestId.apply)

  private def normalizeOptionalText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def sameHandleKey(left: PlayerHandle, rightKey: String): Boolean =
    left.key == rightKey

  private final case class QueueRoom(
    roomId: RoomId,
    createdAt: EpochMillis,
    startsAt: EpochMillis,
    deadline: EpochMillis,
    participants: Vector[QueueParticipantEntry],
    capacity: BattleCapacity,
    durationMs: DurationMillis,
    phase: MatchmakingRoomPhase,
    finishedAt: Option[EpochMillis],
    battleSession: Option[BattleSessionDescriptor]
  )

  private final case class QueueParticipantEntry(
    ticketId: TicketId,
    playerId: PlayerId,
    queueRequestId: Option[QueueRequestId],
    participant: BattleQueueParticipant
  )

  private final case class TicketRecord(
    ticketId: TicketId,
    playerId: PlayerId,
    roomId: RoomId,
    queueRequestId: Option[QueueRequestId]
  )
}

object InMemoryBattleQueueService {
  val DefaultCapacity: BattleCapacity = BattleCapacity(6)
  val DefaultMatchmakingDuration: DurationMillis = DurationMillis(5_000L)

  def apply(): InMemoryBattleQueueService =
    new InMemoryBattleQueueService(
      capacity = DefaultCapacity,
      matchmakingDuration = DefaultMatchmakingDuration,
      currentTimeMillis = () => System.currentTimeMillis()
    )
}
