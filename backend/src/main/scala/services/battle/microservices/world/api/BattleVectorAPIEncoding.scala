package services.battle.microservices.world.api

import io.circe.Encoder
import services.battle.objects.core.BattleVector2

object BattleVectorAPIEncoding {
  given Encoder[BattleVector2] =
    Encoder.forProduct2("x", "y")((response: BattleVector2) => (response.x, response.y))
}
