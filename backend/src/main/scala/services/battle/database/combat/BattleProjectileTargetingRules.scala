package services.battle.database.combat

import services.battle.database.world.BattleArenaCollision.*
import services.battle.database.world.BattleArenaCatalog
import services.battle.database.world.BattleGeometry.*
import services.battle.objects.core.BattleVector2
import services.battle.objects.player.BattlePlayerState
import services.battle.objects.projectile.BattleProjectileState

private[services] object BattleProjectileTargetingRules {
  final case class ProjectilePlayerHit(
    player: BattlePlayerState,
    position: BattleVector2,
    distance: Double
  )

  /** 中文名：查找投射物玩家hit（findProjectilePlayerHit）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
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
