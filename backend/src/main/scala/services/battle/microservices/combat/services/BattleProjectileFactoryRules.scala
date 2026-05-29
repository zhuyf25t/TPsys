package services.battle.microservices.combat.services

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.combat.objects.combat.BattleWeaponProjectileDefinition
import services.battle.objects.core.{BattleAggregateState, BattleVector2, ClientCommandSeq, FacingRadians, ProjectileId}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.projectile.BattleProjectileState

private[battle] object BattleProjectileFactoryRules {
  def weaponProjectiles(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectile: BattleWeaponProjectileDefinition,
    projectileBirthOffset: Double,
    normalizeMovement: BattleVector2 => BattleVector2
  ): Vector[BattleProjectileState] = {
    val projectileCount = math.max(1, projectile.projectileCount.value)
    (0 until projectileCount).toVector.map { index =>
      val direction = spreadDirection(shooter.aim, commandSeq, index, projectileCount, projectile.spread.value, normalizeMovement)
      BattleProjectileState(
        projectileId = projectileId(shooter, commandSeq, index, projectileCount),
        ownerHeroId = shooter.heroId,
        projectileKind = projectile.projectileKind,
        position = projectileBirthPosition(shooter, direction, projectileBirthOffset, normalizeMovement),
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

  def resolvePistolShot(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectile: BattleWeaponProjectileDefinition,
    projectileBirthOffset: Double,
    normalizeMovement: BattleVector2 => BattleVector2
  ): BattleAggregateState = {
    val direction = shooter.aim
    val start = projectileBirthPosition(shooter, direction, projectileBirthOffset, normalizeMovement)
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
    spreadRadians: Double,
    normalizeMovement: BattleVector2 => BattleVector2
  ): BattleVector2 =
    if projectileCount <= 1 || spreadRadians == 0.0 then direction
    else {
      val offset =
        ((projectileIndex.toDouble / (projectileCount - 1).toDouble) - 0.5) * spreadRadians
      rotate(direction, offset, normalizeMovement)
    }

  private def rotate(
    direction: BattleVector2,
    radians: Double,
    normalizeMovement: BattleVector2 => BattleVector2
  ): BattleVector2 = {
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
    projectileBirthOffset: Double,
    normalizeMovement: BattleVector2 => BattleVector2
  ): BattleVector2 =
    add(shooter.position, scale(normalizeMovement(direction), projectileBirthOffset))
}
