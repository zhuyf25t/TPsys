package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.DurationMillis

private[services] final case class BattleHistoryCount(value: Int) extends AnyVal

private[services] object BattleHistoryCatalog {
  val RetainedProjectileTerminalCount: BattleHistoryCount = BattleHistoryCount(64)
  val RetainedBattleEventCount: BattleHistoryCount = BattleHistoryCount(12)
  val ReplayFrameSampleInterval: DurationMillis = DurationMillis(1000L)
  val RetainedReplayFrameCount: BattleHistoryCount = BattleHistoryCount(32)
}
