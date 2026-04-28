package slaydemo.backend.battle.services

import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

import slaydemo.backend.battle.api.BattleQueueJoinRequest
import slaydemo.backend.battle.objects.{
  BattleQueueParticipant,
  BattleQueueSnapshot,
  BattleSessionBootstrap,
  BattleSessionBootstrapSeat,
  BattleSessionDescriptor,
  BattleSessionRosterEntry,
  RealtimeRoomSnapshot
}
import slaydemo.backend.bots.objects.DemoBotProfiles
import slaydemo.backend.shared.objects.UserId

trait BattleQueueService {
  def join(request: BattleQueueJoinRequest): Either[String, BattleQueueSnapshot]
  def status(ticketId: String): Option[BattleQueueSnapshot]
  def leave(ticketId: String): Boolean
  def roomSnapshot(roomId: String): Option[RealtimeRoomSnapshot]
  def heartbeat(roomId: String, ticketId: Option[String], handle: Option[String]): Option[RealtimeRoomSnapshot]
}

final class InMemoryBattleQueueService(
  battleService: BattleService,
  capacity: Int = 6,
  durationMs: Long = 10_000L,
  retentionMs: Long = 60_000L
) extends BattleQueueService {
  private val heroSlotIds = Vector("player-1", "bot-1", "bot-2", "bot-3", "bot-4", "bot-5")

  private final case class QueuedParticipant(
    ticketId: String,
    queueRequestId: Option[String],
    participant: BattleQueueParticipant
  )
  private final case class QueueRoomPublicStatus(phase: String, finishedAt: Option[Long])
  private final case class StoredBattleSession(
    battleId: String,
    startedAt: Long,
    roster: Vector[BattleSessionRosterEntry],
    bootstrap: BattleSessionBootstrap
  )
  private final case class QueueRoom(
    roomId: String,
    createdAt: Long,
    startsAt: Long,
    participants: Vector[QueuedParticipant],
    battleSession: Option[StoredBattleSession]
  )

  private val lock = new AnyRef
  private var rooms: Vector[QueueRoom] = Vector.empty
  private var tickets: Map[String, String] = Map.empty

  override def join(request: BattleQueueJoinRequest): Either[String, BattleQueueSnapshot] = {
    val handle = request.handle.trim
    if (handle.isEmpty) {
      Left("invalid_handle")
    } else {
      lock.synchronized {
        val now = System.currentTimeMillis()
        cleanup(now)
        val handleKey = normalizeHandle(handle)
        val queueRequestId = normalizeOptional(request.queueRequestId)

        findOpenRoomByParticipantIdentity(handleKey, queueRequestId, now) match {
          case Some((queueRoom, queuedParticipant)) =>
            val nextRoom = touchParticipant(queueRoom, queuedParticipant.ticketId, now)
            replaceRoom(nextRoom)
            tickets = tickets + (queuedParticipant.ticketId -> nextRoom.roomId)
            Right(snapshot(queuedParticipant.ticketId, nextRoom, now))
          case None =>
            val queueRoom = findOpenRoom(handleKey, queueRequestId, now).getOrElse(createRoom(now))
            val ticketId = s"ticket-${UUID.randomUUID().toString}"
            val playerId = buildHumanPlayerId(queueRoom.roomId, ticketId)
            val participant = QueuedParticipant(
              ticketId,
              queueRequestId,
              BattleQueueParticipant(
                playerId = playerId,
                handle = handle,
                joinedAt = now,
                lastSeen = now,
                rating = request.rating,
                avatar = normalizeOptional(request.avatar),
                skin = normalizeOptional(request.skin)
              )
            )
            val nextRoom = queueRoom.copy(participants = queueRoom.participants :+ participant)
            replaceRoom(nextRoom)
            tickets = tickets + (ticketId -> nextRoom.roomId)
            Right(snapshot(ticketId, nextRoom, now))
        }
      }
    }
  }

  override def status(ticketId: String): Option[BattleQueueSnapshot] = {
    val normalizedTicket = ticketId.trim
    if (normalizedTicket.isEmpty) {
      None
    } else {
      lock.synchronized {
        val now = System.currentTimeMillis()
        cleanup(now)
        tickets
          .get(normalizedTicket)
          .flatMap(roomId => rooms.find(_.roomId == roomId))
          .map { queueRoom =>
            val nextRoom = ensureBattleSession(touchParticipant(queueRoom, normalizedTicket, now), now)
            replaceRoom(nextRoom)
            snapshot(normalizedTicket, nextRoom, now)
          }
      }
    }
  }

  override def leave(ticketId: String): Boolean = {
    val normalizedTicket = ticketId.trim
    if (normalizedTicket.isEmpty) {
      false
    } else {
      lock.synchronized {
        tickets.get(normalizedTicket) match {
          case Some(roomId) =>
            tickets = tickets - normalizedTicket
            rooms = rooms.flatMap { queueRoom =>
              if (queueRoom.roomId != roomId) {
                Some(queueRoom)
              } else {
                val nextParticipants = queueRoom.participants.filterNot(_.ticketId == normalizedTicket)
                if (nextParticipants.isEmpty) None else Some(queueRoom.copy(participants = nextParticipants))
              }
            }
            true
          case None =>
            false
        }
      }
    }
  }

  override def roomSnapshot(roomId: String): Option[RealtimeRoomSnapshot] = {
    val normalizedRoomId = roomId.trim
    if (normalizedRoomId.isEmpty) {
      None
    } else {
      lock.synchronized {
        val now = System.currentTimeMillis()
        cleanup(now)
        rooms.find(_.roomId == normalizedRoomId).map { room =>
          val nextRoom = ensureBattleSession(room, now)
          replaceRoom(nextRoom)
          realtimeSnapshot(nextRoom, now)
        }
      }
    }
  }

  override def heartbeat(
    roomId: String,
    ticketId: Option[String],
    handle: Option[String]
  ): Option[RealtimeRoomSnapshot] = {
    val normalizedRoomId = roomId.trim
    if (normalizedRoomId.isEmpty) {
      None
    } else {
      lock.synchronized {
        val now = System.currentTimeMillis()
        cleanup(now)
        rooms.find(_.roomId == normalizedRoomId).map { room =>
          val nextRoom = ensureBattleSession(
            touchParticipant(room, normalizeOptional(ticketId), normalizeOptional(handle), now),
            now
          )
          replaceRoom(nextRoom)
          realtimeSnapshot(nextRoom, now)
        }
      }
    }
  }

  private def findOpenRoom(handleKey: String, queueRequestId: Option[String], now: Long): Option[QueueRoom] =
    rooms.find { queueRoom =>
      isJoinableRoom(queueRoom, now) &&
        queueRoom.participants.size < capacity &&
        !hasConflictingFreshParticipant(queueRoom, handleKey, queueRequestId)
    }

  private def findOpenRoomByParticipantIdentity(
    handleKey: String,
    queueRequestId: Option[String],
    now: Long
  ): Option[(QueueRoom, QueuedParticipant)] =
    rooms
      .filter(isJoinableRoom(_, now))
      .flatMap { queueRoom =>
        queueRoom.participants
          .find(queued => isSameQueueParticipant(queued, handleKey, queueRequestId))
          .map(queueRoom -> _)
      }
      .headOption

  private def isSameQueueParticipant(
    queuedParticipant: QueuedParticipant,
    handleKey: String,
    queueRequestId: Option[String]
  ): Boolean =
    normalizeHandle(queuedParticipant.participant.handle) == handleKey &&
      queuedParticipant.queueRequestId == queueRequestId

  private def hasConflictingFreshParticipant(
    queueRoom: QueueRoom,
    handleKey: String,
    queueRequestId: Option[String]
  ): Boolean =
    queueRequestId.nonEmpty &&
      queueRoom.participants.exists { queuedParticipant =>
        normalizeHandle(queuedParticipant.participant.handle) == handleKey &&
          queuedParticipant.queueRequestId != queueRequestId
      }

  private def isJoinableRoom(queueRoom: QueueRoom, now: Long): Boolean =
    queueRoom.battleSession.isEmpty && queueRoom.startsAt > now

  private def createRoom(now: Long): QueueRoom = {
    val queueRoom = QueueRoom(
      roomId = s"room-${UUID.randomUUID().toString}",
      createdAt = now,
      startsAt = now + durationMs,
      participants = Vector.empty,
      battleSession = None
    )
    rooms = rooms :+ queueRoom
    queueRoom
  }

  private def replaceRoom(nextRoom: QueueRoom): Unit = {
    rooms = rooms.map(queueRoom => if (queueRoom.roomId == nextRoom.roomId) nextRoom else queueRoom)
  }

  private def touchParticipant(queueRoom: QueueRoom, ticketId: String, now: Long): QueueRoom = {
    val nextParticipants = queueRoom.participants.map { queued =>
      if (queued.ticketId == ticketId) {
        queued.copy(participant = queued.participant.copy(lastSeen = now))
      } else {
        queued
      }
    }

    queueRoom.copy(participants = nextParticipants)
  }

  private def touchParticipant(
    queueRoom: QueueRoom,
    ticketId: Option[String],
    handle: Option[String],
    now: Long
  ): QueueRoom = {
    if (ticketId.isEmpty && handle.isEmpty) {
      queueRoom
    } else {
      val nextParticipants = queueRoom.participants.map { queued =>
        val ticketMatches = ticketId.contains(queued.ticketId)
        val handleMatches = ticketId.isEmpty && handle.contains(queued.participant.handle)
        if (ticketMatches || handleMatches) {
          queued.copy(participant = queued.participant.copy(lastSeen = now))
        } else {
          queued
        }
      }

      queueRoom.copy(participants = nextParticipants)
    }
  }

  private def cleanup(now: Long): Unit = {
    val retainedRooms = rooms.filter(shouldRetainRoom(_, now))
    val retainedRoomIds = retainedRooms.map(_.roomId).toSet
    val expiredRoomIds = rooms.map(_.roomId).toSet -- retainedRoomIds
    expiredRoomIds.foreach(battleService.releaseRoom)
    rooms = retainedRooms
    tickets = tickets.filter { case (_, roomId) => retainedRoomIds.contains(roomId) }
  }

  private def shouldRetainRoom(queueRoom: QueueRoom, now: Long): Boolean =
    if (withinRetention(queueRoom.startsAt, now)) {
      true
    } else {
      queueRoom.battleSession match {
        case None =>
          false
        case Some(_) =>
          battleService.roomStatus(queueRoom.roomId, now) match {
            case Some(BattleRoomStatus("active", _)) =>
              true
            case Some(BattleRoomStatus("finished", Some(finishedAt))) =>
              withinRetention(finishedAt, now)
            case _ =>
              false
          }
      }
    }

  private def withinRetention(anchor: Long, now: Long): Boolean =
    now <= anchor + retentionMs

  private def snapshot(ticketId: String, queueRoom: QueueRoom, now: Long): BattleQueueSnapshot = {
    val playerId = queueRoom.participants
      .find(_.ticketId == ticketId)
      .map(_.participant.playerId)
      .getOrElse(buildHumanPlayerId(queueRoom.roomId, ticketId))

    val publicStatus = currentPublicStatus(queueRoom, now)

    BattleQueueSnapshot(
      ticketId = ticketId,
      playerId = playerId,
      roomId = queueRoom.roomId,
      createdAt = queueRoom.createdAt,
      startsAt = queueRoom.startsAt,
      deadline = queueRoom.startsAt,
      participants = queueRoom.participants.map(_.participant),
      capacity = capacity,
      durationMs = durationMs,
      phase = publicStatus.phase,
      finishedAt = publicStatus.finishedAt,
      battleSession = renderBattleSession(queueRoom, now)
    )
  }

  private def realtimeSnapshot(queueRoom: QueueRoom, now: Long): RealtimeRoomSnapshot = {
    val publicStatus = currentPublicStatus(queueRoom, now)

    RealtimeRoomSnapshot(
      roomId = queueRoom.roomId,
      serverTime = now,
      participants = queueRoom.participants.map(_.participant),
      capacity = capacity,
      phase = publicStatus.phase,
      finishedAt = publicStatus.finishedAt,
      battleSession = renderBattleSession(queueRoom, now)
    )
  }

  private def ensureBattleSession(queueRoom: QueueRoom, now: Long): QueueRoom =
    if (now < queueRoom.startsAt || queueRoom.battleSession.nonEmpty) {
      queueRoom
    } else {
      val storedSession = StoredBattleSession(
        battleId = s"battle-${UUID.randomUUID().toString}",
        startedAt = queueRoom.startsAt,
        roster = queueRoom.participants.zipWithIndex.map { case (queued, index) =>
          BattleSessionRosterEntry(
            seat = index,
            playerId = queued.participant.playerId,
            handle = queued.participant.handle,
            joinedAt = queued.participant.joinedAt,
            rating = queued.participant.rating,
            avatar = queued.participant.avatar,
            skin = queued.participant.skin
          )
        },
        bootstrap = buildBattleBootstrap(queueRoom.participants)
      )
      battleService.initializeRoomBattle(
        queueRoom.roomId,
        BattleSessionDescriptor(
          battleId = storedSession.battleId,
          startedAt = storedSession.startedAt,
          serverTime = now,
          roster = storedSession.roster,
          capacity = capacity,
          bootstrap = storedSession.bootstrap
        ),
        commandOwnership = queueRoom.participants.map { queued =>
          BattleCommandOwnership(
            playerId = UserId(queued.participant.playerId),
            ticketId = queued.ticketId
          )
        }
      )
      queueRoom.copy(
        battleSession = Some(storedSession)
      )
    }

  private def renderBattleSession(queueRoom: QueueRoom, now: Long): Option[BattleSessionDescriptor] =
    queueRoom.battleSession.map { descriptor =>
      BattleSessionDescriptor(
        battleId = descriptor.battleId,
        startedAt = descriptor.startedAt,
        serverTime = now,
        roster = descriptor.roster,
        capacity = capacity,
        bootstrap = descriptor.bootstrap
      )
    }

  private def currentPublicStatus(queueRoom: QueueRoom, now: Long): QueueRoomPublicStatus =
    if (now < queueRoom.startsAt || queueRoom.battleSession.isEmpty) {
      QueueRoomPublicStatus("waiting", None)
    } else {
      battleService.roomStatus(queueRoom.roomId, now) match {
        case Some(BattleRoomStatus("finished", finishedAt)) =>
          QueueRoomPublicStatus("finished", finishedAt)
        case _ =>
          QueueRoomPublicStatus("active", None)
      }
    }

  private def buildBattleBootstrap(participants: Vector[QueuedParticipant]): BattleSessionBootstrap = {
    val humanSeats = participants.zipWithIndex.map { case (queued, index) =>
      BattleSessionBootstrapSeat(
        seat = index,
        playerId = queued.participant.playerId,
        heroId = heroSlotId(index),
        handle = queued.participant.handle,
        displayName = queued.participant.handle,
        joinedAt = queued.participant.joinedAt,
        isBot = false,
        spawnPointIndex = index,
        rating = queued.participant.rating,
        avatar = queued.participant.avatar,
        skin = queued.participant.skin
      )
    }
    val botSeats = (participants.size until capacity).flatMap(buildBotBootstrapSeat)

    BattleSessionBootstrap(
      seats = (humanSeats ++ botSeats).sortBy(_.seat)
    )
  }

  private def buildBotBootstrapSeat(seat: Int): Option[BattleSessionBootstrapSeat] = {
    val profileIndex = seat - 1
    if (profileIndex < 0) {
      None
    } else {
      DemoBotProfiles.all.lift(profileIndex).map { profile =>
        BattleSessionBootstrapSeat(
          seat = seat,
          playerId = s"bot-seat-$seat",
          heroId = heroSlotId(seat),
          handle = profile.handle,
          displayName = profile.displayName,
          joinedAt = 0L,
          isBot = true,
          spawnPointIndex = seat,
          rating = Some(profile.initialRating),
          avatar = Some(profile.skin.avatarKey),
          skin = Some(profile.skin.avatarKey)
        )
      }
    }
  }

  private def heroSlotId(seat: Int): String =
    heroSlotIds.lift(seat).getOrElse(s"bot-${seat}")

  private def buildHumanPlayerId(roomId: String, ticketId: String): String =
    s"player-${UUID.nameUUIDFromBytes(s"${roomId}:${ticketId}".getBytes(StandardCharsets.UTF_8)).toString}"

  private def normalizeOptional(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)

  private def normalizeHandle(value: String): String =
    value.trim.toLowerCase(Locale.ROOT)
}
