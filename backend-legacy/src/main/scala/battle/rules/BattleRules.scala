package slaydemo.backend.battle.rules

import slaydemo.backend.shared.rules.HandleRules

object BattleRules {
  val ArenaPlayerCapacity: Int = 6
  val MatchmakingDurationMs: Long = 5_000L
  val BattleDurationMs: Long = 5L * 60L * 1000L
  val VisitorHandle: String = "Visitor"

  def isVisitorHandle(value: String): Boolean =
    HandleRules.isVisitorLikeHandle(value)
}
