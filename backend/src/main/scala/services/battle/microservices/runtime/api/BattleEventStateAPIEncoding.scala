package services.battle.microservices.runtime.api

import io.circe.Encoder
import services.battle.microservices.runtime.objects.event.{BattleEventKind, BattleEventParticipant, BattleEventState}

object BattleEventParticipantAPIEncoding {
  given Encoder[BattleEventParticipant] =
    Encoder.forProduct3("playerId", "heroId", "displayName")((response: BattleEventParticipant) =>
      (response.playerId.value, response.heroId.value, response.displayName.value)
    )
}

object BattleEventStateAPIEncoding {
  import BattleEventParticipantAPIEncoding.given

  given Encoder[BattleEventState] =
    Encoder.forProduct7("eventId", "type", "kind", "elapsedMs", "message", "source", "target")(
      (response: BattleEventState) =>
        (
          response.eventId.value,
          BattleEventKind.wireValue(response.eventKind),
          BattleEventKind.wireValue(response.eventKind),
          response.elapsedMs.value,
          response.message,
          response.source,
          response.target
        )
    )
}
