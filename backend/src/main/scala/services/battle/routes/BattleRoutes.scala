package services.battle.routes

import services.battle.api.command.BattleCommandAPIMessage
import services.battle.api.queue.{
  BattleQueueJoinAPIContext,
  BattleQueueJoinAPIMessage,
  BattleQueueLeaveAPIMessage,
  BattleQueueStatusAPIMessage
}
import services.battle.api.results.{BattleResultListAPIMessage, BattleResultRecordAPIMessage}
import services.battle.api.room.{BattleRoomHeartbeatAPIMessage, BattleRoomSnapshotAPIMessage}
import services.battle.api.state.BattleStateReadAPIMessage
import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.BattleQueueLeaveOutcome
import services.battle.objects.command.BattleCommandAccepted
import services.battle.objects.core.BattleAggregateState
import services.battle.objects.queue.{BattleQueueSnapshot, RealtimeRoomSnapshot}
import services.battle.objects.apiTypes.command.BattleCommandAcceptedResponse.given
import services.battle.objects.apiTypes.queue.BattleQueueSnapshotResponse.given
import services.battle.objects.apiTypes.queue.BattleQueueLeaveResponse.given
import services.battle.objects.apiTypes.results.{BattleResultListResponse, BattleResultRecordResponse}
import services.battle.objects.apiTypes.results.BattleResultRecordResponse.given
import services.battle.objects.apiTypes.results.BattleResultListResponse.given
import services.battle.objects.apiTypes.room.RealtimeRoomSnapshotResponse.given
import services.battle.objects.apiTypes.state.BattleStateRootResponse.given
import system.api.RegisteredAPIMessage
import system.api.RegisteredAPIMessage.{apiWithToken, apiWithTokenAndContext}

object BattleRoutes {
  val connectionBackedResultApiMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[
        BattleResultListAPIMessage,
        BattleResultListResponse
      ],
      apiWithToken[
        BattleResultRecordAPIMessage,
        BattleResultRecordResponse
      ]
    )

  def apiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    serviceInjectedRuntimeApiMessages(context) ++ resultApiMessages

  private def serviceInjectedRuntimeApiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    List(
      apiWithTokenAndContext[
        BattleQueueJoinAPIContext,
        BattleQueueJoinAPIMessage,
        BattleQueueSnapshot
      ](
        context = BattleQueueJoinAPIContext(
          queueService = context.queueService,
          authorizationService = context.joinAuthorizationService
        ),
        decodeFailure = BattleQueueJoinAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleQueueStatusAPIMessage,
        BattleQueueSnapshot
      ](
        context = context.queueService,
        decodeFailure = BattleQueueStatusAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleQueueLeaveAPIMessage,
        BattleQueueLeaveOutcome
      ](
        context = context.queueService,
        decodeFailure = BattleQueueLeaveAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleRoomSnapshotAPIMessage,
        RealtimeRoomSnapshot
      ](
        context = context.queueService,
        decodeFailure = BattleRoomSnapshotAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleQueueService,
        BattleRoomHeartbeatAPIMessage,
        RealtimeRoomSnapshot
      ](
        context = context.queueService,
        decodeFailure = BattleRoomHeartbeatAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleStateService,
        BattleStateReadAPIMessage,
        BattleAggregateState
      ](
        context = context.stateService,
        decodeFailure = BattleStateReadAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleStateService,
        BattleCommandAPIMessage,
        BattleCommandAccepted
      ](
        context = context.stateService,
        decodeFailure = BattleCommandAPIMessage.requestDecodeFailure
      )
    )

  private def resultApiMessages: List[RegisteredAPIMessage] =
    connectionBackedResultApiMessages
}
