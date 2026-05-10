package slaydemo.backend.battle.objects

final case class BattleSlowFieldState(
  fieldId: SlowFieldId,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  position: BattleVector2,
  radius: Radius,
  ttlMs: DurationMillis,
  durationMs: DurationMillis
)
