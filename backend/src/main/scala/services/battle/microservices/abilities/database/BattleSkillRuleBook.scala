package services.battle.microservices.abilities.database

import java.util.concurrent.atomic.AtomicReference

import services.battle.microservices.abilities.objects.abilities.*

private[services] object BattleSkillRuleBook {
  private val ruleSet =
    AtomicReference[Option[BattleSkillRuleSet]](None)

  def replace(rules: BattleSkillRuleSet): Unit =
    ruleSet.set(Some(rules))

  def hasRules: Boolean =
    ruleSet.get().nonEmpty

  def blink: BlinkConfig =
    requireRules.blink

  def dash: DashConfig =
    requireRules.dash

  def freeze: FreezeConfig =
    requireRules.freeze

  private def requireRules: BattleSkillRuleSet =
    ruleSet.get().getOrElse {
      throw IllegalStateException("Missing battle skill rules in PostgreSQL.")
    }
}
