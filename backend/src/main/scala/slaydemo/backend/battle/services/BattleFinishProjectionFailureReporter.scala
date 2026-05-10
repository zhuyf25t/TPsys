package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.BattleId

trait BattleFinishProjectionFailureReporter {
  def reportFailure(battleId: BattleId, message: String): Unit
}

object ConsoleBattleFinishProjectionFailureReporter extends BattleFinishProjectionFailureReporter {
  override def reportFailure(battleId: BattleId, message: String): Unit =
    Console.err.println(s"[battle-finish-projection] battleId=${battleId.value} failed: $message")
}
