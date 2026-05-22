package services.battle.objects.event

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

import services.identity.objects.DisplayName

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
