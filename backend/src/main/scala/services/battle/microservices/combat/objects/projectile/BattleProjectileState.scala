package services.battle.microservices.combat.objects.projectile

import services.battle.microservices.combat.objects.projectile.{ProjectileKind, ProjectileTerminalReason}
import services.battle.microservices.combat.objects.combat.Damage
import services.battle.microservices.actors.objects.player.HitPoints
import services.battle.objects.core.{
  BattleVector2,
  DurationMillis,
  ElapsedMillis,
  FacingRadians,
  HeroId,
  PlayerId,
  ProjectileId,
  Radius
}

final case class BattleProjectileState(
  projectileId: ProjectileId,
  ownerHeroId: HeroId,
  projectileKind: ProjectileKind,
  position: BattleVector2,
  velocity: BattleVector2,
  facing: FacingRadians,
  radius: Radius,
  damage: Damage,
  ttlMs: DurationMillis,
  maxLifetimeMs: DurationMillis,
  splashRadius: Radius
)

final case class BattleProjectileTerminalState(
  projectileId: ProjectileId,
  projectileKind: ProjectileKind,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  reason: ProjectileTerminalReason,
  start: BattleVector2,
  end: BattleVector2,
  terminalPosition: BattleVector2,
  ttlBefore: DurationMillis,
  ttlAfter: DurationMillis,
  elapsedMs: ElapsedMillis,
  targetPlayerId: Option[PlayerId],
  targetHeroId: Option[HeroId],
  hpBefore: Option[HitPoints],
  hpAfter: Option[HitPoints],
  damage: Option[Damage]
)
