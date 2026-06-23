package services.battle.microservices.runtime.api

import services.battle.microservices.runtime.objects.command.BattleCommandAccepted
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.BattleAggregateState
import system.api.RegisteredAPIMessage
import system.api.RegisteredAPIMessage.{apiWithContext, apiWithTokenAndContext}

object BattleRuntimeAPIMessages {
  import BattleAggregateStateAPIEncoding.given
  import BattleCommandAcceptedAPIEncoding.given

  def messages(stateService: BattleStateService): List[RegisteredAPIMessage] =
    List(
      apiWithTokenAndContext[
        BattleStateService,
        BattleStateReadAPIMessage,
        BattleAggregateState
      ](
        context = stateService,
        decodeFailure = BattleStateReadAPIMessage.requestDecodeFailure
      ),
      apiWithTokenAndContext[
        BattleStateService,
        BattleCommandAPIMessage,
        BattleCommandAccepted
      ](
        context = stateService,
        decodeFailure = BattleCommandAPIMessage.requestDecodeFailure
      )
    )

  def commandCompatibilityMessages(stateService: BattleStateService): List[RegisteredAPIMessage] =
    List(
      apiWithContext[
        BattleStateService,
        BattleCommandCompatibilityAPIMessage,
        BattleCommandAccepted
      ](
        context = stateService,
        decodeFailure = BattleCommandCompatibilityAPIMessage.requestDecodeFailure
      )
    )
}
