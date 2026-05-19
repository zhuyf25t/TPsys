package slaydemo.backend.battle.services.combat

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.world.BattleArenaCollision.*
import slaydemo.backend.battle.services.world.BattleGeometry.*

private[services] object BattleProjectileTargetingRules {
  final case class ProjectilePlayerHit(
    player: BattlePlayerState,
    position: BattleVector2,
    distance: Double
  )

  /** 中文名：查找投射物玩家hit（findProjectilePlayerHit）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火。 */
  def findProjectilePlayerHit(
    players: Vector[BattlePlayerState],
    projectile: BattleProjectileState,
    destination: BattleVector2
  ): Option[ProjectilePlayerHit] = {
    val path = BattleVector2(destination.x - projectile.position.x, destination.y - projectile.position.y)
    val pathLength = vectorLength(path)
    if pathLength <= 0.0001 then None
    else {
      players
        .filter(player => player.alive && player.heroId != projectile.ownerHeroId)
        .flatMap { player =>
          val hitRadius =
            projectile.radius.value +
              BattleArenaCatalog.PlayerCollisionRadius +
              BattleArenaCatalog.ProjectileShooterAdvantageRadius
          segmentCircleHitT(projectile.position, destination, player.position, hitRadius).map { hitT =>
            ProjectilePlayerHit(player, pointAtSegmentT(projectile.position, destination, hitT), hitT * pathLength)
          }
        }
        .sortBy(_.distance)
        .headOption
    }
  }
}
