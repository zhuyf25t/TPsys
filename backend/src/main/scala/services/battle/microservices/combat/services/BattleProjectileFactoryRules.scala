package services.battle.microservices.combat.services

import cats.effect.IO

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
    normalizeMovement: BattleVector2 => IO[BattleVector2]
  ): IO[Vector[BattleProjectileState]] = {
    val projectileCount = math.max(1, projectile.projectileCount.value)
    (0 until projectileCount).toVector.foldLeft(IO.pure(Vector.empty[BattleProjectileState])) { (projectilesIO, index) =>
      for
        projectiles <- projectilesIO
        direction <- spreadDirection(shooter.aim, index, projectileCount, projectile.spread.value, normalizeMovement)
        id <- projectileId(shooter, commandSeq, index, projectileCount)
        position <- projectileBirthPosition(shooter, direction, projectileBirthOffset, normalizeMovement)
        nextProjectile <- weaponProjectile(shooter, id, position, direction, projectile)
      yield projectiles :+ nextProjectile
    }
  }

  def resolvePistolShot(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectile: BattleWeaponProjectileDefinition,
    projectileBirthOffset: Double,
    normalizeMovement: BattleVector2 => IO[BattleVector2]
  ): IO[BattleAggregateState] = {
    val direction = shooter.aim
    for
      start <- projectileBirthPosition(shooter, direction, projectileBirthOffset, normalizeMovement)
      projectileState <- pistolProjectile(shooter, commandSeq, start, direction, projectile)
    yield state.copy(projectiles = state.projectiles :+ projectileState)
  }

  private def projectileId(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectileIndex: Int,
    projectileCount: Int
  ): IO[ProjectileId] = IO.pure {
    val suffix = if projectileCount == 1 then "" else s"-$projectileIndex"
    ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}$suffix")
  }

  private def spreadDirection(
    direction: BattleVector2,
    projectileIndex: Int,
    projectileCount: Int,
    spreadRadians: Double,
    normalizeMovement: BattleVector2 => IO[BattleVector2]
  ): IO[BattleVector2] =
    if projectileCount <= 1 || spreadRadians == 0.0 then IO.pure(direction)
    else
      val offset =
        ((projectileIndex.toDouble / (projectileCount - 1).toDouble) - 0.5) * spreadRadians
      rotate(direction, offset, normalizeMovement)

  private def rotate(
    direction: BattleVector2,
    radians: Double,
    normalizeMovement: BattleVector2 => IO[BattleVector2]
  ): IO[BattleVector2] = {
    val cos = math.cos(radians)
    val sin = math.sin(radians)
    normalizeMovement(
      BattleVector2(
        direction.x * cos - direction.y * sin,
        direction.x * sin + direction.y * cos
      )
    )
  }

  private def weaponProjectile(
    shooter: BattlePlayerState,
    projectileId: ProjectileId,
    position: BattleVector2,
    direction: BattleVector2,
    projectile: BattleWeaponProjectileDefinition
  ): IO[BattleProjectileState] =
    scale(direction, projectile.speed.value).map { velocity =>
      BattleProjectileState(
        projectileId = projectileId,
        ownerHeroId = shooter.heroId,
        projectileKind = projectile.projectileKind,
        position = position,
        velocity = velocity,
        facing = FacingRadians(math.atan2(direction.y, direction.x)),
        radius = projectile.radius,
        damage = projectile.damage,
        ttlMs = projectile.lifetime,
        maxLifetimeMs = projectile.lifetime,
        splashRadius = projectile.splashRadius
      )
    }

  private def pistolProjectile(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    start: BattleVector2,
    direction: BattleVector2,
    projectile: BattleWeaponProjectileDefinition
  ): IO[BattleProjectileState] =
    scale(direction, projectile.speed.value).map { velocity =>
      BattleProjectileState(
        projectileId = ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}"),
        ownerHeroId = shooter.heroId,
        projectileKind = projectile.projectileKind,
        position = start,
        velocity = velocity,
        facing = shooter.facing,
        radius = projectile.radius,
        damage = projectile.damage,
        ttlMs = projectile.lifetime,
        maxLifetimeMs = projectile.lifetime,
        splashRadius = projectile.splashRadius
      )
    }

  private def projectileBirthPosition(
    shooter: BattlePlayerState,
    direction: BattleVector2,
    projectileBirthOffset: Double,
    normalizeMovement: BattleVector2 => IO[BattleVector2]
  ): IO[BattleVector2] =
    for
      normalized <- normalizeMovement(direction)
      offset <- scale(normalized, projectileBirthOffset)
      position <- add(shooter.position, offset)
    yield position
}
