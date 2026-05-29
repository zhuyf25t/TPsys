package services.battle.microservices.session.api.state

import io.circe.{Decoder, DecodingFailure}

import services.battle.objects.core.BattleId

object BattleStateReadAPIRequest {
  given Decoder[BattleStateReadQuery] =
    Decoder.instance { cursor =>
      cursor.get[String]("battleId").flatMap { value =>
        Option(value).map(_.trim).filter(_.nonEmpty) match {
          case Some(battleId) =>
            Right(BattleStateReadQuery(BattleId(battleId)))
          case None =>
            Left(DecodingFailure("battleId is required.", cursor.history))
        }
      }
    }
}
