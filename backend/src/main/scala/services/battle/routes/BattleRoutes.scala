package services.battle.routes

import services.battle.microservices.queue.api.BattleQueueAPIMessages
import services.battle.microservices.results.api.BattleResultAPIMessages
import services.battle.microservices.runtime.api.BattleRuntimeAPIMessages
import system.api.RegisteredAPIMessage

object BattleRoutes {
  val connectionBackedResultApiMessages: List[RegisteredAPIMessage] =
    BattleResultAPIMessages.connectionBackedMessages

  def apiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    queueApiMessages(context) ++ runtimeApiMessages(context) ++ resultApiMessages

  def commandCompatibilityApiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    BattleRuntimeAPIMessages.commandCompatibilityMessages(context.stateService)

  def queueApiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    BattleQueueAPIMessages.messages(context.queueService, context.joinAuthorizationService)

  def runtimeApiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage] =
    BattleRuntimeAPIMessages.messages(context.stateService)

  private def resultApiMessages: List[RegisteredAPIMessage] =
    connectionBackedResultApiMessages
}
