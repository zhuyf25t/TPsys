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
import services.battle.database.queue.BattleQueueService
import services.battle.database.results.{BattleResultRepository, BattleResultStorage}
import services.battle.database.session.BattleStateService
import services.battle.objects.BattleQueueLeaveOutcome
import services.battle.objects.command.BattleCommandAccepted
import services.battle.objects.core.BattleAggregateState
import services.battle.objects.queue.{BattleQueueSnapshot, RealtimeRoomSnapshot}
import services.battle.objects.result.{BattleResultList, BattleResultRecord}
import services.battle.objects.apiTypes.command.BattleCommandAcceptedResponse.given
import services.battle.objects.apiTypes.queue.BattleQueueSnapshotResponse.given
import services.battle.objects.apiTypes.queue.BattleQueueLeaveResponse.given
import services.battle.objects.apiTypes.results.BattleResultRecordResponse.given
import services.battle.objects.apiTypes.results.BattleResultListResponse.given
import services.battle.objects.apiTypes.room.RealtimeRoomSnapshotResponse.given
import services.battle.objects.apiTypes.state.BattleStateRootResponse.given
import system.api.RegisteredAPIMessage
import system.api.RegisteredAPIMessage.{apiWithToken, apiWithTokenAndContext}

enum BattleResultAPIRegistration {
  case ConnectionBacked
  case RepositoryBacked(resultRepository: BattleResultRepository)
}

object BattleRoutes {
  val connectionBackedResultApiMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[
        BattleResultListAPIMessage,
        BattleResultList
      ],
      apiWithToken[
        BattleResultRecordAPIMessage,
        BattleResultRecord
      ]
    )

  def apiMessages(
    context: BattleAPIRuntimeContext,
    resultRegistration: BattleResultAPIRegistration
  ): List[RegisteredAPIMessage] =
    serviceInjectedRuntimeApiMessages(context) ++ resultApiMessages(resultRegistration)

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

  private def resultApiMessages(
    resultRegistration: BattleResultAPIRegistration
  ): List[RegisteredAPIMessage] =
    resultRegistration match {
      case BattleResultAPIRegistration.ConnectionBacked =>
        connectionBackedResultApiMessages
      case BattleResultAPIRegistration.RepositoryBacked(resultRepository) =>
        resultApiMessages(BattleResultStorage.Repository(resultRepository))
    }

  private def resultApiMessages(storage: BattleResultStorage): List[RegisteredAPIMessage] =
    List(
      apiWithTokenAndContext[
        BattleResultStorage,
        BattleResultListAPIMessage,
        BattleResultList
      ](
        context = storage,
        decodeFailure = BattleResultListAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleResultStorage,
        BattleResultRecordAPIMessage,
        BattleResultRecord
      ](
        context = storage,
        decodeFailure = BattleResultRecordAPIMessage.requestDecodeFailure
      )
    )
}
