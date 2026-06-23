package services.battle.microservices.queue.api

import services.battle.microservices.queue.api.queue.{
  BattleQueueJoinAPIContext,
  BattleQueueJoinAPIMessage,
  BattleQueueLeaveAPIEncoding,
  BattleQueueLeaveAPIMessage,
  BattleQueueSnapshotAPIEncoding,
  BattleQueueStatusAPIMessage
}
import services.battle.microservices.queue.api.room.{
  BattleRoomHeartbeatAPIMessage,
  BattleRoomSnapshotAPIEncoding,
  BattleRoomSnapshotAPIMessage
}
import services.battle.microservices.queue.objects.queue.{
  BattleQueueLeaveOutcome,
  BattleQueueSnapshot,
  RealtimeRoomSnapshot
}
import services.battle.microservices.queue.services.{BattleQueueJoinAuthorizationService, BattleQueueService}
import system.api.RegisteredAPIMessage
import system.api.RegisteredAPIMessage.apiWithTokenAndContext

object BattleQueueAPIMessages {
  import BattleQueueLeaveAPIEncoding.given
  import BattleQueueSnapshotAPIEncoding.given
  import BattleRoomSnapshotAPIEncoding.given

  def messages(
    queueService: BattleQueueService,
    authorizationService: BattleQueueJoinAuthorizationService
  ): List[RegisteredAPIMessage] =
    List(
      apiWithTokenAndContext[
        BattleQueueJoinAPIContext,
        BattleQueueJoinAPIMessage,
        BattleQueueSnapshot
      ](
        context = BattleQueueJoinAPIContext(
          queueService = queueService,
          authorizationService = authorizationService
        ),
        decodeFailure = BattleQueueJoinAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleQueueStatusAPIMessage,
        BattleQueueSnapshot
      ](
        context = queueService,
        decodeFailure = BattleQueueStatusAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleQueueLeaveAPIMessage,
        BattleQueueLeaveOutcome
      ](
        context = queueService,
        decodeFailure = BattleQueueLeaveAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleRoomSnapshotAPIMessage,
        RealtimeRoomSnapshot
      ](
        context = queueService,
        decodeFailure = BattleRoomSnapshotAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleRoomHeartbeatAPIMessage,
        RealtimeRoomSnapshot
      ](
        context = queueService,
        decodeFailure = BattleRoomHeartbeatAPIMessage.requestDecodeFailure
      )
    )
}
