package services.battle.objects.projectile

import services.battle.objects.*
import services.battle.objects.core.*
import services.battle.objects.event.*
import services.battle.objects.pickup.*
import services.battle.objects.player.*
import services.battle.objects.projectile.*
import services.battle.objects.queue.*
import services.battle.objects.replay.*
import services.battle.objects.result.*
import services.battle.objects.skill.*
import services.battle.objects.weapon.*

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
