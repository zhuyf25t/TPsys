package slaydemo.backend.battle.objects.projectile

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.objects.core.*
import slaydemo.backend.battle.objects.event.*
import slaydemo.backend.battle.objects.pickup.*
import slaydemo.backend.battle.objects.player.*
import slaydemo.backend.battle.objects.projectile.*
import slaydemo.backend.battle.objects.queue.*
import slaydemo.backend.battle.objects.replay.*
import slaydemo.backend.battle.objects.result.*
import slaydemo.backend.battle.objects.skill.*
import slaydemo.backend.battle.objects.weapon.*

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
