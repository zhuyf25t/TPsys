package services.battle.database.actors

import java.util.concurrent.atomic.AtomicReference

import services.battle.objects.actors.*

private[services] object BattleBotRuleBook {
  private val config =
    AtomicReference[Option[BattleBotRuleConfig]](None)

  def replace(nextConfig: BattleBotRuleConfig): Unit =
    config.set(Some(nextConfig))

  def hasRules: Boolean =
    config.get().nonEmpty

  def current: BattleBotRuleConfig =
    config.get().getOrElse {
      throw IllegalStateException("Missing battle bot rules in PostgreSQL.")
    }
}
