package services.battle.objects.projectile

import services.battle.objects.{ProjectileKind, ProjectileTerminalReason}
import services.battle.objects.core.{
  BattleVector2,
  Damage,
  DurationMillis,
  ElapsedMillis,
  FacingRadians,
  HeroId,
  HitPoints,
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
