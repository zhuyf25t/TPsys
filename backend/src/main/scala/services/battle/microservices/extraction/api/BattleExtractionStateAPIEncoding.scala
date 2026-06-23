package services.battle.microservices.extraction.api

import io.circe.Encoder
import io.circe.syntax.*
import services.battle.microservices.extraction.objects.extraction.*
import services.battle.objects.core.BattleVector2

object BattleExtractionStateAPIEncoding {
  private given Encoder[BattleVector2] =
    Encoder.forProduct2("x", "y")((response: BattleVector2) => (response.x, response.y))

  given Encoder[BattleGasZoneState] =
    Encoder[BattleGasZoneStatePayload].contramap(response =>
      BattleGasZoneStatePayload(
        phase = BattleGasPhase.wireValue(response.phase),
        center = response.center,
        radius = response.radius.value,
        nextRadius = response.nextRadius.value,
        damagePerSecond = response.damagePerSecond.value,
        stageIndex = response.stageIndex.value,
        progressMs = response.progressMs.value,
        startsAtMs = response.startsAt.value,
        endsAtMs = response.endsAt.value
      )
    )

  given Encoder[BattleExtractionZoneDefinition] =
    Encoder[BattleExtractionZoneDefinitionPayload].contramap(response =>
      BattleExtractionZoneDefinitionPayload(
        zoneId = response.zoneId.value,
        position = response.position,
        radius = response.radius.value,
        availableFromMs = response.availableFrom.value,
        channelDurationMs = response.channelDuration.value
      )
    )

  given Encoder[BattleExtractionStatus] =
    Encoder.instance {
      case BattleExtractionStatus.Inactive =>
        StatusOnlyPayload(BattleExtractionStatus.wireValue(BattleExtractionStatus.Inactive)).asJson
      case BattleExtractionStatus.Available =>
        StatusOnlyPayload(BattleExtractionStatus.wireValue(BattleExtractionStatus.Available)).asJson
      case status @ BattleExtractionStatus.Extracting(playerId, heroId, zoneId, progressMs) =>
        BattleExtractionInProgressPayload(
          status = BattleExtractionStatus.wireValue(status),
          playerId = playerId.value,
          heroId = heroId.value,
          zoneId = zoneId.value,
          progressMs = progressMs.value
        )
          .asJson
      case status @ BattleExtractionStatus.Extracted(playerId, heroId, zoneId, atElapsedMs) =>
        BattleExtractionCompletedPayload(
          status = BattleExtractionStatus.wireValue(status),
          playerId = playerId.value,
          heroId = heroId.value,
          zoneId = zoneId.value,
          atElapsedMs = atElapsedMs.value
        )
          .asJson
      case status @ BattleExtractionStatus.Interrupted(playerId, heroId, zoneId, reason, atElapsedMs) =>
        BattleExtractionInterruptedPayload(
          status = BattleExtractionStatus.wireValue(status),
          playerId = playerId.value,
          heroId = heroId.value,
          zoneId = zoneId.value,
          reason = BattleExtractionInterruptReason.wireValue(reason),
          atElapsedMs = atElapsedMs.value
        )
          .asJson
    }

  given Encoder[BattleExtractionState] =
    Encoder.forProduct2("zones", "status")((response: BattleExtractionState) => (response.zones, response.status))

  given Encoder[BattleLootCacheStatus] =
    Encoder.instance {
      case BattleLootCacheStatus.Available =>
        StatusOnlyPayload(BattleLootCacheStatus.wireValue(BattleLootCacheStatus.Available)).asJson
      case status @ BattleLootCacheStatus.Searching(playerId, heroId, progressMs) =>
        BattleLootCacheSearchingPayload(
          status = BattleLootCacheStatus.wireValue(status),
          playerId = playerId.value,
          heroId = heroId.value,
          progressMs = progressMs.value
        )
          .asJson
      case status @ BattleLootCacheStatus.Searched(playerId, heroId, atElapsedMs) =>
        BattleLootCacheSearchedPayload(
          status = BattleLootCacheStatus.wireValue(status),
          playerId = playerId.value,
          heroId = heroId.value,
          atElapsedMs = atElapsedMs.value
        )
          .asJson
    }

  given Encoder[BattleLootCacheState] =
    Encoder[BattleLootCacheStatePayload].contramap(response =>
      BattleLootCacheStatePayload(
        cacheId = response.cacheId.value,
        position = response.position,
        radius = response.radius.value,
        searchDurationMs = response.searchDuration.value,
        scoreValue = response.scoreValue.value,
        status = response.status
      )
    )

  private final case class BattleGasZoneStatePayload(
    phase: String,
    center: BattleVector2,
    radius: Double,
    nextRadius: Double,
    damagePerSecond: Double,
    stageIndex: Int,
    progressMs: Long,
    startsAtMs: Long,
    endsAtMs: Long
  )

  private given Encoder[BattleGasZoneStatePayload] =
    Encoder.forProduct9(
      "phase",
      "center",
      "radius",
      "nextRadius",
      "damagePerSecond",
      "stageIndex",
      "progressMs",
      "startsAtMs",
      "endsAtMs"
    )(payload =>
      (
        payload.phase,
        payload.center,
        payload.radius,
        payload.nextRadius,
        payload.damagePerSecond,
        payload.stageIndex,
        payload.progressMs,
        payload.startsAtMs,
        payload.endsAtMs
      )
    )

  private final case class BattleExtractionZoneDefinitionPayload(
    zoneId: String,
    position: BattleVector2,
    radius: Double,
    availableFromMs: Long,
    channelDurationMs: Long
  )

  private given Encoder[BattleExtractionZoneDefinitionPayload] =
    Encoder.forProduct5("zoneId", "position", "radius", "availableFromMs", "channelDurationMs")(payload =>
      (payload.zoneId, payload.position, payload.radius, payload.availableFromMs, payload.channelDurationMs)
    )

  private final case class StatusOnlyPayload(status: String)

  private given Encoder[StatusOnlyPayload] =
    Encoder.forProduct1("status")(_.status)

  private final case class BattleExtractionInProgressPayload(
    status: String,
    playerId: String,
    heroId: String,
    zoneId: String,
    progressMs: Long
  )

  private given Encoder[BattleExtractionInProgressPayload] =
    Encoder.forProduct5("status", "playerId", "heroId", "zoneId", "progressMs")(payload =>
      (payload.status, payload.playerId, payload.heroId, payload.zoneId, payload.progressMs)
    )

  private final case class BattleExtractionCompletedPayload(
    status: String,
    playerId: String,
    heroId: String,
    zoneId: String,
    atElapsedMs: Long
  )

  private given Encoder[BattleExtractionCompletedPayload] =
    Encoder.forProduct5("status", "playerId", "heroId", "zoneId", "atElapsedMs")(payload =>
      (payload.status, payload.playerId, payload.heroId, payload.zoneId, payload.atElapsedMs)
    )

  private final case class BattleExtractionInterruptedPayload(
    status: String,
    playerId: String,
    heroId: String,
    zoneId: String,
    reason: String,
    atElapsedMs: Long
  )

  private given Encoder[BattleExtractionInterruptedPayload] =
    Encoder.forProduct6("status", "playerId", "heroId", "zoneId", "reason", "atElapsedMs")(payload =>
      (payload.status, payload.playerId, payload.heroId, payload.zoneId, payload.reason, payload.atElapsedMs)
    )

  private final case class BattleLootCacheSearchingPayload(
    status: String,
    playerId: String,
    heroId: String,
    progressMs: Long
  )

  private given Encoder[BattleLootCacheSearchingPayload] =
    Encoder.forProduct4("status", "playerId", "heroId", "progressMs")(payload =>
      (payload.status, payload.playerId, payload.heroId, payload.progressMs)
    )

  private final case class BattleLootCacheSearchedPayload(
    status: String,
    playerId: String,
    heroId: String,
    atElapsedMs: Long
  )

  private given Encoder[BattleLootCacheSearchedPayload] =
    Encoder.forProduct4("status", "playerId", "heroId", "atElapsedMs")(payload =>
      (payload.status, payload.playerId, payload.heroId, payload.atElapsedMs)
    )

  private final case class BattleLootCacheStatePayload(
    cacheId: String,
    position: BattleVector2,
    radius: Double,
    searchDurationMs: Long,
    scoreValue: Int,
    status: BattleLootCacheStatus
  )

  private given Encoder[BattleLootCacheStatePayload] =
    Encoder.forProduct6("cacheId", "position", "radius", "searchDurationMs", "scoreValue", "status")(payload =>
      (
        payload.cacheId,
        payload.position,
        payload.radius,
        payload.searchDurationMs,
        payload.scoreValue,
        payload.status
      )
    )
}
