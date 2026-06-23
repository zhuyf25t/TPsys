package services.battle.microservices.abilities.api

import io.circe.Encoder
import services.battle.microservices.abilities.objects.skill.BattleSlowFieldState
import services.battle.microservices.world.api.BattleVectorAPIEncoding.given

object BattleSlowFieldStateAPIEncoding {
  given Encoder[BattleSlowFieldState] =
    Encoder.forProduct7("fieldId", "ownerPlayerId", "ownerHeroId", "position", "radius", "ttlMs", "durationMs")(
      (response: BattleSlowFieldState) =>
        (
          response.fieldId.value,
          response.ownerPlayerId.value,
          response.ownerHeroId.value,
          response.position,
          response.radius.value,
          response.ttlMs.value,
          response.durationMs.value
        )
    )
}
