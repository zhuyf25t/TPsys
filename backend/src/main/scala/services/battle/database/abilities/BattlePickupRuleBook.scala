package services.battle.database.abilities

import java.util.concurrent.atomic.AtomicReference

import services.battle.objects.abilities.BattlePickupRuleConfig

private[services] object BattlePickupRuleBook {
  private val config =
    AtomicReference[Option[BattlePickupRuleConfig]](None)

  def replace(nextConfig: BattlePickupRuleConfig): Unit =
    config.set(Some(nextConfig))

  def hasRules: Boolean =
    config.get().nonEmpty

  def current: BattlePickupRuleConfig =
    config.get().getOrElse {
      throw IllegalStateException("Missing battle pickup rules in PostgreSQL.")
    }
}
