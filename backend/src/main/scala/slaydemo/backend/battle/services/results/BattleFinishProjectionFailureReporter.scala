package slaydemo.backend.battle.services.results

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.BattleId

trait BattleFinishProjectionFailureReporter {
  /** 中文名：reportfailure（reportFailure）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def reportFailure(battleId: BattleId, message: String): Unit
}

object ConsoleBattleFinishProjectionFailureReporter extends BattleFinishProjectionFailureReporter {
  /** 中文名：reportfailure（reportFailure）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def reportFailure(battleId: BattleId, message: String): Unit =
    Console.err.println(s"[battle-finish-projection] battleId=${battleId.value} failed: $message")
}
