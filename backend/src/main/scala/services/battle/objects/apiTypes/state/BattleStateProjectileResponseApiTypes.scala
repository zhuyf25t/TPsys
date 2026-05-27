package services.battle.objects.apiTypes.state

import io.circe.Encoder
import services.battle.objects.{ProjectileKind, ProjectileTerminalReason}
import services.battle.objects.projectile.{BattleProjectileState, BattleProjectileTerminalState}

import BattleStateVectorResponse.given

object BattleStateProjectileResponse {
  given Encoder[BattleProjectileState] =
    Encoder.forProduct11(
      "projectileId",
      "ownerHeroId",
      "kind",
      "position",
      "velocity",
      "facing",
      "radius",
      "damage",
      "ttlMs",
      "maxLifetimeMs",
      "splashRadius"
    )((response: BattleProjectileState) =>
      (
        response.projectileId.value,
        response.ownerHeroId.value,
        ProjectileKind.wireValue(response.projectileKind),
        response.position,
        response.velocity,
        response.facing.value,
        response.radius.value,
        response.damage.value,
        response.ttlMs.value,
        response.maxLifetimeMs.value,
        response.splashRadius.value
      )
    )
}

object BattleStateProjectileTerminalResponse {
  given Encoder[BattleProjectileTerminalState] =
    Encoder.forProduct16(
      "projectileId",
      "kind",
      "ownerPlayerId",
      "ownerHeroId",
      "reason",
      "start",
      "end",
      "terminalPosition",
      "ttlBefore",
      "ttlAfter",
      "elapsedMs",
      "targetPlayerId",
      "targetHeroId",
      "hpBefore",
      "hpAfter",
      "damage"
    )((response: BattleProjectileTerminalState) =>
      (
        response.projectileId.value,
        ProjectileKind.wireValue(response.projectileKind),
        response.ownerPlayerId.value,
        response.ownerHeroId.value,
        ProjectileTerminalReason.wireValue(response.reason),
        response.start,
        response.end,
        response.terminalPosition,
        response.ttlBefore.value,
        response.ttlAfter.value,
        response.elapsedMs.value,
        response.targetPlayerId.map(_.value),
        response.targetHeroId.map(_.value),
        response.hpBefore.map(_.value),
        response.hpAfter.map(_.value),
        response.damage.map(_.value)
      )
    )
}
