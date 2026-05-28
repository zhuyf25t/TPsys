package services.battle.database.runtime

import java.util.concurrent.atomic.AtomicReference

import services.battle.objects.runtime.*

private[services] object BattleRuntimeRuleBook {
  private val rules =
    AtomicReference[Option[BattleRuntimeRuleSet]](None)

  def replace(nextRules: BattleRuntimeRuleSet): Unit =
    rules.set(Some(nextRules))

  def runtime: BattleRuntimeRuleConfig =
    requireRules.runtime

  def history: BattleHistoryRuleConfig =
    requireRules.history

  def sessionPlayer: BattleSessionPlayerRuleConfig =
    requireRules.sessionPlayer

  private def requireRules: BattleRuntimeRuleSet =
    rules.get().getOrElse {
      throw IllegalStateException("Missing battle runtime rules in PostgreSQL.")
    }
}
