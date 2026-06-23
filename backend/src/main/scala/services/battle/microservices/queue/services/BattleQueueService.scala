package services.battle.microservices.queue.services

import cats.effect.{IO, Ref, Resource}

import BattleQueueSnapshots.{toQueueSnapshot, toRoomSnapshot}
import services.battle.microservices.queue.objects.queue.*
import services.battle.microservices.session.services.{BattleSessionSeed, RandomBattleIdGenerator}
import services.battle.objects.BattleMode
import services.battle.objects.core.{
  BattleId,
  DurationMillis,
  EpochMillis,
  PlayerId,
  RoomId
}
import services.identity.objects.PlayerHandle

final class InMemoryBattleQueueService private (
  capacity: BattleCapacity,
  matchmakingDuration: DurationMillis,
  currentTimeMillis: () => Long,
  newBattleId: () => IO[BattleId],
  runtimeState: Ref[IO, QueueRuntimeState]
) extends BattleQueueService {
  /** 中文名：加入队列（join）。游戏职责：把玩家加入等待房间，并在房间满足条件时生成战斗会话。 */
  override def join(command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
    for
      now <- currentTime
      snapshot <- updateAdvancedState(now) { advancedState =>
        for
          normalizedCommand <- BattleQueueJoinRules.normalizeCommand(command)
          reuseResult <- reuseWaitingQueueRequestOrForgetStale(advancedState, normalizedCommand.queueRequestId, normalizedCommand.battleMode, now)
          (reusedSnapshot, stateAfterReuse) = reuseResult
          result <- reusedSnapshot match {
            case Some(snapshot) =>
              IO.pure(stateAfterReuse -> snapshot)
            case None =>
              for
                selected <- selectJoinRoom(stateAfterReuse, normalizedCommand.handle, normalizedCommand.battleMode, normalizedCommand.queueRequestId, now)
                (room, stateWithRoom) = selected
                ticket <- nextTicketId(stateWithRoom)
                (ticketId, stateAfterTicket) = ticket
                player <- nextPlayerId(stateAfterTicket)
                (playerId, stateAfterPlayer) = player
                draft <- BattleQueueJoinRules.draft(normalizedCommand, room, ticketId, playerId, now)
                updatedRoom <- advanceRoom(draft.room, now)
                queueRequests <- BattleQueueJoinRules.queueRequestsAfterJoin(stateAfterPlayer.queueRequests, normalizedCommand, ticketId)
                snapshot <- toQueueSnapshot(updatedRoom, draft.entry, now)
                stateWithRoom <- stateAfterPlayer.withRoom(updatedRoom)
                stateWithTicket <- stateWithRoom.withTickets(stateAfterPlayer.tickets.updated(ticketId, draft.ticket))
                nextState <- stateWithTicket.withQueueRequests(queueRequests)
              yield nextState -> snapshot
          }
        yield result
      }
    yield snapshot

  /** 中文名：查询排队状态（status）。游戏职责：按 ticketId 返回等待房间和即将进入战斗的快照。 */
  override def status(ticketId: TicketId): IO[Either[BattleQueueStatusError, BattleQueueSnapshot]] =
    for
      now <- currentTime
      snapshot <- updateAdvancedState(now) { advancedState =>
        BattleQueueTicketSnapshots
          .snapshotForTicket(advancedState.tickets, advancedState.rooms, ticketId, now)
          .map(snapshot => advancedState -> snapshot)
      }
    yield snapshot.toRight(BattleQueueStatusError.TicketNotFound)

  /** 中文名：离开队列（leave）。游戏职责：从等待房间移除 ticket，并返回离队结果 ADT。 */
  override def leave(ticketId: TicketId): IO[BattleQueueLeaveOutcome] =
    updateState { currentState =>
      for
        transition <- BattleQueueLeaveRules.leave(currentState.rooms, currentState.tickets, currentState.queueRequests, ticketId)
        stateWithRooms <- currentState.withRooms(transition.rooms)
        stateWithTickets <- stateWithRooms.withTickets(transition.tickets)
        nextState <- stateWithTickets.withQueueRequests(transition.queueRequests)
      yield nextState -> transition.outcome
    }

  /** 中文名：房间快照（roomSnapshot）。游戏职责：按 roomId 返回等待区房间当前状态。 */
  override def roomSnapshot(roomId: RoomId): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    for
      now <- currentTime
      result <- updateAdvancedState(now) { advancedState =>
        roomSnapshotResult(advancedState, roomId, now).map(result => advancedState -> result)
      }
    yield result

  /** 中文名：房间心跳（heartbeat）。游戏职责：刷新等待区玩家在线状态，并返回最新房间快照。 */
  override def heartbeat(request: RealtimeRoomHeartbeatCommand): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    for
      now <- currentTime
      result <-
        if request.startGateAction == BattleRoomStartGateAction.Pause then heartbeatWithPausePriority(request, now)
        else updateAdvancedState(now) { advancedState =>
          heartbeatTransition(advancedState, request, now)
        }
    yield result

  /** 中文名：标记战斗结束（markBattleFinished）。游戏职责：把已启动战斗对应的等待房间推进到 Finished ADT 状态。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] =
    updateState { currentState =>
      BattleQueueRoomLifecycleRules
        .markFinished(currentState.rooms, roomId, finishedAt)
        .flatMap(rooms => currentState.withRooms(rooms).map(_ -> ()))
    }

  /** 中文名：读取活动战斗会话（activeBattleSession）。游戏职责：让战斗运行时按 battleId 找到已启动的 session seed。 */
  override def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]] =
    for
      now <- currentTime
      session <- updateAdvancedState(now) { advancedState =>
        BattleQueueSessionLookupRules
          .activeBattleSession(advancedState.rooms.values, battleId, now)
          .map(session => advancedState -> session)
      }
    yield session

  private def roomSnapshotResult(
    state: QueueRuntimeState,
    roomId: RoomId,
    now: EpochMillis
  ): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    state.rooms.get(roomId) match {
      case None       => IO.pure(Left(BattleRoomError.RoomNotFound))
      case Some(room) => toRoomSnapshot(room, now).map(Right(_))
    }

  private def heartbeatTransition(
    state: QueueRuntimeState,
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): IO[(QueueRuntimeState, Either[BattleRoomError, RealtimeRoomSnapshot])] =
    BattleQueueHeartbeatRules.roomIdForHeartbeat(state.tickets, request).flatMap {
      case None =>
        IO.pure(state -> Left(BattleRoomError.MissingRoomId))
      case Some(id) =>
        state.rooms.get(id) match {
          case None =>
            IO.pure(state -> Left(BattleRoomError.RoomNotFound))
          case Some(room) =>
            for
              updatedRoom <- BattleQueueHeartbeatRules.updateHeartbeat(room, request, now)
              snapshot <- toRoomSnapshot(updatedRoom, now)
              nextState <- state.withRoom(updatedRoom)
            yield nextState -> Right(snapshot)
        }
    }

  private def heartbeatWithPausePriority(
    request: RealtimeRoomHeartbeatCommand,
    now: EpochMillis
  ): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    updateState { currentState =>
      for
        heartbeatResult <- heartbeatTransition(currentState, request, now)
        (stateAfterHeartbeat, resultAfterHeartbeat) = heartbeatResult
        advancedState <- advanceRooms(stateAfterHeartbeat, now)
        refreshedResult <- resultAfterHeartbeat match {
          case Left(error) =>
            IO.pure(Left(error))
          case Right(snapshot) =>
            roomSnapshotResult(advancedState, snapshot.roomId, now)
        }
      yield advancedState -> refreshedResult
    }

  private def selectJoinRoom(
    state: QueueRuntimeState,
    handle: PlayerHandle,
    battleMode: BattleMode,
    queueRequestId: Option[QueueRequestId],
    now: EpochMillis
  ): IO[(QueueRoom, QueueRuntimeState)] =
    for
      openRooms <- BattleQueueRoomSelectionRules.openWaitingRooms(state.rooms.values)
      reusable <- BattleQueueRoomSelectionRules.reusableRoom(openRooms, handle, battleMode, queueRequestId)
      selected <- reusable match {
        case Some(room) => IO.pure(room -> state)
        case None       => createRoom(state, now, battleMode)
      }
    yield selected

  private def createRoom(state: QueueRuntimeState, now: EpochMillis, battleMode: BattleMode): IO[(QueueRoom, QueueRuntimeState)] =
    for
      allocated <- nextRoomId(state)
      (roomId, stateAfterRoomId) = allocated
      room <- BattleQueueRoomLifecycleRules.newWaitingRoom(roomId, battleMode, now, matchmakingDuration, BattleQueueCapacityRules.capacityFor(battleMode, capacity))
      nextState <- stateAfterRoomId.withRoom(room)
    yield room -> nextState

  private def currentTime: IO[EpochMillis] =
    IO.blocking(EpochMillis(currentTimeMillis()))

  private def updateState[A](transition: QueueRuntimeState => IO[(QueueRuntimeState, A)]): IO[A] =
    runtimeState.access.flatMap { case (currentState, setState) =>
      transition(currentState).flatMap { case (nextState, result) =>
        setState(nextState).flatMap {
          case true  => IO.pure(result)
          case false => updateState(transition)
        }
      }
    }

  private def updateAdvancedState[A](
    now: EpochMillis
  )(transition: QueueRuntimeState => IO[(QueueRuntimeState, A)]): IO[A] =
    updateState { currentState =>
      advanceRooms(currentState, now).flatMap(transition)
    }

  private def advanceRooms(state: QueueRuntimeState, now: EpochMillis): IO[QueueRuntimeState] =
    state.rooms.foldLeft(IO.pure(Map.empty[RoomId, QueueRoom])) { case (updatedRoomsIO, (roomId, room)) =>
      for
        updatedRooms <- updatedRoomsIO
        updatedRoom <- advanceRoom(room, now)
      yield updatedRooms.updated(roomId, updatedRoom)
    }.flatMap(state.withRooms)

  private def advanceRoom(room: QueueRoom, now: EpochMillis): IO[QueueRoom] =
    BattleQueueRoomLifecycleRules.startDecision(room, now).flatMap {
      case QueueRoomStartDecision.Start =>
        newBattleId().flatMap(battleId => BattleQueueRoomLifecycleRules.startRoom(room, battleId, now))
      case QueueRoomStartDecision.Keep =>
        IO.pure(room)
    }

  private def reuseWaitingQueueRequestOrForgetStale(
    state: QueueRuntimeState,
    queueRequestId: Option[QueueRequestId],
    battleMode: BattleMode,
    now: EpochMillis
  ): IO[(Option[BattleQueueSnapshot], QueueRuntimeState)] =
    queueRequestId match {
      case None =>
        IO.pure(None -> state)
      case Some(id) =>
        BattleQueueRequestReuseRules
          .reuseWaitingRequest(
            queueRequests = state.queueRequests,
            tickets = state.tickets,
            rooms = state.rooms,
            queueRequestId = id,
            battleMode = battleMode,
            now = now
          )
          .flatMap(result => state.withQueueRequests(result.queueRequests).map(nextState => result.snapshot -> nextState))
    }

  private def nextTicketId(state: QueueRuntimeState): IO[(TicketId, QueueRuntimeState)] =
    state.idAllocator.allocateTicketId.map { case (id, nextAllocator) =>
      id -> state.copy(idAllocator = nextAllocator)
    }

  private def nextRoomId(state: QueueRuntimeState): IO[(RoomId, QueueRuntimeState)] =
    state.idAllocator.allocateRoomId.map { case (id, nextAllocator) =>
      id -> state.copy(idAllocator = nextAllocator)
    }

  private def nextPlayerId(state: QueueRuntimeState): IO[(PlayerId, QueueRuntimeState)] =
    state.idAllocator.allocatePlayerId.map { case (id, nextAllocator) =>
      id -> state.copy(idAllocator = nextAllocator)
    }
}

object InMemoryBattleQueueService {
  val DefaultCapacity: BattleCapacity = BattleCapacity(4)
  val DefaultMatchmakingDuration: DurationMillis = DurationMillis(5_000L)

  /** 中文名：创建内存队列服务（apply）。游戏职责：为本地运行时创建默认排队服务实例。 */
  def apply(): IO[InMemoryBattleQueueService] =
    create()

  def create(): IO[InMemoryBattleQueueService] =
    create(
      capacity = DefaultCapacity,
      matchmakingDuration = DefaultMatchmakingDuration,
      currentTimeMillis = () => System.currentTimeMillis(),
      newBattleId = () => RandomBattleIdGenerator.nextBattleId()
    )

  def create(
    capacity: BattleCapacity,
    matchmakingDuration: DurationMillis,
    currentTimeMillis: () => Long,
    newBattleId: () => IO[BattleId]
  ): IO[InMemoryBattleQueueService] =
    Ref.of[IO, QueueRuntimeState](QueueRuntimeState.initial).map { stateRef =>
      new InMemoryBattleQueueService(
        capacity = capacity,
        matchmakingDuration = matchmakingDuration,
        currentTimeMillis = currentTimeMillis,
        newBattleId = newBattleId,
        runtimeState = stateRef
      )
    }

  def resource(): Resource[IO, InMemoryBattleQueueService] =
    Resource.eval(create())
}

private[battle] object BattleQueueCapacityRules {
  private val WinterZombieCapacity: BattleCapacity = BattleCapacity(6)
  private val AutumnBotCapacity: BattleCapacity = BattleCapacity(8)

  def capacityFor(battleMode: BattleMode, defaultCapacity: BattleCapacity): BattleCapacity =
    battleMode match {
      case BattleMode.Autumn => AutumnBotCapacity
      case BattleMode.Winter => WinterZombieCapacity
      case _                 => defaultCapacity
    }
}
