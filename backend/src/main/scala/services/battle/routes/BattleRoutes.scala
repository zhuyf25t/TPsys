package services.battle.routes

import services.battle.microservices.session.api.command.BattleCommandAPIMessage
import services.battle.microservices.queue.api.queue.{
  BattleQueueJoinAPIContext,
  BattleQueueJoinAPIMessage,
  BattleQueueLeaveAPIMessage,
  BattleQueueStatusAPIMessage
}
import services.battle.microservices.results.api.{BattleResultListAPIMessage, BattleResultRecordAPIMessage}
import services.battle.microservices.queue.api.room.{BattleRoomHeartbeatAPIMessage, BattleRoomSnapshotAPIMessage}
import services.battle.microservices.session.api.state.BattleStateReadAPIMessage
import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.session.services.BattleStateService
import services.battle.microservices.session.objects.command.BattleCommandAccepted
import services.battle.objects.core.BattleAggregateState
import services.battle.microservices.queue.objects.queue.{BattleQueueLeaveOutcome, BattleQueueSnapshot, RealtimeRoomSnapshot}
import services.battle.microservices.session.api.command.BattleCommandAcceptedResponse.given
import services.battle.microservices.queue.api.queue.BattleQueueSnapshotResponse.given
import services.battle.microservices.queue.api.queue.BattleQueueLeaveResponse.given
import services.battle.microservices.results.api.results.{BattleResultListResponse, BattleResultRecordResponse}
import services.battle.microservices.results.api.results.BattleResultRecordResponse.given
import services.battle.microservices.results.api.results.BattleResultListResponse.given
import services.battle.microservices.queue.api.room.RealtimeRoomSnapshotResponse.given
import services.battle.microservices.session.api.state.BattleStateRootResponse.given
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
