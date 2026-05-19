package slaydemo.backend.battle.objects.event

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

import slaydemo.backend.identity.objects.DisplayName

final case class BattleEventParticipant(
  playerId: PlayerId,
  heroId: HeroId,
  displayName: DisplayName
)

final case class BattleEventState(
  eventId: BattleEventId,
  eventKind: BattleEventKind,
  elapsedMs: ElapsedMillis,
  message: String,
  source: BattleEventParticipant,
  target: BattleEventParticipant
)
