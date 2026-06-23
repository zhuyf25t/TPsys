package services.battle.microservices.runtime.api

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.{BattleAggregateState, BattleId}
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleStateReadAPIMessage(
  userId: UserId,
  battleId: BattleId
) extends APIWithTokenContextMessage[BattleStateService, BattleAggregateState] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleAggregateState] =
    BattleStateReadAPIPlanner.plan(stateService, this)
}

object BattleStateReadAPIMessage {
  private given Decoder[BattleId] =
    Decoder.decodeString.emap { value =>
      Option(value).map(_.trim).filter(_.nonEmpty).map(BattleId.apply).toRight("battleId is required.")
    }

  private given Decoder[UserId] =
    Decoder.decodeString.emap { value =>
      Option(value).map(_.trim).filter(_.nonEmpty).map(UserId.apply).toRight("Login is required.")
    }

  given Decoder[BattleStateReadAPIMessage] =
    deriveDecoder[BattleStateReadAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleRuntimeAPIMessageErrors.stateReadDecodeFailure(error)
}
