package services.battle.microservices.world.database

import java.util.concurrent.atomic.AtomicReference

import services.battle.microservices.world.objects.world.*
import services.battle.objects.core.BattleMapId

private[services] object BattleWorldRuleBook {
  private val rules =
    AtomicReference[Option[BattleWorldRuleSet]](None)

  def replace(nextRules: BattleWorldRuleSet): Unit =
    rules.set(Some(nextRules))

  def world: BattleWorldRuleConfig =
    requireRules.world

  def movement: BattleMovementRuleConfig =
    requireRules.movement

  def loadedMap(mapId: BattleMapId): BattleLoadedMapSpec =
    requireRules.mapsById.getOrElse(
      mapId,
      throw IllegalStateException(s"Missing battle world map rule in PostgreSQL: ${mapId.value}")
    )

  private def requireRules: BattleWorldRuleSet =
    rules.get().getOrElse {
      throw IllegalStateException("Missing battle world rules in PostgreSQL.")
    }
}
