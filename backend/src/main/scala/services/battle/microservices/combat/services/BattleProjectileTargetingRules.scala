package services.battle.microservices.combat.services

import cats.effect.IO

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
    segmentCircleHitT: (BattleVector2, BattleVector2, BattleVector2, Radius) => IO[Option[Double]]
  ): IO[Option[ProjectilePlayerHit]] = {
    val path = BattleVector2(destination.x - projectile.position.x, destination.y - projectile.position.y)
    vectorLength(path).flatMap { pathLength =>
      if pathLength <= 0.0001 then IO.pure(None)
      else
        players
          .filter(player => player.alive && player.heroId != projectile.ownerHeroId)
          .foldLeft(IO.pure(Vector.empty[ProjectilePlayerHit])) { (hitsIO, player) =>
            for
              hits <- hitsIO
              hitT <- segmentCircleHitT(projectile.position, destination, player.position, hitRadius)
              nextHit <- hitT match {
                case Some(value) =>
                  pointAtSegmentT(projectile.position, destination, value)
                    .map(position => Some(ProjectilePlayerHit(player, position, value * pathLength)))
                case None =>
                  IO.pure(None)
              }
            yield nextHit.fold(hits)(hit => hits :+ hit)
          }
          .map(hits => hits.sortBy(_.distance).headOption)
    }
  }
}
