package services.battle.microservices.combat.services

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.objects.core.{BattleVector2, Radius}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileTargetingRules {
  final case class ProjectilePlayerHit(
    player: BattlePlayerState,
    position: BattleVector2,
    distance: Double
  )

  def findProjectilePlayerHit(
    players: Vector[BattlePlayerState],
    projectile: BattleProjectileState,
    destination: BattleVector2,
    hitRadius: Radius,
    segmentCircleHitT: (BattleVector2, BattleVector2, BattleVector2, Radius) => Option[Double]
  ): Option[ProjectilePlayerHit] = {
    val path = BattleVector2(destination.x - projectile.position.x, destination.y - projectile.position.y)
    val pathLength = vectorLength(path)
    if pathLength <= 0.0001 then None
    else {
      players
        .filter(player => player.alive && player.heroId != projectile.ownerHeroId)
        .flatMap { player =>
          segmentCircleHitT(projectile.position, destination, player.position, hitRadius).map { hitT =>
            ProjectilePlayerHit(player, pointAtSegmentT(projectile.position, destination, hitT), hitT * pathLength)
          }
        }
        .sortBy(_.distance)
        .headOption
    }
  }
}