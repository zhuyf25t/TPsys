package services.battle.objects.apiTypes.state

import io.circe.{Encoder, Json}
import io.circe.syntax.*
import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.core.BattleAggregateState

import BattleStateVectorResponse.given
import BattleStateProjectileResponse.given
import BattleStateProjectileTerminalResponse.given
import BattleStateSlowFieldResponse.given
import BattleStatePickupResponse.given
import BattleStateEventResponse.given
import BattleStatePlayerResponse.given

object BattleStateRootResponse {
  given Encoder[BattleAggregateState] =
    Encoder.instance(state =>
      Json
        .obj(
          "battleId" -> state.battleId.value.asJson,
          "roomId" -> state.roomId.value.asJson,
          "mapId" -> state.mapId.value.asJson,
          "phase" -> BattlePhase.wireValue(state.phase).asJson,
          "serverTime" -> state.serverTime.value.asJson,
          "startedAt" -> state.startedAt.value.asJson,
          "durationMs" -> state.durationMs.value.asJson,
          "elapsedMs" -> state.elapsedMs.value.asJson,
          "endsAt" -> state.endsAt.value.asJson,
          "worldSize" -> state.worldSize.asJson,
          "tick" -> state.tick.value.asJson,
          "resultReady" -> BattleArtifactStatus.isResultReady(state.artifactStatus).asJson,
          "replayReady" -> BattleArtifactStatus.isReplayReady(state.artifactStatus).asJson,
          "players" -> state.players.asJson,
          "projectiles" -> state.projectiles.asJson,
          "projectileTerminals" -> state.projectileTerminals.asJson,
          "slowFields" -> state.slowFields.asJson,
          "pickups" -> state.pickups.asJson,
          "events" -> state.events.asJson,
          "winnerPlayerId" -> state.winnerPlayerId.filter(_.value.trim.nonEmpty).map(_.value).asJson,
          "winnerHeroId" -> state.winnerHeroId.filter(_.value.trim.nonEmpty).map(_.value).asJson
        )
        .dropNullValues
    )
}
