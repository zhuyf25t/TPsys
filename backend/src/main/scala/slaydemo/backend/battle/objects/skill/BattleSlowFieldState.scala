package slaydemo.backend.battle.objects.skill

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

final case class BattleSlowFieldState(
  fieldId: SlowFieldId,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  position: BattleVector2,
  radius: Radius,
  ttlMs: DurationMillis,
  durationMs: DurationMillis
)
