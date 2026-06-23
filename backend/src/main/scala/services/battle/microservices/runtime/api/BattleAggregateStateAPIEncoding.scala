package services.battle.microservices.runtime.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import services.battle.microservices.abilities.api.{BattlePickupStateAPIEncoding, BattleSlowFieldStateAPIEncoding}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.abilities.objects.skill.BattleSlowFieldState
import services.battle.microservices.actors.api.BattlePlayerStateAPIEncoding
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.api.{BattleProjectileStateAPIEncoding, BattleProjectileTerminalStateAPIEncoding}
import services.battle.microservices.combat.objects.projectile.{BattleProjectileState, BattleProjectileTerminalState}
import services.battle.microservices.extraction.api.BattleExtractionStateAPIEncoding
import services.battle.microservices.extraction.objects.extraction.{BattleExtractionState, BattleGasZoneState, BattleLootCacheState}
import services.battle.microservices.runtime.objects.event.BattleEventState
import services.battle.microservices.world.api.{BattleVectorAPIEncoding, BattleWorldStateAPIEncoding}
import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.{BattleAggregateState, BattleVector2}

object BattleAggregateStateAPIEncoding {
  import BattleExtractionStateAPIEncoding.given
  import BattleEventStateAPIEncoding.given
  import BattlePickupStateAPIEncoding.given
  import BattlePlayerStateAPIEncoding.given
  import BattleProjectileStateAPIEncoding.given
  import BattleProjectileTerminalStateAPIEncoding.given
  import BattleSlowFieldStateAPIEncoding.given
  import BattleVectorAPIEncoding.given
  import BattleWorldStateAPIEncoding.given

  given Encoder[BattleAggregateState] =
    Encoder[BattleAggregateStatePayload]
      .contramap(BattleAggregateStatePayload.fromState)
      .mapJson(_.dropNullValues)

  private final case class BattleAggregateStatePayload(
    battleId: String,
    roomId: String,
    mapId: String,
    phase: String,
    serverTime: Long,
    startedAt: Long,
    durationMs: Long,
    elapsedMs: Long,
    endsAt: Long,
    worldSize: BattleVector2,
    tick: Long,
    resultReady: Boolean,
    replayReady: Boolean,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    projectileTerminals: Vector[BattleProjectileTerminalState],
    slowFields: Vector[BattleSlowFieldState],
    pickups: Vector[BattlePickupState],
    gasZone: Option[BattleGasZoneState],
    extraction: Option[BattleExtractionState],
    lootCaches: Vector[BattleLootCacheState],
    events: Vector[BattleEventState],
    winnerPlayerId: Option[String],
    winnerHeroId: Option[String]
  )

  private object BattleAggregateStatePayload {
    def fromState(state: BattleAggregateState): BattleAggregateStatePayload =
      BattleAggregateStatePayload(
        battleId = state.battleId.value,
        roomId = state.roomId.value,
        mapId = state.mapId.value,
        phase = BattlePhase.wireValue(state.phase),
        serverTime = state.serverTime.value,
        startedAt = state.startedAt.value,
        durationMs = state.durationMs.value,
        elapsedMs = state.elapsedMs.value,
        endsAt = state.endsAt.value,
        worldSize = state.worldSize,
        tick = state.tick.value,
        resultReady = BattleArtifactStatus.isResultReady(state.artifactStatus),
        replayReady = BattleArtifactStatus.isReplayReady(state.artifactStatus),
        players = state.players,
        projectiles = state.projectiles,
        projectileTerminals = state.projectileTerminals,
        slowFields = state.slowFields,
        pickups = state.pickups,
        gasZone = state.gasZone,
        extraction = state.extraction,
        lootCaches = state.lootCaches,
        events = state.events,
        winnerPlayerId = state.winnerPlayerId.filter(_.value.trim.nonEmpty).map(_.value),
        winnerHeroId = state.winnerHeroId.filter(_.value.trim.nonEmpty).map(_.value)
      )

    given Encoder[BattleAggregateStatePayload] =
      deriveEncoder[BattleAggregateStatePayload]
  }
}
