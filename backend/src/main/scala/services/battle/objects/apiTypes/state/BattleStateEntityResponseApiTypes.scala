package services.battle.objects.apiTypes.state

import io.circe.Encoder
import services.battle.objects.{BattleEventKind, PickupKind, WeaponKind}
import services.battle.objects.event.{BattleEventParticipant, BattleEventState}
import services.battle.objects.pickup.BattlePickupState
import services.battle.objects.skill.BattleSlowFieldState

import BattleStateVectorResponse.given

object BattleStateSlowFieldResponse {
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

object BattleStateEventParticipantResponse {
  given Encoder[BattleEventParticipant] =
    Encoder.forProduct3("playerId", "heroId", "displayName")((response: BattleEventParticipant) =>
      (response.playerId.value, response.heroId.value, response.displayName.value)
    )
}

object BattleStateEventResponse {
  import BattleStateEventParticipantResponse.given

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

object BattleStatePickupResponse {
  given Encoder[BattlePickupState] =
    Encoder
      .forProduct6("pickupId", "kind", "position", "available", "respawnMs", "weaponKind")(
        (response: BattlePickupState) =>
          (
            response.pickupId.value,
            PickupKind.wireValue(response.pickupKind),
            response.position,
            response.available,
            response.respawnMs.value,
            response.weaponKind.map(WeaponKind.wireValue)
          )
      )
      .mapJson(_.dropNullValues)
}
