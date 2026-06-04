package services.battle.microservices.extraction.api.state

import io.circe.{Encoder, Json}
import io.circe.syntax.*
import services.battle.microservices.extraction.objects.extraction.*
import services.battle.objects.core.BattleVector2

object BattleExtractionStateResponse {
  private given Encoder[BattleVector2] =
    Encoder.forProduct2("x", "y")((response: BattleVector2) => (response.x, response.y))

  given Encoder[BattleGasZoneState] =
    Encoder.instance(response =>
      Json.obj(
        "phase" -> BattleGasPhase.wireValue(response.phase).asJson,
        "center" -> response.center.asJson,
        "radius" -> response.radius.value.asJson,
        "nextRadius" -> response.nextRadius.value.asJson,
        "damagePerSecond" -> response.damagePerSecond.value.asJson,
        "stageIndex" -> response.stageIndex.value.asJson,
        "progressMs" -> response.progressMs.value.asJson,
        "startsAtMs" -> response.startsAt.value.asJson,
        "endsAtMs" -> response.endsAt.value.asJson
      )
    )

  given Encoder[BattleExtractionZoneDefinition] =
    Encoder.instance(response =>
      Json.obj(
        "zoneId" -> response.zoneId.value.asJson,
        "position" -> response.position.asJson,
        "radius" -> response.radius.value.asJson,
        "availableFromMs" -> response.availableFrom.value.asJson,
        "channelDurationMs" -> response.channelDuration.value.asJson
      )
    )

  given Encoder[BattleExtractionStatus] =
    Encoder.instance {
      case BattleExtractionStatus.Inactive =>
        Json.obj("status" -> BattleExtractionStatus.wireValue(BattleExtractionStatus.Inactive).asJson)
      case BattleExtractionStatus.Available =>
        Json.obj("status" -> BattleExtractionStatus.wireValue(BattleExtractionStatus.Available).asJson)
      case BattleExtractionStatus.Extracting(playerId, heroId, zoneId, progressMs) =>
        Json.obj(
          "status" -> "extracting".asJson,
          "playerId" -> playerId.value.asJson,
          "heroId" -> heroId.value.asJson,
          "zoneId" -> zoneId.value.asJson,
          "progressMs" -> progressMs.value.asJson
        )
      case BattleExtractionStatus.Extracted(playerId, heroId, zoneId, atElapsedMs) =>
        Json.obj(
          "status" -> "extracted".asJson,
          "playerId" -> playerId.value.asJson,
          "heroId" -> heroId.value.asJson,
          "zoneId" -> zoneId.value.asJson,
          "atElapsedMs" -> atElapsedMs.value.asJson
        )
      case BattleExtractionStatus.Interrupted(playerId, heroId, zoneId, reason, atElapsedMs) =>
        Json.obj(
          "status" -> "interrupted".asJson,
          "playerId" -> playerId.value.asJson,
          "heroId" -> heroId.value.asJson,
          "zoneId" -> zoneId.value.asJson,
          "reason" -> BattleExtractionInterruptReason.wireValue(reason).asJson,
          "atElapsedMs" -> atElapsedMs.value.asJson
        )
    }

  given Encoder[BattleExtractionState] =
    Encoder.forProduct2("zones", "status")((response: BattleExtractionState) => (response.zones, response.status))

  given Encoder[BattleLootCacheStatus] =
    Encoder.instance {
      case BattleLootCacheStatus.Available =>
        Json.obj("status" -> BattleLootCacheStatus.wireValue(BattleLootCacheStatus.Available).asJson)
      case BattleLootCacheStatus.Searching(playerId, heroId, progressMs) =>
        Json.obj(
          "status" -> "searching".asJson,
          "playerId" -> playerId.value.asJson,
          "heroId" -> heroId.value.asJson,
          "progressMs" -> progressMs.value.asJson
        )
      case BattleLootCacheStatus.Searched(playerId, heroId, atElapsedMs) =>
        Json.obj(
          "status" -> "searched".asJson,
          "playerId" -> playerId.value.asJson,
          "heroId" -> heroId.value.asJson,
          "atElapsedMs" -> atElapsedMs.value.asJson
        )
    }

  given Encoder[BattleLootCacheState] =
    Encoder.instance(response =>
      Json.obj(
        "cacheId" -> response.cacheId.value.asJson,
        "position" -> response.position.asJson,
        "radius" -> response.radius.value.asJson,
        "searchDurationMs" -> response.searchDuration.value.asJson,
        "scoreValue" -> response.scoreValue.value.asJson,
        "status" -> response.status.asJson
      )
    )
}
