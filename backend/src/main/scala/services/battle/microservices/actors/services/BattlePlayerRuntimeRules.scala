package services.battle.microservices.actors.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.actors.services.BattleBotRules.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.world.services.BattleMotionRules.*
import services.battle.microservices.actors.services.BattlePlayerLifecycleRules.*
import services.battle.microservices.runtime.services.BattleEventFactory.*
import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.microservices.combat.services.BattleWeaponRules.*
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.runtime.objects.event.BattleEventKind
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.combat.objects.weapon.BattleWeaponHeat
import services.battle.objects.core.{BattleAggregateState, CooldownMillis, DurationMillis}
import services.battle.microservices.actors.objects.player.{
  BattlePlayerLifeState,
  BattlePlayerState,
  HitPoints,
  KillCount,
  Score,
  Stamina
}
import services.battle.microservices.abilities.objects.skill.BattleSlowFieldState
import services.battle.microservices.combat.objects.weapon.{BattleWeaponState, BattleWeaponThermalState}

private[battle] object BattlePlayerRuntimeRules {
  def advancePlayers(
    state: BattleAggregateState,
    deltaMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    val nextElapsed = state.elapsedMs.value
    val previousElapsed = math.max(0L, nextElapsed - deltaMs)
    BattleArenaCatalog.contextFor(state.mapId, battleRules).flatMap { arena =>
      state.players.traverse { player =>
        for
          _ <- IO.cede
          withTimers <- advancePlayerTimers(player, deltaMs, previousElapsed, nextElapsed, battleRules)
          controlledPlayer <-
            if withTimers.alive && withTimers.isBot then applyBotControl(withTimers, state, arena, battleRules)
            else IO.pure(withTimers)
          advancedPlayer <-
            if controlledPlayer.alive then movePlayer(controlledPlayer, deltaMs, state.slowFields, previousElapsed, nextElapsed, arena, battleRules)
            else clearDeadPlayerRuntime(controlledPlayer)
        yield advancedPlayer
      }.map(advancedPlayers => state.copy(players = advancedPlayers))
        .flatMap(nextState => resolveZombieContactEliminations(nextState, arena, battleRules))
    }
  }

  def advancePlayerTimers(
    player: BattlePlayerState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] =
    for
      timedSkills <- player.skills.traverse { skill =>
        for
          cooldownMs <- decrementInt(skill.cooldownMs.value, deltaMs)
          activeMs <- decrementLong(skill.activeMs.value, deltaMs)
        yield skill.copy(
          cooldownMs = CooldownMillis(cooldownMs),
          activeMs = DurationMillis(activeMs)
        )
      }
      withSkills = player.copy(skills = timedSkills)
      timedWeapons <- withSkills.weapons.zipWithIndex.traverse { case (weapon, index) =>
        for
          heatAdvanced <- advanceWeaponHeat(weapon, deltaMs, previousElapsed, nextElapsed, battleRules)
          advanced <-
            if index == withSkills.currentWeaponIndex then
              for
                fireCooldownMs <- decrementInt(heatAdvanced.fireCooldownMs.value, deltaMs)
                timerWeapon = heatAdvanced.copy(fireCooldownMs = CooldownMillis(fireCooldownMs))
                nextWeapon <-
                  if timerWeapon.reloadRemainingMs.value <= 0 then IO.pure(timerWeapon)
                  else
                    decrementInt(timerWeapon.reloadRemainingMs.value, deltaMs).flatMap { remaining =>
                      if remaining > 0 then IO.pure(timerWeapon.copy(reloadRemainingMs = CooldownMillis(remaining)))
                      else finishReload(timerWeapon)
                    }
              yield nextWeapon
            else IO.pure(heatAdvanced)
        yield advanced
      }
      result <-
        if timedWeapons.isEmpty then IO.pure(withSkills)
        else
          clampWeaponIndex(withSkills.currentWeaponIndex, timedWeapons.length).map { currentIndex =>
            withSkills.copy(
              currentWeaponIndex = currentIndex,
              currentWeaponKind = timedWeapons(currentIndex).weaponKind,
              weapons = timedWeapons
            )
          }
    yield result

  def movePlayer(
    player: BattlePlayerState,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState],
    previousElapsed: Long,
    nextElapsed: Long,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    val botMoveSpeedIO =
      if player.isBot then battleRules.bot.map(_.moveSpeed.value)
      else IO.pure(0.0)

    for
      botMoveSpeed <- botMoveSpeedIO
      movementRules <- battleRules.movement
      movementLength <- vectorLength(player.movement)
      hasMovement = movementLength > 0.0 && deltaMs > 0L
      canSprint = player.sprint && hasMovement && player.stamina.value > 0.0
      nextStamina <- advanceStamina(
        player = player,
        sprinting = canSprint,
        deltaMs = deltaMs,
        previousElapsed = previousElapsed,
        nextElapsed = nextElapsed,
        staminaDrainPerSecond = movementRules.staminaDrainPerSecond.value,
        staminaRecoverPerSecond = movementRules.staminaRecoverPerSecond.value
      )
      withStamina = player.copy(stamina = nextStamina, sprint = canSprint)
      movedPlayer <-
        if !hasMovement then IO.pure(withStamina)
        else
          val baseSpeed =
            if player.isBot then botMoveSpeed
            else if canSprint then movementRules.sprintSpeed.value
            else movementRules.walkSpeed.value
          for
            slowed <- slowFields.existsM(field => distanceBetween(player.position, field.position).map(_ <= field.radius.value))
            slowFactor <- IO.pure(if slowed then movementRules.slowFieldMovementFactor.value else 1.0)
            distance <- IO.pure(baseSpeed * slowFactor * deltaMs.toDouble / 1000.0)
            motion <- findMotionDestination(
              position = player.position,
              direction = player.movement,
              distance = distance,
              radius = arena.playerCollisionRadius,
              arena = arena
            )
          yield withStamina.copy(position = motion.destination)
    yield movedPlayer
  }

  private def advanceWeaponHeat(
    weapon: BattleWeaponState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleWeaponState] =
    heatDefinition(weapon.weaponKind, battleRules).flatMap {
      case None =>
        IO.pure(weapon.copy(heat = BattleWeaponHeat(0), thermalState = BattleWeaponThermalState.Ready))
      case Some(heatDefinition) =>
        for
          overheatRemainingMs <- decrementInt(weapon.overheatRemainingMs.value, deltaMs)
          heatDelta <- elapsedRateDelta(heatDefinition.coolRatePerSecond.value, previousElapsed, nextElapsed)
        yield
          val timerWeapon = weapon.copy(
            thermalState = BattleWeaponThermalState.overheated(CooldownMillis(overheatRemainingMs))
          )
          timerWeapon.copy(heat = BattleWeaponHeat(math.max(0, timerWeapon.heat.value - heatDelta)))
    }

  private def advanceStamina(
    player: BattlePlayerState,
    sprinting: Boolean,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long,
    staminaDrainPerSecond: Double,
    staminaRecoverPerSecond: Double
  ): IO[Stamina] =
    if deltaMs <= 0L then IO.pure(player.stamina)
    else
      val delta =
        if sprinting then -staminaDrainPerSecond
        else staminaRecoverPerSecond
      elapsedRateDeltaDouble(math.abs(delta), previousElapsed, nextElapsed).map { staminaDelta =>
        val signedDelta = if delta < 0 then -staminaDelta else staminaDelta
        Stamina(math.max(0, math.min(player.maxStamina.value, player.stamina.value + signedDelta)))
      }

  private val ZombieContactRadiusScale = 2.4

  private def resolveZombieContactEliminations(
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    val contactRadius = arena.playerCollisionRadius * ZombieContactRadiusScale
    battleRules.history.flatMap { historyRules =>
      state.players.filter(player => player.alive && !player.isBot).foldLeft(IO.pure(state)) {
        case (stateIO, targetSnapshot) =>
          stateIO.flatMap { currentState =>
            currentState.players.find(player =>
              player.playerId == targetSnapshot.playerId && player.alive && !player.isBot
            ) match {
              case None =>
                IO.pure(currentState)
              case Some(target) =>
                nearestContactZombie(currentState, target, contactRadius).flatMap {
                  case None =>
                    IO.pure(currentState)
                  case Some(zombie) =>
                    eliminateByZombieContact(
                      state = currentState,
                      zombie = zombie,
                      target = target,
                      retainedBattleEventCount = historyRules.retainedBattleEventCount.value
                    )
                }
            }
          }
      }
    }
  }

  private def nearestContactZombie(
    state: BattleAggregateState,
    target: BattlePlayerState,
    contactRadius: Double
  ): IO[Option[BattlePlayerState]] =
    state.players
      .filter(player => player.alive && player.isBot)
      .traverse(zombie => distanceBetween(zombie.position, target.position).map(distance => zombie -> distance))
      .map(_.filter { case (_, distance) => distance <= contactRadius }.sortBy(_._2).headOption.map(_._1))

  private def eliminateByZombieContact(
    state: BattleAggregateState,
    zombie: BattlePlayerState,
    target: BattlePlayerState,
    retainedBattleEventCount: Int
  ): IO[BattleAggregateState] =
    for
      eliminatedTarget <- clearDeadPlayerRuntime(target.copy(
        hp = HitPoints(0),
        lifeState = BattlePlayerLifeState.eliminated(
          target.eliminatedAtMs.orElse(Some(state.elapsedMs)),
          DurationMillis(0L)
        )
      ))
      creditedZombie = zombie.copy(
        score = Score(zombie.score.value + 1),
        kills = KillCount(zombie.kills.value + 1)
      )
      players = state.players.map { player =>
        if player.playerId == eliminatedTarget.playerId then eliminatedTarget
        else if player.playerId == creditedZombie.playerId then creditedZombie
        else player
      }
      stateWithPlayers = state.copy(players = players)
      event <- battleEvent(
        stateWithPlayers,
        BattleEventKind.Kill,
        creditedZombie,
        eliminatedTarget,
        Some(s"${creditedZombie.displayName.value} infected ${eliminatedTarget.displayName.value}"),
        None
      )
      retainedEvents = (stateWithPlayers.events :+ event).takeRight(retainedBattleEventCount)
    yield stateWithPlayers.copy(events = retainedEvents)
}
