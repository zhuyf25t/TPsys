package slaydemo.backend.battle.objects

enum BattleReplayHeroLifeState {
  case Alive
  case Eliminated(eliminatedAtMs: Option[ElapsedMillis])
}

object BattleReplayHeroLifeState {
  def fromAliveFlag(alive: Boolean, eliminatedAtMs: Option[ElapsedMillis]): BattleReplayHeroLifeState =
    if alive then BattleReplayHeroLifeState.Alive
    else BattleReplayHeroLifeState.Eliminated(eliminatedAtMs)

  def aliveFlag(value: BattleReplayHeroLifeState): Boolean =
    value == BattleReplayHeroLifeState.Alive

  def eliminatedAtMs(value: BattleReplayHeroLifeState): Option[ElapsedMillis] =
    value match {
      case BattleReplayHeroLifeState.Alive =>
        None
      case BattleReplayHeroLifeState.Eliminated(eliminatedAtMs) =>
        eliminatedAtMs
    }
}
