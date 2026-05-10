package slaydemo.backend.battle.objects

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
