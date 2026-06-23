package services.battle.microservices.abilities.api

import io.circe.Encoder
import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.microservices.actors.objects.player.BattlePlayerSkillState

object BattleSkillStateAPIEncoding {
  given Encoder[BattlePlayerSkillState] =
    Encoder.forProduct3("kind", "cooldownMs", "activeMs")((response: BattlePlayerSkillState) =>
      (SkillKind.wireValue(response.skillKind), response.cooldownMs.value, response.activeMs.value)
    )
}
