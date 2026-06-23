package services.battle.microservices.runtime.api

import cats.effect.IO

import services.battle.microservices.runtime.objects.command.{BattleCommandAccepted, BattleCommandRequest}
import services.battle.microservices.session.services.BattleStateService

object BattleCommandAPIPlanner {
  def plan(stateService: BattleStateService, message: BattleCommandAPIMessage): IO[BattleCommandAccepted] =
    submitCommand(stateService, message.toCommandRequest)

  def submitCommand(
    stateService: BattleStateService,
    command: BattleCommandRequest
  ): IO[BattleCommandAccepted] =
    for
      result <- stateService.acceptCommand(command)
      accepted <- BattleRuntimeAPIMessageErrors.commandSubmit(result)
    yield accepted
}
