package services.battle.objects.apiTypes.state

import io.circe.Encoder
import services.battle.objects.core.BattleVector2

object BattleStateVectorResponse {
  given Encoder[BattleVector2] =
    Encoder.forProduct2("x", "y")((response: BattleVector2) => (response.x, response.y))
}
