package services.battle.objects.skill

import services.battle.objects.core.{BattleVector2, DurationMillis, HeroId, PlayerId, Radius, SlowFieldId}

final case class BattleSlowFieldState(
  fieldId: SlowFieldId,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  position: BattleVector2,
  radius: Radius,
  ttlMs: DurationMillis,
  durationMs: DurationMillis
)
