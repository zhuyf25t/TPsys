package services.battle.objects.event

import services.battle.objects.BattleEventKind
import services.battle.objects.core.{BattleEventId, ElapsedMillis, HeroId, PlayerId}
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
