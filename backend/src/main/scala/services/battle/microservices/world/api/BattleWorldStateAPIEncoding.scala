package services.battle.microservices.world.api

import io.circe.Encoder
import services.battle.objects.core.BattleMapId

object BattleWorldStateAPIEncoding {
  given Encoder[BattleMapId] =
    Encoder.encodeString.contramap(_.value)
}
