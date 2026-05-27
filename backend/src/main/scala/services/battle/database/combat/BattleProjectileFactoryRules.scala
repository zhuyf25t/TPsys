package services.battle.database.combat

import services.battle.database.world.BattleArenaCatalog
import services.battle.database.world.BattleGeometry.*
import services.battle.database.world.BattleMotionRules.*
import services.battle.objects.core.{
  BattleAggregateState,
  BattleVector2,
  ClientCommandSeq,
  FacingRadians,
  ProjectileId
}
import services.battle.objects.player.BattlePlayerState
import services.battle.objects.projectile.BattleProjectileState

private[services] object BattleProjectileFactoryRules {
  /** 中文名：武器projectiles（weaponProjectiles）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def weaponProjectiles(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectile: BattleWeaponProjectileDefinition
  ): Vector[BattleProjectileState] = {
    val projectileCount = math.max(1, projectile.projectileCount.value)
    (0 until projectileCount).toVector.map { index =>
      val direction = spreadDirection(shooter.aim, commandSeq, index, projectileCount, projectile.spread.value)
      BattleProjectileState(
        projectileId = projectileId(shooter, commandSeq, index, projectileCount),
        ownerHeroId = shooter.heroId,
        projectileKind = projectile.projectileKind,
        position = projectileBirthPosition(shooter, direction, projectile.radius.value),
        velocity = scale(direction, projectile.speed.value),
        facing = FacingRadians(math.atan2(direction.y, direction.x)),
        radius = projectile.radius,
        damage = projectile.damage,
        ttlMs = projectile.lifetime,
        maxLifetimeMs = projectile.lifetime,
        splashRadius = projectile.splashRadius
      )
    }
  }

  /** 中文名：解析pistolshot（resolvePistolShot）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def resolvePistolShot(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectile: BattleWeaponProjectileDefinition
  ): BattleAggregateState = {
    val direction = shooter.aim
    val start = projectileBirthPosition(shooter, direction, projectile.radius.value)
    state.copy(projectiles = state.projectiles :+ pistolProjectile(shooter, commandSeq, start, direction, projectile))
  }

  private def projectileId(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectileIndex: Int,
    projectileCount: Int
  ): ProjectileId = {
    val suffix = if projectileCount == 1 then "" else s"-$projectileIndex"
    ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}$suffix")
  }

  private def spreadDirection(
    direction: BattleVector2,
    commandSeq: ClientCommandSeq,
    projectileIndex: Int,
    projectileCount: Int,
    spreadRadians: Double
  ): BattleVector2 =
    if projectileCount <= 1 || spreadRadians == 0.0 then direction
    else {
      val offset =
        ((projectileIndex.toDouble / (projectileCount - 1).toDouble) - 0.5) * spreadRadians
      rotate(direction, offset)
    }

  private def rotate(direction: BattleVector2, radians: Double): BattleVector2 = {
    val cos = math.cos(radians)
    val sin = math.sin(radians)
    normalizeMovement(
      BattleVector2(
        direction.x * cos - direction.y * sin,
        direction.x * sin + direction.y * cos
      )
    )
  }

  private def pistolProjectile(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    start: BattleVector2,
    direction: BattleVector2,
    projectile: BattleWeaponProjectileDefinition
  ): BattleProjectileState =
    BattleProjectileState(
      projectileId = ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}"),
      ownerHeroId = shooter.heroId,
      projectileKind = projectile.projectileKind,
      position = start,
      velocity = scale(direction, projectile.speed.value),
      facing = shooter.facing,
      radius = projectile.radius,
      damage = projectile.damage,
      ttlMs = projectile.lifetime,
      maxLifetimeMs = projectile.lifetime,
      splashRadius = projectile.splashRadius
    )

  private def projectileBirthPosition(
    shooter: BattlePlayerState,
    direction: BattleVector2,
    projectileRadius: Double
  ): BattleVector2 =
    add(
      shooter.position,
      scale(
        normalizeMovement(direction),
        BattleArenaCatalog.PlayerCollisionRadius +
          projectileRadius +
          BattleArenaCatalog.ProjectileBirthClearance
      )
    )
}
