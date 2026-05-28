package services.battle.objects.runtime

import services.battle.objects.WeaponKind
import services.battle.objects.core.{DurationMillis, HitPoints, Stamina}

private[services] final case class BattleHistoryCount(value: Int) extends AnyVal

private[services] final case class BattleRuntimeRuleConfig(
  defaultBattleDuration: DurationMillis,
  tickStep: DurationMillis
)

private[services] final case class BattleHistoryRuleConfig(
  retainedProjectileTerminalCount: BattleHistoryCount,
  retainedBattleEventCount: BattleHistoryCount,
  replayFrameSampleInterval: DurationMillis,
  retainedReplayFrameCount: BattleHistoryCount
)

private[services] final case class BattleSessionPlayerRuleConfig(
  initialHp: HitPoints,
  maxHp: HitPoints,
  initialStamina: Stamina,
  maxStamina: Stamina,
  defaultWeaponKind: WeaponKind
)

private[services] final case class BattleRuntimeRuleSet(
  runtime: BattleRuntimeRuleConfig,
  history: BattleHistoryRuleConfig,
  sessionPlayer: BattleSessionPlayerRuleConfig
)
