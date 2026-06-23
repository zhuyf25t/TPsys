package services.battle.microservices.runtime.api

import cats.effect.IO

import services.battle.microservices.runtime.objects.command.{BattleCommandAccepted, BattleCommandRequest}
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.{BattleAggregateState, BattleId}
import system.api.{APIMessageError, UnsupportedAPIConnection}
import system.objects.UserId

enum BattleRuntimeChannelStateReadError {
  case BattleNotFound
}

object BattleRuntimeChannelAPIMessagePlanner {
  private val PublicBattleCompatibilityUserId: UserId =
    UserId("public-battle-compatibility")

  def readPublicState(
    stateService: BattleStateService,
    battleId: BattleId
  ): IO[Either[BattleRuntimeChannelStateReadError, BattleAggregateState]] =
    BattleStateReadAPIMessage(PublicBattleCompatibilityUserId, battleId)
      .plan(stateService, UnsupportedAPIConnection.create)
      .attempt
      .flatMap {
        case Right(state) =>
          IO.pure(Right(state))
        case Left(APIMessageError.NotFound(_)) =>
          IO.pure(Left(BattleRuntimeChannelStateReadError.BattleNotFound))
        case Left(error) =>
          IO.raiseError(error)
      }

  def submitCompatibilityCommand(
    stateService: BattleStateService,
    command: BattleCommandRequest
  ): IO[BattleCommandAccepted] =
    BattleCommandCompatibilityAPIMessage
      .fromCommandRequest(command)
      .plan(stateService, UnsupportedAPIConnection.create)
}
