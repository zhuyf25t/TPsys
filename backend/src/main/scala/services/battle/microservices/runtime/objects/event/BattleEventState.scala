package services.battle.microservices.runtime.objects.event

import services.identity.objects.DisplayName

final case class BattleEventParticipant(
  playerId: _root_.services.battle.objects.core.PlayerId,
  heroId: _root_.services.battle.objects.core.HeroId,
  displayName: DisplayName
)

final case class BattleEventState(
  eventId: BattleEventId,
  eventKind: BattleEventKind,
  elapsedMs: _root_.services.battle.objects.core.ElapsedMillis,
  message: String,
  source: BattleEventParticipant,
  target: BattleEventParticipant
)
