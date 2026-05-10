package slaydemo.backend.battle.objects

enum BattlePlayerLifeState {
  case Alive
  case Eliminated(eliminatedAtMs: Option[ElapsedMillis], respawnMs: DurationMillis)
}

object BattlePlayerLifeState {
  def fromAliveFlag(
    alive: Boolean,
    eliminatedAtMs: Option[ElapsedMillis],
    respawnMs: DurationMillis
  ): BattlePlayerLifeState =
    if alive then BattlePlayerLifeState.Alive
    else BattlePlayerLifeState.Eliminated(eliminatedAtMs, DurationMillis(math.max(0L, respawnMs.value)))

  def eliminated(eliminatedAtMs: Option[ElapsedMillis], respawnMs: DurationMillis): BattlePlayerLifeState =
    BattlePlayerLifeState.Eliminated(eliminatedAtMs, DurationMillis(math.max(0L, respawnMs.value)))

  def withRespawnMs(value: BattlePlayerLifeState, respawnMs: DurationMillis): BattlePlayerLifeState =
    value match {
      case BattlePlayerLifeState.Alive =>
        BattlePlayerLifeState.Alive
      case BattlePlayerLifeState.Eliminated(eliminatedAtMs, _) =>
        eliminated(eliminatedAtMs, respawnMs)
    }

  def aliveFlag(value: BattlePlayerLifeState): Boolean =
    value == BattlePlayerLifeState.Alive

  def eliminatedAtMs(value: BattlePlayerLifeState): Option[ElapsedMillis] =
    value match {
      case BattlePlayerLifeState.Alive =>
        None
      case BattlePlayerLifeState.Eliminated(eliminatedAtMs, _) =>
        eliminatedAtMs
    }

  def respawnMs(value: BattlePlayerLifeState): DurationMillis =
    value match {
      case BattlePlayerLifeState.Alive =>
        DurationMillis(0L)
      case BattlePlayerLifeState.Eliminated(_, respawnMs) =>
        DurationMillis(math.max(0L, respawnMs.value))
    }
}
