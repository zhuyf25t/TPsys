package services.battle.microservices.actors.api

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import services.battle.microservices.runtime.objects.command.BattleCommandVector

object BattlePlayerInputAPIEncoding {
  given commandVectorDecoder: Decoder[BattleCommandVector] =
    deriveDecoder[BattleCommandVector].emap { value =>
      Either.cond(value.x.isFinite && value.y.isFinite, value, "invalid_battle_command_vector")
    }
}
