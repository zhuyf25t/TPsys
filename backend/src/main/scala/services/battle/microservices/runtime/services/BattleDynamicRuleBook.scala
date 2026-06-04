package services.battle.microservices.runtime.services

import cats.effect.{IO, Ref, Resource}

import services.battle.microservices.abilities.objects.abilities.*
import services.battle.microservices.actors.objects.actors.BattleBotRuleConfig
import services.battle.microservices.combat.objects.combat.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.runtime.objects.runtime.*
import services.battle.microservices.world.objects.world.*
import services.battle.objects.core.BattleMapId

final case class BattleDynamicRules(
  worldRuleSet: BattleWorldRuleSet,
  runtimeRuleSet: BattleRuntimeRuleSet,
  combatRulesByWeapon: Map[WeaponKind, BattleWeaponRuleDefinition],
  skillRuleSet: BattleSkillRuleSet,
  pickupRuleConfig: BattlePickupRuleConfig,
  botRuleConfig: BattleBotRuleConfig
)

object BattleDynamicRules {
  def fromLoaded(
    worldRuleSet: BattleWorldRuleSet,
    runtimeRuleSet: BattleRuntimeRuleSet,
    combatRules: Vector[BattleWeaponRuleDefinition],
    skillRuleSet: BattleSkillRuleSet,
    pickupRuleConfig: BattlePickupRuleConfig,
    botRuleConfig: BattleBotRuleConfig
  ): IO[BattleDynamicRules] =
    IO.raiseWhen(combatRules.isEmpty)(
      IllegalStateException("PostgreSQL table battle_combat_weapon_rules has no rows.")
    ).as(
      BattleDynamicRules(
        worldRuleSet = worldRuleSet,
        runtimeRuleSet = runtimeRuleSet,
        combatRulesByWeapon = combatRules.map(rule => rule.weaponKind -> rule).toMap,
        skillRuleSet = skillRuleSet,
        pickupRuleConfig = pickupRuleConfig,
        botRuleConfig = botRuleConfig
      )
    )
}

final class BattleDynamicRuleBook private (
  rulesRef: Ref[IO, BattleDynamicRules]
) {
  def snapshot: IO[BattleDynamicRules] =
    rulesRef.get

  def replace(nextRules: BattleDynamicRules): IO[Unit] =
    rulesRef.set(nextRules)

  def runtime: IO[BattleRuntimeRuleConfig] =
    snapshot.map(_.runtimeRuleSet.runtime)

  def history: IO[BattleHistoryRuleConfig] =
    snapshot.map(_.runtimeRuleSet.history)

  def sessionPlayer: IO[BattleSessionPlayerRuleConfig] =
    snapshot.map(_.runtimeRuleSet.sessionPlayer)

  def world: IO[BattleWorldRuleConfig] =
    snapshot.map(_.worldRuleSet.world)

  def movement: IO[BattleMovementRuleConfig] =
    snapshot.map(_.worldRuleSet.movement)

  def loadedMap(mapId: BattleMapId): IO[BattleLoadedMapSpec] =
    snapshot.flatMap { rules =>
      IO.fromOption(rules.worldRuleSet.mapsById.get(mapId))(
        IllegalStateException(s"Missing battle world map rule in PostgreSQL: ${mapId.value}")
      )
    }

  def blink: IO[BlinkConfig] =
    snapshot.map(_.skillRuleSet.blink)

  def dash: IO[DashConfig] =
    snapshot.map(_.skillRuleSet.dash)

  def freeze: IO[FreezeConfig] =
    snapshot.map(_.skillRuleSet.freeze)

  def critical: IO[CriticalConfig] =
    snapshot.map(_.skillRuleSet.critical)

  def pickup: IO[BattlePickupRuleConfig] =
    snapshot.map(_.pickupRuleConfig)

  def bot: IO[BattleBotRuleConfig] =
    snapshot.map(_.botRuleConfig)

  def fireDefinition(weaponKind: WeaponKind): IO[BattleWeaponFireDefinition] =
    weaponRule(weaponKind).map(_.fire)

  def inventoryDefinition(weaponKind: WeaponKind): IO[BattleWeaponInventoryDefinition] =
    weaponRule(weaponKind).map(_.inventory)

  def heatDefinition(weaponKind: WeaponKind): IO[Option[BattleWeaponHeatDefinition]] =
    fireDefinition(weaponKind).map(_.heat)

  private def weaponRule(weaponKind: WeaponKind): IO[BattleWeaponRuleDefinition] =
    snapshot.flatMap { rules =>
      IO.fromOption(rules.combatRulesByWeapon.get(weaponKind))(
        IllegalStateException(
          s"Missing battle combat weapon rule in PostgreSQL: ${WeaponKind.wireValue(weaponKind)}"
        )
      )
    }
}

object BattleDynamicRuleBook {
  def create(initialRules: BattleDynamicRules): IO[BattleDynamicRuleBook] =
    Ref.of[IO, BattleDynamicRules](initialRules).map(ref => new BattleDynamicRuleBook(ref))

  def resource(initialRules: BattleDynamicRules): Resource[IO, BattleDynamicRuleBook] =
    Resource.eval(create(initialRules))
}
