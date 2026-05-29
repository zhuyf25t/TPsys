package services.battle.microservices.actors.services

import services.battle.microservices.world.services.BattleArenaCollision.*
import services.battle.microservices.world.services.BattleArenaCatalog
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.world.services.BattleInitialLayout.*
import services.battle.microservices.actors.services.BattleInputRules.*
import services.battle.microservices.world.services.BattleMotionRules.*
import services.battle.microservices.actors.database.BattleBotRuleBook
import services.battle.microservices.combat.services.BattleWeaponRules.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.abilities.objects.pickup.PickupKind
import services.battle.objects.core.{BattleAggregateState, BattleVector2, FacingRadians, SpawnPointIndex}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.actors.objects.player.BattlePlayerState

private[battle] object BattleBotRules {
  private final case class BotTarget(
    player: BattlePlayerState,
    distance: Double,
    visible: Boolean
  )

  def applyBotControl(player: BattlePlayerState, state: BattleAggregateState): BattlePlayerState = {
    val target = selectTarget(player, state)
    val visibleThreat = target.exists(_.visible)
    val reloadPressed = shouldBotReload(player, visibleThreat)
    val movement =
      if reloadPressed then reloadMovement(player, target, state)
      else target match {
        case Some(botTarget) => combatMovement(player, botTarget, state)
        case None            => objectiveMovement(player, state)
      }
    val aim =
      target
        .map(botTarget => aimAtTarget(player, botTarget, state))
        .getOrElse(normalizeAim(player.aim, if vectorLength(movement) > 0.0001 then movement else player.aim))
    val primaryHeld =
      !reloadPressed && target.exists(botTarget => shouldFireAtTarget(player, botTarget, state))

    player.copy(
      aim = aim,
      facing = FacingRadians(math.atan2(aim.y, aim.x)),
      movement = movement,
      sprint = false,
      primaryHeld = primaryHeld,
      reloadPressed = reloadPressed
    )
  }

  private def selectTarget(player: BattlePlayerState, state: BattleAggregateState): Option[BotTarget] = {
    val targets = state.players
      .filter(candidate => candidate.playerId != player.playerId && candidate.alive)
      .map(candidate =>
        BotTarget(
          player = candidate,
          distance = distanceBetween(player.position, candidate.position),
          visible = hasArenaLineOfSight(player.position, candidate.position)
        )
      )
    val visibleTargets = targets.filter(_.visible)
    val visibleOrAll = if visibleTargets.nonEmpty then visibleTargets else targets
    val humanTargets = visibleOrAll.filterNot(_.player.isBot)
    val preferredTargets = if humanTargets.nonEmpty then humanTargets else visibleOrAll

    preferredTargets.sortBy(targetScore).headOption
  }

  private def targetScore(target: BotTarget): Double = {
    val visibilityPenalty = if target.visible then 0.0 else 380.0
    val humanPenalty = if target.player.isBot then 120.0 else 0.0
    val lowHealthBonus = (target.player.maxHp.value - target.player.hp.value) * -1.6
    target.distance + visibilityPenalty + humanPenalty + lowHealthBonus
  }

  private def aimAtTarget(player: BattlePlayerState, target: BotTarget, state: BattleAggregateState): BattleVector2 = {
    val botRules = BattleBotRuleBook.current
    val lead = scale(target.player.movement, botRules.aimLeadDistance.value)
    val base = subtract(target.player.position, player.position)
    val wobbleDirection =
      if ((state.tick.value + player.seat.value.toLong + target.player.seat.value.toLong) % 2L) == 0L then 1.0
      else -1.0
    val aimNoise = scale(perpendicular(normalizeAim(player.aim, base), wobbleDirection), botRules.aimErrorRadius.value)
    normalizeAim(player.aim, add(add(base, lead), aimNoise))
  }

  private def combatMovement(player: BattlePlayerState, target: BotTarget, state: BattleAggregateState): BattleVector2 = {
    val toTarget = subtract(target.player.position, player.position)
    val aim = normalizeAim(player.aim, toTarget)
    val desired =
      if shouldRetreat(player, target) then coverOrRetreatDirection(player, target)
      else if !target.visible then flankDirection(player, aim, state)
      else {
        val orbit = perpendicular(aim, orbitDirection(player, state))
        val botRules = BattleBotRuleBook.current
        val radial =
          if target.distance > botRules.preferredRange.value + botRules.preferredRangeAdvanceMargin.value then aim
          else if target.distance < botRules.preferredRange.value - botRules.preferredRangeRetreatMargin.value then scale(aim, -1.0)
          else BattleArenaCatalog.ZeroVector
        add(scale(radial, 0.86), scale(orbit, 0.52))
      }

    chooseOpenMovement(player, desired)
  }

  private def reloadMovement(
    player: BattlePlayerState,
    target: Option[BotTarget],
    state: BattleAggregateState
  ): BattleVector2 =
    target match {
      case Some(botTarget) => chooseOpenMovement(player, coverOrRetreatDirection(player, botTarget))
      case None            => objectiveMovement(player, state)
    }

  private def objectiveMovement(player: BattlePlayerState, state: BattleAggregateState): BattleVector2 =
    pickupObjective(player, state) match {
      case Some(pickup) => chooseOpenMovement(player, subtract(pickup.position, player.position))
      case None         => chooseOpenMovement(player, subtract(patrolTarget(player, state), player.position))
    }

  private def pickupObjective(player: BattlePlayerState, state: BattleAggregateState): Option[BattlePickupState] = {
    val availablePickups = state.pickups.filter(_.available)
    val botRules = BattleBotRuleBook.current
    val needsHealth = player.hp.value <= player.maxHp.value * botRules.pickupHealthRatio
    val needsWeapon =
      currentWeapon(player).forall(_.weaponKind == WeaponKind.Pistol) ||
        currentWeapon(player).exists(weapon =>
          !weaponUsesHeat(weapon.weaponKind) &&
            weapon.ammoInMagazine.value <= math.max(1, math.floor(weapon.magazineSize.value * botRules.tacticalReloadRatio).toInt)
        )
    val preferred =
      if needsHealth then availablePickups.filter(_.pickupKind == PickupKind.Medkit)
      else if needsWeapon then availablePickups.filter(_.pickupKind == PickupKind.Weapon)
      else availablePickups.filter(pickup => distanceBetween(player.position, pickup.position) <= botRules.pickupSeekRange.value)

    preferred.sortBy(pickup => distanceBetween(player.position, pickup.position)).headOption
  }

  private def shouldRetreat(player: BattlePlayerState, target: BotTarget): Boolean = {
    val botRules = BattleBotRuleBook.current
    val lowHealth = player.hp.value <= player.maxHp.value * botRules.lowHealthRatio
    lowHealth || target.distance < botRules.preferredRange.value - botRules.preferredRangeRetreatMargin.value
  }

  private def coverOrRetreatDirection(player: BattlePlayerState, target: BotTarget): BattleVector2 = {
    val away = normalizeMovement(subtract(player.position, target.player.position))
    val candidates = Vector(
      away,
      add(scale(away, 0.82), scale(perpendicular(away, 1.0), 0.58)),
      add(scale(away, 0.82), scale(perpendicular(away, -1.0), 0.58)),
      perpendicular(away, 1.0),
      perpendicular(away, -1.0)
    )

    candidates
      .map(normalizeMovement)
      .filter(vectorLength(_) > 0.0001)
      .maxByOption { direction =>
        val probe = clampToPlayable(add(player.position, scale(direction, BattleBotRuleBook.current.coverProbeDistance.value)))
        val coverBonus = if hasArenaLineOfSight(target.player.position, probe) then 0.0 else 900.0
        val occupancyBonus = if canPlayerOccupy(probe, BattleArenaCatalog.PlayerCollisionRadius) then 180.0 else -900.0
        coverBonus + occupancyBonus + distanceBetween(probe, target.player.position)
      }
      .getOrElse(away)
  }

  private def flankDirection(player: BattlePlayerState, aim: BattleVector2, state: BattleAggregateState): BattleVector2 =
    normalizeMovement(add(scale(aim, 0.58), scale(perpendicular(aim, orbitDirection(player, state)), 0.92)))

  private def chooseOpenMovement(player: BattlePlayerState, desired: BattleVector2): BattleVector2 = {
    val normalized = normalizeMovement(desired)
    if vectorLength(normalized) <= 0.0001 then BattleArenaCatalog.ZeroVector
    else {
      val candidates = Vector(
        normalized,
        rotate(normalized, math.Pi / 6.0),
        rotate(normalized, -math.Pi / 6.0),
        rotate(normalized, math.Pi / 3.0),
        rotate(normalized, -math.Pi / 3.0),
        rotate(normalized, math.Pi / 2.0),
        rotate(normalized, -math.Pi / 2.0),
        scale(normalized, -1.0)
      )

      val best = candidates
        .map(normalizeMovement)
        .map(direction =>
          val motion = findMotionDestination(
            position = player.position,
            direction = direction,
            distance = BattleBotRuleBook.current.movementProbeDistance.value,
            radius = BattleArenaCatalog.PlayerCollisionRadius
          )
          direction -> distanceBetween(player.position, motion.destination)
        )
        .filter { case (_, distance) => distance > 4.0 }
        .maxByOption { case (_, distance) => distance }

      best.map(_._1).getOrElse(normalizeMovement(perpendicular(player.aim, 1.0)))
    }
  }

  private def shouldFireAtTarget(player: BattlePlayerState, target: BotTarget, state: BattleAggregateState): Boolean =
    target.visible &&
      target.distance <= botFireRangeForTarget(target.player) &&
      canBotFireAtTarget(state) &&
      isBotFirePulseOpen(player, state) &&
      currentWeapon(player).exists(weapon =>
        weapon.reloadRemainingMs.value <= 0 &&
          (weaponUsesHeat(weapon.weaponKind) || weapon.ammoInMagazine.value > 0)
      )

  private def shouldBotReload(player: BattlePlayerState, visibleThreat: Boolean): Boolean =
    player.isBot && currentWeapon(player).exists(weapon =>
      val emptyReload = weapon.ammoInMagazine.value <= 0 && canStartMagazineReload(weapon)
      val tacticalReload =
        !visibleThreat &&
          !weaponUsesHeat(weapon.weaponKind) &&
          weapon.ammoInMagazine.value <= math.max(1, math.floor(weapon.magazineSize.value * BattleBotRuleBook.current.tacticalReloadRatio).toInt) &&
          canStartMagazineReload(weapon)
      emptyReload || tacticalReload
    )

  private def canBotFireAtTarget(state: BattleAggregateState): Boolean =
    state.elapsedMs.value >= BattleBotRuleBook.current.openingFireDelay.value

  private def isBotFirePulseOpen(player: BattlePlayerState, state: BattleAggregateState): Boolean = {
    val botRules = BattleBotRuleBook.current
    val interval = math.max(1L, botRules.firePulseInterval.value)
    val window = math.max(1L, math.min(botRules.firePulseWindow.value, interval))
    val rawPhase = (state.elapsedMs.value + player.seat.value.toLong * 97L) % interval
    val phase = if rawPhase < 0L then rawPhase + interval else rawPhase
    phase < window
  }

  private def botFireRangeForTarget(target: BattlePlayerState): Double =
    val botRules = BattleBotRuleBook.current
    if target.isBot then botRules.botFireRange.value
    else botRules.humanFireRange.value

  private def patrolTarget(player: BattlePlayerState, state: BattleAggregateState): BattleVector2 = {
    val spawnAnchor = spawnPointFor(SpawnPointIndex(player.seat.value))
    val patrolAngle = (state.tick.value + player.seat.value.toLong * 11L).toDouble * 0.18
    clampToPlayable(BattleVector2(
      spawnAnchor.x + math.cos(patrolAngle) * 260.0,
      spawnAnchor.y + math.sin(patrolAngle) * 190.0
    ))
  }

  private def orbitDirection(player: BattlePlayerState, state: BattleAggregateState): Double =
    if (state.tick.value + player.seat.value.toLong) % 2L == 0L then 1.0
    else -1.0

  private def rotate(vector: BattleVector2, radians: Double): BattleVector2 =
    BattleVector2(
      vector.x * math.cos(radians) - vector.y * math.sin(radians),
      vector.x * math.sin(radians) + vector.y * math.cos(radians)
    )

  private def clampToPlayable(point: BattleVector2): BattleVector2 =
    BattleVector2(
      clampDouble(
        point.x,
        BattleArenaCatalog.PlayerCollisionRadius,
        BattleArenaCatalog.WorldSize.x - BattleArenaCatalog.PlayerCollisionRadius
      ),
      clampDouble(
        point.y,
        BattleArenaCatalog.PlayerCollisionRadius,
        BattleArenaCatalog.WorldSize.y - BattleArenaCatalog.PlayerCollisionRadius
      )
    )
}
