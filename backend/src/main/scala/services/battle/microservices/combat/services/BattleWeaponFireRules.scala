package services.battle.microservices.combat.services

import cats.effect.IO

import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.world.services.BattleMotionRules.*
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.combat.objects.combat.{BattleWeaponFireDefinition, BattleWeaponRecoilStrength}
import services.battle.microservices.combat.services.BattleWeaponRules.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.combat.objects.weapon.BattleWeaponHeat
import services.battle.objects.core.{BattleAggregateState, BattleVector2, ClientCommandSeq}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.weapon.{BattleWeaponState, BattleWeaponThermalState}

private[battle] object BattleWeaponFireRules {
  def applyPrimaryFire(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    currentWeapon(shooter).flatMap {
      case Some(weapon) if weapon.weaponKind == WeaponKind.Pistol =>
        canFireMagazineWeapon(weapon).flatMap {
          case true  => fireMagazineWeapon(state, shooter, weapon, commandSeq, WeaponKind.Pistol, pistol = true, arena = arena, battleRules = battleRules)
          case false => handleCannotFireWeapon(state, shooter, weapon, battleRules)
        }
      case Some(weapon) if weapon.weaponKind == WeaponKind.RocketLauncher =>
        canFireMagazineWeapon(weapon).flatMap {
          case true  => fireMagazineWeapon(state, shooter, weapon, commandSeq, WeaponKind.RocketLauncher, pistol = false, arena = arena, battleRules = battleRules)
          case false => handleCannotFireWeapon(state, shooter, weapon, battleRules)
        }
      case Some(weapon) if weapon.weaponKind == WeaponKind.Gatling =>
        canFireHeatWeapon(weapon).flatMap {
          case true  => fireHeatWeapon(state, shooter, weapon, commandSeq, WeaponKind.Gatling, arena, battleRules)
          case false => handleCannotFireWeapon(state, shooter, weapon, battleRules)
        }
      case Some(weapon) if weapon.weaponKind == WeaponKind.Shotgun =>
        canFireMagazineWeapon(weapon).flatMap {
          case true  => fireMagazineWeapon(state, shooter, weapon, commandSeq, WeaponKind.Shotgun, pistol = false, arena = arena, battleRules = battleRules)
          case false => handleCannotFireWeapon(state, shooter, weapon, battleRules)
        }
      case Some(weapon) =>
        handleCannotFireWeapon(state, shooter, weapon, battleRules)
      case _ => IO.pure(state)
    }

  def resolveRequestedReloads(state: BattleAggregateState, battleRules: BattleDynamicRuleBook): IO[BattleAggregateState] =
    state.players.foldLeft(IO.pure(state)) { (currentStateIO, snapshotPlayer) =>
      currentStateIO.flatMap { currentState =>
        currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
          case Some(player) if player.alive && player.reloadPressed =>
            currentWeapon(player).flatMap {
              case Some(weapon) =>
                canStartMagazineReload(weapon, battleRules).flatMap {
                  case true =>
                    for
                      reloadingWeapon <- startMagazineReload(weapon, battleRules)
                      updatedPlayer <- updateCurrentWeapon(player.copy(reloadPressed = false), reloadingWeapon)
                      nextState <- replacePlayer(currentState, updatedPlayer)
                    yield nextState
                  case false =>
                    replacePlayer(currentState, player.copy(reloadPressed = false))
                }
              case _ =>
                replacePlayer(currentState, player.copy(reloadPressed = false))
            }
          case _ => IO.pure(currentState)
        }
      }
    }

  def runtimeFireCommandSeq(state: BattleAggregateState, player: BattlePlayerState): IO[ClientCommandSeq] =
    IO.pure(ClientCommandSeq(-(((state.tick.value + 1L) * 1_000L) + player.seat.value.toLong + 1L)))

  private def handleCannotFireWeapon(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    weapon: BattleWeaponState,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    shouldAutoReload(weapon, battleRules).flatMap {
      case true =>
        for
          reloadingWeapon <- startMagazineReload(weapon, battleRules)
          updatedPlayer <- updateCurrentWeapon(shooter, reloadingWeapon)
          nextState <- replacePlayer(state, updatedPlayer)
        yield nextState
      case false => IO.pure(state)
    }

  private def fireMagazineWeapon(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    weapon: BattleWeaponState,
    commandSeq: ClientCommandSeq,
    weaponKind: WeaponKind,
    pistol: Boolean,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    for
      definition <- fireDefinition(weaponKind, battleRules)
      chargedWeapon <- chargeMagazineWeapon(weapon, definition.cooldownMs, battleRules)
      chargedShooter <- updateCurrentWeapon(shooter, chargedWeapon)
      recoiledShooter <- applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength, arena)
      replacedState <- replacePlayer(state, recoiledShooter)
      birthOffset <- projectileBirthOffset(definition.projectile.radius.value, arena)
      nextState <-
        if pistol then
          BattleProjectileFactoryRules.resolvePistolShot(
            replacedState,
            chargedShooter,
            commandSeq,
            definition.projectile,
            birthOffset,
            normalizeMovement
          )
        else
          for
            projectiles <- BattleProjectileFactoryRules.weaponProjectiles(
              shooter = chargedShooter,
              commandSeq = commandSeq,
              projectile = definition.projectile,
              projectileBirthOffset = birthOffset,
              normalizeMovement = normalizeMovement
            )
          yield replacedState.copy(projectiles = replacedState.projectiles ++ projectiles)
    yield nextState

  private def fireHeatWeapon(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    weapon: BattleWeaponState,
    commandSeq: ClientCommandSeq,
    weaponKind: WeaponKind,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    for
      definition <- fireDefinition(weaponKind, battleRules)
      chargedWeapon <- chargeHeatWeapon(weapon, definition)
      chargedShooter <- updateCurrentWeapon(shooter, chargedWeapon)
      recoiledShooter <- applyWeaponRecoil(chargedShooter, chargedShooter.aim, definition.recoilStrength, arena)
      replacedState <- replacePlayer(state, recoiledShooter)
      birthOffset <- projectileBirthOffset(definition.projectile.radius.value, arena)
      projectiles <- BattleProjectileFactoryRules.weaponProjectiles(
        shooter = chargedShooter,
        commandSeq = commandSeq,
        projectile = definition.projectile,
        projectileBirthOffset = birthOffset,
        normalizeMovement = normalizeMovement
      )
    yield replacedState.copy(projectiles = replacedState.projectiles ++ projectiles)

  private def applyWeaponRecoil(
    player: BattlePlayerState,
    direction: BattleVector2,
    recoilStrength: BattleWeaponRecoilStrength,
    arena: BattleArenaContext
  ): IO[BattlePlayerState] = {
    val recoilDistance = math.min(24.0, math.max(0.0, recoilStrength.value) * 0.18)
    for
      recoilVector <- scale(direction, -1.0)
      recoilDirection <- normalizeMovement(recoilVector)
      recoilDirectionLength <- vectorLength(recoilDirection)
      result <-
        if recoilDistance <= 0.0 || recoilDirectionLength <= 0.0001 then IO.pure(player)
        else
          findMotionDestination(
            position = player.position,
            direction = recoilDirection,
            distance = recoilDistance,
            radius = arena.playerCollisionRadius,
            arena = arena
          ).map(motion => player.copy(position = motion.destination))
    yield result
  }

  private def chargeHeatWeapon(
    weapon: BattleWeaponState,
    definition: BattleWeaponFireDefinition
  ): IO[BattleWeaponState] =
    definition.heat match {
      case None => IO.pure(weapon)
      case Some(heatDefinition) =>
        val heatAfter = math.min(
          heatDefinition.maxHeat.value,
          weapon.heat.value + heatDefinition.heatPerShot.value
        )
        val overheated = heatAfter >= heatDefinition.maxHeat.value
        IO.pure(weapon.copy(
          fireCooldownMs = definition.cooldownMs,
          heat = BattleWeaponHeat(heatAfter),
          thermalState =
            if overheated then BattleWeaponThermalState.overheated(heatDefinition.overheatLockMs)
            else weapon.thermalState
        ))
    }

  private def projectileBirthOffset(projectileRadius: Double, arena: BattleArenaContext): IO[Double] =
    IO.pure(
      arena.playerCollisionRadius +
        projectileRadius +
        arena.projectileBirthClearance
    )

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): IO[BattleAggregateState] =
    IO.pure(state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing)))
}
