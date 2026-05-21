package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.routes.BattleStateJsonSupport.*

object BattleStateJson {
  def renderState(state: BattleAggregateState): String =
    renderObject(
      Vector(
        "battleId" -> jsonString(state.battleId.value),
        "roomId" -> jsonString(state.roomId.value),
        "phase" -> jsonString(BattlePhase.wireValue(state.phase)),
        "serverTime" -> state.serverTime.value.toString,
        "startedAt" -> state.startedAt.value.toString,
        "durationMs" -> state.durationMs.value.toString,
        "elapsedMs" -> state.elapsedMs.value.toString,
        "endsAt" -> state.endsAt.value.toString,
        "worldSize" -> renderVector(state.worldSize),
        "tick" -> state.tick.value.toString,
        "resultReady" -> BattleArtifactStatus.isResultReady(state.artifactStatus).toString,
        "replayReady" -> BattleArtifactStatus.isReplayReady(state.artifactStatus).toString,
        "players" -> state.players.map(BattlePlayerStateJsonRenderer.renderPlayer).mkString("[", ",", "]"),
        "projectiles" -> state.projectiles.map(BattleEntityStateJsonRenderer.renderProjectile).mkString("[", ",", "]"),
        "projectileTerminals" -> state.projectileTerminals.map(BattleEntityStateJsonRenderer.renderProjectileTerminal).mkString("[", ",", "]"),
        "slowFields" -> state.slowFields.map(BattleEntityStateJsonRenderer.renderSlowField).mkString("[", ",", "]"),
        "pickups" -> state.pickups.map(BattleEntityStateJsonRenderer.renderPickup).mkString("[", ",", "]"),
        "events" -> state.events.map(BattleEntityStateJsonRenderer.renderEvent).mkString("[", ",", "]"),
        "winnerPlayerId" -> renderOptionalString(state.winnerPlayerId.map(_.value)),
        "winnerHeroId" -> renderOptionalString(state.winnerHeroId.map(_.value))
      )
    )

}
