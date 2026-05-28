package services.battle.database.combat

import java.util.concurrent.atomic.AtomicReference

import services.battle.objects.combat.*
import services.battle.objects.WeaponKind

private[services] object BattleCombatRuleBook {
  private val rulesByWeapon =
    AtomicReference[Map[WeaponKind, BattleWeaponRuleDefinition]](Map.empty)

  def replaceAll(rules: Vector[BattleWeaponRuleDefinition]): Unit =
    rulesByWeapon.set(ruleMap(rules))

  def hasRules: Boolean =
    rulesByWeapon.get().nonEmpty

  def inventoryDefinition(weaponKind: WeaponKind): BattleWeaponInventoryDefinition =
    requireRule(weaponKind).inventory

  def fireDefinition(weaponKind: WeaponKind): BattleWeaponFireDefinition =
    requireRule(weaponKind).fire

  def heatDefinition(weaponKind: WeaponKind): Option[BattleWeaponHeatDefinition] =
    fireDefinition(weaponKind).heat

  private def requireRule(weaponKind: WeaponKind): BattleWeaponRuleDefinition =
    rulesByWeapon.get().getOrElse(weaponKind, {
      throw IllegalStateException(
        s"Missing battle combat weapon rule in PostgreSQL: ${WeaponKind.wireValue(weaponKind)}"
      )
    })

  private def ruleMap(rules: Vector[BattleWeaponRuleDefinition]): Map[WeaponKind, BattleWeaponRuleDefinition] =
    rules.map(rule => rule.weaponKind -> rule).toMap
}
