package slaydemo.backend.battle.runtime

import scala.collection.mutable

import slaydemo.backend.battle.runtime.BattleContentCatalog._
import slaydemo.backend.battle.api.{BattleCommandRequest, BattleCommandSkillOutcome, BattleCommandVector}
import slaydemo.backend.battle.objects.{
  BattleAggregateState,
  BattleEventParticipant,
  BattleEventState,
  BattlePickupState,
  BattlePlayerState,
  BattlePlayerSkillState,
  BattleProjectileState,
  BattleProjectileTerminalState,
  BattleSlowFieldState,
  BattleReplayFrameState,
  BattleReplayHeroFrameState,
  BattleReplayPickupFrameState,
  BattleReplayProjectileFrameState,
  BattleWeaponState,
  BattleSessionDescriptor,
  BattleVector2
}
import slaydemo.backend.shared.objects.UserId

final class InMemoryAuthoritativeBattleRuntime(
  configuredBattleDurationMs: Long = InMemoryAuthoritativeBattleRuntime.DefaultBattleDurationMs
) extends BattleRuntime {
  private final case class PlayerControl(
    movement: BattleVector2,
    aim: BattleVector2,
    primaryHeld: Boolean,
    sprint: Boolean,
    reloadPressed: Boolean
  )
  private final case class AdvancedProjectile(
    projectile: BattleProjectileState,
    start: BattleVector2,
    end: BattleVector2,
    ttlBefore: Long,
    block: Option[ProjectileBlock]
  )
  private final case class ProjectileBlock(
    blockedAtT: Double,
    reason: String
  )
  private final case class ProjectileHitCandidate(
    playerIndex: Int,
    hitT: Double
  )
  private final case class PlayerSkillCastResult(
    player: BattlePlayerState,
    outcome: BattleCommandSkillOutcome
  )
  private final case class FreezeSkillCastResult(
    player: BattlePlayerState,
    slowFields: Vector[BattleSlowFieldState],
    outcome: BattleCommandSkillOutcome
  )
  private val battleDurationMs = math.max(1L, configuredBattleDurationMs)
  private val latestHumanSprints = mutable.Map.empty[(String, String), Boolean]

  override def createBattle(roomId: String, descriptor: BattleSessionDescriptor, now: Long): BattleAggregateState = {
    latestHumanSprints.keys.filter(_._1 == descriptor.battleId).toVector.foreach(latestHumanSprints.remove)
    val startedAt = if (descriptor.startedAt > 0L) descriptor.startedAt else now
    val elapsedMs = elapsedAt(startedAt, battleDurationMs, now)
    val players = descriptor.bootstrap.seats.toVector.sortBy(_.seat).map { seat =>
      val spawnAnchor = spawnPointFor(seat.spawnPointIndex)
      BattlePlayerState(
        playerId = UserId(seat.playerId),
        heroId = seat.heroId,
        handle = seat.handle,
        displayName = seat.displayName,
        seat = seat.seat,
        isBot = seat.isBot,
        spawnAnchor = spawnAnchor,
        position = spawnAnchor,
        velocity = zeroVector,
        aim = BattleVector2(1.0, 0.0),
        facing = 0.0,
        movementIntent = zeroVector,
        primaryHeld = false,
        reloadPressed = false,
        lastClientCommandSeq = 0L,
        currentWeaponIndex = 0,
        weapons = Vector(initialWeapon(pistolWeaponKind)),
        currentWeaponKind = pistolWeaponKind,
        ammoInMagazine = weaponDefinitions(pistolWeaponKind).magazineSize,
        magazineSize = weaponDefinitions(pistolWeaponKind).magazineSize,
        reserveAmmo = weaponDefinitions(pistolWeaponKind).reserveAmmo,
        fireCooldownMs = 0L,
        reloadRemainingMs = 0L,
        hp = defaultMaxHp,
        maxHp = defaultMaxHp,
        stamina = playerMaxStamina,
        maxStamina = playerMaxStamina,
        score = 0,
        kills = 0,
        skills = initialSkills,
        alive = true,
        eliminatedAtMs = None,
        respawnMs = 0L
      )
    }
    val phase = if (shouldFinishBattle(players, elapsedMs, battleDurationMs)) "finished" else "active"
    val aggregatePlayers = if (phase == "finished") players.map(clearFinishedPlayerRuntime) else players
    val initialPickupsState = initialPickups
    val initialReplayFrames = Vector(captureReplayFrame(0L, players, Vector.empty, initialPickupsState))
    val replayFrames =
      if (phase == "finished") {
        appendReplayFrame(initialReplayFrames, elapsedMs, aggregatePlayers, Vector.empty, initialPickupsState)
      } else {
        initialReplayFrames
      }
    val initialWinner = resolveWinningPlayer(aggregatePlayers)

    BattleAggregateState(
      battleId = slaydemo.backend.shared.objects.BattleId(descriptor.battleId),
      roomId = roomId,
      phase = phase,
      serverTime = now,
      startedAt = startedAt,
      durationMs = battleDurationMs,
      elapsedMs = elapsedMs,
      endsAt = startedAt + battleDurationMs,
      worldSize = authoritativeWorldSize,
      tick = 0L,
      players = aggregatePlayers,
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = initialPickupsState,
      replayFrames = replayFrames,
      events = Vector.empty,
      winnerPlayerId = if (phase == "finished") initialWinner.map(_.playerId) else None,
      winnerHeroId = if (phase == "finished") initialWinner.map(_.heroId) else None
    )
  }

  override def step(state: BattleAggregateState, deltaMs: Long, now: Long): BattleAggregateState =
    if (state.phase != "active") {
      state.copy(
        serverTime = now,
        players = if (state.phase == "finished") state.players.map(clearFinishedPlayerRuntime) else state.players
      )
    } else {
      val currentElapsedMs = elapsedAt(state, now)
      if (shouldFinishBattle(state.players, currentElapsedMs, state.durationMs)) {
        finishBattle(state.copy(serverTime = now, elapsedMs = currentElapsedMs), state.players, Vector.empty)
      } else if (deltaMs <= 0) {
        state.copy(
          serverTime = now,
          elapsedMs = currentElapsedMs
        )
      } else {
        val stepStartElapsedMs = elapsedAt(state.startedAt, state.durationMs, now - deltaMs)
        val effectiveDeltaMs = math.min(deltaMs, remainingDurationMs(state, stepStartElapsedMs))
        if (effectiveDeltaMs <= 0L) {
          finishBattle(state.copy(serverTime = now, elapsedMs = state.durationMs), state.players, Vector.empty)
        } else {
          val elapsedSeconds = effectiveDeltaMs.toDouble / 1000.0
          val advancedSlowFields = advanceSlowFields(state.slowFields, effectiveDeltaMs)
          val advancedPlayers = advancePlayers(
            state.players,
            state,
            elapsedSeconds,
            effectiveDeltaMs,
            advancedSlowFields
          )
          val (playersAfterShots, spawnedProjectiles) = resolveShots(advancedPlayers, state.tick + 1L)
          val advancedProjectiles = advanceProjectiles(
            state.projectiles ++ spawnedProjectiles,
            elapsedSeconds,
            effectiveDeltaMs,
            advancedSlowFields
          )
          val nextElapsedMs = elapsedAt(state, now)
          val (playersAfterHits, survivingProjectiles, killEvents, projectileTerminals) =
            resolveProjectileHits(playersAfterShots, advancedProjectiles, nextElapsedMs)
          val advancedPickups = advancePickups(state.pickups, effectiveDeltaMs)
          val (playersAfterPickups, nextPickups, pickupEvents) =
            resolvePickups(playersAfterHits, advancedPickups, nextElapsedMs)
          val finished = shouldFinishBattle(playersAfterPickups, nextElapsedMs, state.durationMs)
          val nextPlayers = if (finished) playersAfterPickups.map(clearFinishedPlayerRuntime) else playersAfterPickups
          val winningPlayer = resolveWinningPlayer(nextPlayers)
          val nextEvents = state.events ++ killEvents ++ pickupEvents
          val nextProjectileTerminals =
            retainRecentProjectileTerminals(state.projectileTerminals ++ projectileTerminals)

          state.copy(
            phase = if (finished) "finished" else "active",
            serverTime = now,
            elapsedMs = nextElapsedMs,
            tick = state.tick + 1L,
            players = nextPlayers,
            projectiles = if (finished) Vector.empty else survivingProjectiles,
            projectileTerminals = nextProjectileTerminals,
            slowFields = advancedSlowFields,
            pickups = nextPickups,
            replayFrames = updateReplayFrames(
              state.replayFrames,
              nextElapsedMs,
              nextPlayers,
              if (finished) Vector.empty else survivingProjectiles,
              nextPickups,
              hasRuntimeEvents = killEvents.nonEmpty || pickupEvents.nonEmpty,
              finished = finished
            ),
            events = retainRecentEvents(nextEvents),
            winnerPlayerId = if (finished) winningPlayer.map(_.playerId) else None,
            winnerHeroId = if (finished) winningPlayer.map(_.heroId) else None
          )
        }
      }
    }

  private def advancePlayers(
    players: Vector[BattlePlayerState],
    state: BattleAggregateState,
    elapsedSeconds: Double,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState]
  ): Vector[BattlePlayerState] =
    players.map { player =>
      val control = resolvePlayerControl(player, state)
      advancePlayer(player, control, elapsedSeconds, deltaMs, slowFields)
    }

  override def applyCommand(
    state: BattleAggregateState,
    request: BattleCommandRequest,
    now: Long
  ): Either[String, BattleCommandApplication] = {
    val commandState =
      if (state.phase == "active") {
        val nextElapsedMs = elapsedAt(state, now)
        if (shouldFinishBattle(state.players, nextElapsedMs, state.durationMs)) {
          finishBattle(state.copy(serverTime = now, elapsedMs = nextElapsedMs), state.players, Vector.empty)
        } else {
          state
        }
      } else {
        state
      }
    val playerIndex = commandState.players.indexWhere(_.playerId == request.playerId)
    if (commandState.phase == "finished") {
      Right(BattleCommandApplication.ignored(
        commandState.copy(serverTime = now, players = commandState.players.map(clearFinishedPlayerRuntime)),
        "battle_finished"
      ))
    } else if (playerIndex < 0) {
      Left("player_not_found")
    } else {
      val player = commandState.players(playerIndex)
      if (player.isBot) {
        Left("bot_commands_not_supported")
      } else if (commandState.phase != "active") {
        Right(BattleCommandApplication.ignored(
          commandState.copy(serverTime = now, elapsedMs = elapsedAt(commandState, now)),
          "battle_inactive"
        ))
      } else if (!player.alive) {
        Right(BattleCommandApplication.ignored(
          commandState.copy(serverTime = now, elapsedMs = elapsedAt(commandState, now)),
          "player_dead"
        ))
      } else {
        val movementIntent = normalizeVector(request.movement)
        val aim = normalizeAim(player.aim, request.aim)
        latestHumanSprints.update(playerControlKey(commandState, player), request.sprint)
        val weaponResolvedPlayer = applyWeaponSwitchRequest(player, request.switchWeaponDirection)
        val nextPlayer = weaponResolvedPlayer.copy(
          movementIntent = movementIntent,
          aim = aim,
          facing = vectorAngle(aim, player.facing),
          primaryHeld = request.primaryHeld,
          reloadPressed = request.reloadPressed,
          lastClientCommandSeq = math.max(player.lastClientCommandSeq, request.clientCommandSeq)
        )
        val (blinkResolvedPlayer, blinkOutcomes) =
          if (request.castBlink) {
            val result = castBlinkIfReady(nextPlayer, request.pointerWorld)
            (result.player, Vector(result.outcome))
          } else {
            (nextPlayer, Vector.empty)
          }
        val (dashResolvedPlayer, dashOutcomes) =
          if (request.castDash) {
            val result = castDashIfReady(blinkResolvedPlayer, movementIntent, aim)
            (result.player, Vector(result.outcome))
          } else {
            (blinkResolvedPlayer, Vector.empty)
          }
        val (freezeResolvedPlayer, nextSlowFields, freezeOutcomes) =
          if (request.castFreeze) {
            val result = castFreezeIfReady(commandState, dashResolvedPlayer, request.pointerWorld)
            (result.player, result.slowFields, Vector(result.outcome))
          } else {
            (dashResolvedPlayer, commandState.slowFields, Vector.empty)
          }
        val outcomes = blinkOutcomes ++ dashOutcomes ++ freezeOutcomes

        Right(BattleCommandApplication.applied(
          commandState.copy(
            serverTime = now,
            elapsedMs = elapsedAt(commandState, now),
            players = commandState.players.updated(playerIndex, freezeResolvedPlayer),
            slowFields = nextSlowFields
          ),
          outcomes
        ))
      }
    }
  }

  private def advancePlayer(
    player: BattlePlayerState,
    control: PlayerControl,
    elapsedSeconds: Double,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState]
  ): BattlePlayerState =
    if (!player.alive) {
      clearWeaponRuntime(player.copy(
        velocity = zeroVector,
        movementIntent = zeroVector,
        primaryHeld = false,
        reloadPressed = false,
        respawnMs = 0L,
        skills = advanceEliminatedSkills(player.skills, deltaMs)
      ))
    } else {
      val currentWeapon = primaryWeapon(player)
      val timedWeapon = currentWeapon.copy(
        fireCooldownMs = math.max(0L, currentWeapon.fireCooldownMs - deltaMs),
        reloadRemainingMs = math.max(0L, currentWeapon.reloadRemainingMs - deltaMs)
      )
      val timedPlayer = finishReloadIfReady(
        withPrimaryWeapon(
          player.copy(
          skills = advanceSkills(player.skills, deltaMs)
          ),
          timedWeapon
        ),
        currentWeapon.reloadRemainingMs
      )
      val normalizedMovement = normalizeVector(control.movement)
      val hasMovement = !isZeroVector(normalizedMovement)
      val nextAim = normalizeAim(timedPlayer.aim, control.aim)
      val canSprint = !timedPlayer.isBot && control.sprint && hasMovement && timedPlayer.stamina > 0.0
      val nextStamina =
        if (canSprint) {
          clamp(timedPlayer.stamina - staminaDrainPerSecond * elapsedSeconds, 0.0, timedPlayer.maxStamina)
        } else {
          clamp(timedPlayer.stamina + staminaRecoverPerSecond * elapsedSeconds, 0.0, timedPlayer.maxStamina)
        }
      val moveSpeed =
        if (timedPlayer.isBot) {
          botMoveSpeedPerSecond
        } else if (canSprint) {
          playerMoveSpeedPerSecond * playerSprintMultiplier
        } else {
          playerMoveSpeedPerSecond
        }
      val speedMultiplier = slowSpeedMultiplier(timedPlayer.position, slowFields)
      val velocity = BattleVector2(
        x = normalizedMovement.x * moveSpeed * speedMultiplier,
        y = normalizedMovement.y * moveSpeed * speedMultiplier
      )
      val travelDistance = math.hypot(velocity.x * elapsedSeconds, velocity.y * elapsedSeconds)
      val resolvedMotion = AuthoritativeArenaGeometry.findMotionDestination(
        position = timedPlayer.position,
        direction = normalizedMovement,
        distance = travelDistance,
        radius = playerHitRadius
      )
      val nextPosition = resolvedMotion.destination
      val resolvedVelocity =
        if (elapsedSeconds <= 0.0) {
          zeroVector
        } else {
          BattleVector2(
            x = (nextPosition.x - timedPlayer.position.x) / elapsedSeconds,
            y = (nextPosition.y - timedPlayer.position.y) / elapsedSeconds
          )
        }

      timedPlayer.copy(
        position = nextPosition,
        velocity = resolvedVelocity,
        aim = nextAim,
        facing = vectorAngle(nextAim, timedPlayer.facing),
        movementIntent = normalizedMovement,
        primaryHeld = control.primaryHeld,
        reloadPressed = control.reloadPressed,
        stamina = nextStamina
      )
    }

  private def resolveShots(
    players: Vector[BattlePlayerState],
    nextTick: Long
  ): (Vector[BattlePlayerState], Vector[BattleProjectileState]) = {
    val nextPlayers = players.toArray
    val spawnedProjectiles = Vector.newBuilder[BattleProjectileState]

    players.indices.foreach { index =>
      val player = startReloadIfRequested(nextPlayers(index))
      val weapon = primaryWeapon(player)
      val weaponDefinition = weaponDefinitionFor(weapon.weaponKind)
      if (
        player.alive &&
        player.primaryHeld &&
        weapon.fireCooldownMs <= 0L &&
        weapon.reloadRemainingMs <= 0L &&
        weapon.ammoInMagazine > 0
      ) {
        val facing = normalizeAim(BattleVector2(1.0, 0.0), player.aim)
        val projectileCount = math.max(1, weaponDefinition.pellets)
        (0 until projectileCount).foreach { projectileIndex =>
          val projectileFacing = spreadFacing(facing, weaponDefinition, projectileIndex, projectileCount)
          val origin = add(
            player.position,
            scale(projectileFacing, playerHitRadius + weaponDefinition.projectileRadius + 4.0)
          )
          val projectileId =
            if (projectileCount == 1) s"projectile-${nextTick}-${player.seat}"
            else s"projectile-${nextTick}-${player.seat}-${projectileIndex + 1}"
          spawnedProjectiles += BattleProjectileState(
            projectileId = projectileId,
            ownerPlayerId = player.playerId,
            ownerHeroId = player.heroId,
            kind = weaponDefinition.projectileKind,
            position = origin,
            velocity = scale(projectileFacing, weaponDefinition.projectileSpeedPerSecond),
            facing = vectorAngle(projectileFacing, player.facing),
            radius = weaponDefinition.projectileRadius,
            damage = weaponDefinition.projectileDamage,
            ttlMs = weaponDefinition.projectileLifetimeMs,
            maxLifetimeMs = weaponDefinition.projectileLifetimeMs,
            splashRadius = weaponDefinition.splashRadius
          )
        }
        val firedPlayer = withPrimaryWeapon(
          player.copy(
          reloadPressed = false
          ),
          weapon.copy(
            ammoInMagazine = math.max(0, weapon.ammoInMagazine - 1),
            fireCooldownMs = weaponDefinition.cooldownMs
          )
        )
        nextPlayers(index) = startReloadIfRequested(applyWeaponRecoil(firedPlayer, facing, weaponDefinition))
      } else {
        nextPlayers(index) = player.copy(reloadPressed = false)
      }
    }

    (nextPlayers.toVector, spawnedProjectiles.result())
  }

  private def startReloadIfRequested(player: BattlePlayerState): BattlePlayerState =
    if (canStartReload(player) && (player.reloadPressed || (player.primaryHeld && player.ammoInMagazine <= 0))) {
      val weapon = primaryWeapon(player)
      withPrimaryWeapon(player, weapon.copy(reloadRemainingMs = weaponDefinitionFor(weapon.weaponKind).reloadMs))
    } else {
      player
    }

  private def canStartReload(player: BattlePlayerState): Boolean =
    {
      val weapon = primaryWeapon(player)
      player.alive &&
        weapon.reloadRemainingMs <= 0L &&
        weaponDefinitionFor(weapon.weaponKind).reloadMs > 0L &&
        weapon.magazineSize > 0 &&
        weapon.ammoInMagazine < weapon.magazineSize &&
        weapon.reserveAmmo > 0
    }

  private def shouldBotReload(player: BattlePlayerState): Boolean =
    canStartReload(player) && primaryWeapon(player).ammoInMagazine <= 0

  private def finishReloadIfReady(player: BattlePlayerState, previousReloadRemainingMs: Long): BattlePlayerState =
    if (previousReloadRemainingMs > 0L && player.reloadRemainingMs <= 0L) {
      finishReload(player)
    } else {
      player
    }

  private def finishReload(player: BattlePlayerState): BattlePlayerState = {
    val weapon = primaryWeapon(player)
    val missingAmmo = math.max(0, weapon.magazineSize - weapon.ammoInMagazine)
    val transferredAmmo = math.min(missingAmmo, math.max(0, weapon.reserveAmmo))
    if (transferredAmmo <= 0) {
      player
    } else {
      withPrimaryWeapon(
        player,
        weapon.copy(
          ammoInMagazine = weapon.ammoInMagazine + transferredAmmo,
          reserveAmmo = weapon.reserveAmmo - transferredAmmo
        )
      )
    }
  }

  private def advanceProjectiles(
    projectiles: Vector[BattleProjectileState],
    elapsedSeconds: Double,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState]
  ): Vector[AdvancedProjectile] =
    projectiles.flatMap { projectile =>
      val travelMs = math.min(math.max(0L, deltaMs), math.max(0L, projectile.ttlMs))
      if (travelMs <= 0L) {
        None
      } else {
        val travelSeconds = math.min(elapsedSeconds, travelMs.toDouble / 1000.0)
        val speedMultiplier = slowSpeedMultiplier(projectile.position, slowFields)
        val nextPosition = BattleVector2(
          x = projectile.position.x + projectile.velocity.x * travelSeconds * speedMultiplier,
          y = projectile.position.y + projectile.velocity.y * travelSeconds * speedMultiplier
        )
        val block = firstProjectileBlock(projectile.position, nextPosition, projectile.radius)
        Some(
          AdvancedProjectile(
            projectile = projectile.copy(
              position = nextPosition,
              facing = vectorAngle(projectile.velocity, projectile.facing),
              ttlMs = projectile.ttlMs - travelMs
            ),
            start = projectile.position,
            end = nextPosition,
            ttlBefore = projectile.ttlMs,
            block = block
          )
        )
      }
    }

  private def resolveProjectileHits(
    players: Vector[BattlePlayerState],
    projectiles: Vector[AdvancedProjectile],
    elapsedMs: Long
  ): (Vector[BattlePlayerState], Vector[BattleProjectileState], Vector[BattleEventState], Vector[BattleProjectileTerminalState]) = {
    val nextPlayers = players.toArray
    val survivingProjectiles = Vector.newBuilder[BattleProjectileState]
    val killEvents = Vector.newBuilder[BattleEventState]
    val projectileTerminals = Vector.newBuilder[BattleProjectileTerminalState]

    projectiles.foreach { advancedProjectile =>
      val projectile = advancedProjectile.projectile
      val maybeHit =
        nextPlayers.indices
          .flatMap(index => projectileHitCandidate(advancedProjectile, nextPlayers(index), index))
          .minByOption(_.hitT)

      val terminal =
        maybeHit match {
          case Some(hit) =>
            Some(("hit", hit.hitT, Some(hit.playerIndex)))
          case None =>
            advancedProjectile.block match {
              case Some(block) =>
                Some((block.reason, block.blockedAtT, None))
              case None if projectile.ttlMs <= 0L =>
                Some(("ttl", 1.0, None))
              case None =>
                None
            }
        }

      terminal match {
        case Some((reason, terminalT, directHitIndex)) =>
          val terminalPosition = pointAtSegmentT(advancedProjectile.start, advancedProjectile.end, terminalT)
          val damageTargetIndices = projectileDamageTargetIndices(projectile, terminalPosition, nextPlayers, directHitIndex)
          var terminalTargetPlayerId: Option[UserId] = None
          var terminalTargetHeroId: Option[String] = None
          var terminalHpBefore: Option[Int] = None
          var terminalHpAfter: Option[Int] = None
          var terminalDamage: Option[Int] = None

          damageTargetIndices.foreach { playerIndex =>
            val target = nextPlayers(playerIndex)
            val hpBefore = target.hp
            val nextHp = math.max(0, target.hp - projectile.damage)
            nextPlayers(playerIndex) =
              if (nextHp <= 0) {
                val eliminated = eliminatePlayer(target, elapsedMs)
                resolveKillEvent(projectile, target, nextPlayers.toVector, elapsedMs).foreach(killEvents += _)
                awardProjectileKillScore(projectile, target, nextPlayers)
                eliminated
              } else {
                target.copy(hp = nextHp)
              }

            if (terminalTargetPlayerId.isEmpty || directHitIndex.contains(playerIndex)) {
              terminalTargetPlayerId = Some(target.playerId)
              terminalTargetHeroId = Some(target.heroId)
              terminalHpBefore = Some(hpBefore)
              terminalHpAfter = Some(nextHp)
              terminalDamage = Some(projectile.damage)
            }
          }

          projectileTerminals += projectileTerminal(
            advancedProjectile = advancedProjectile,
            reason = reason,
            terminalT = terminalT,
            elapsedMs = elapsedMs,
            targetPlayerId = terminalTargetPlayerId,
            targetHeroId = terminalTargetHeroId,
            hpBefore = terminalHpBefore,
            hpAfter = terminalHpAfter,
            damage = terminalDamage
          )

        case None =>
          survivingProjectiles += projectile
      }
    }

    (nextPlayers.toVector, survivingProjectiles.result(), killEvents.result(), projectileTerminals.result())
  }

  private def projectileDamageTargetIndices(
    projectile: BattleProjectileState,
    terminalPosition: BattleVector2,
    players: Array[BattlePlayerState],
    directHitIndex: Option[Int]
  ): Vector[Int] = {
    val directTargets = directHitIndex.toVector
    if (projectile.splashRadius <= 0.0) {
      directTargets
    } else {
      val splashRadius = projectile.splashRadius + playerHitRadius
      val splashRadiusSquared = splashRadius * splashRadius
      val splashTargets =
        players.indices
          .filter(index => isProjectileTargetCandidate(projectile, players(index)))
          .filter(index => distanceSquared(players(index).position, terminalPosition) <= splashRadiusSquared)
          .toVector

      (directTargets ++ splashTargets.filterNot(index => directTargets.contains(index))).distinct
    }
  }

  private def projectileTerminal(
    advancedProjectile: AdvancedProjectile,
    reason: String,
    terminalT: Double,
    elapsedMs: Long,
    targetPlayerId: Option[UserId] = None,
    targetHeroId: Option[String] = None,
    hpBefore: Option[Int] = None,
    hpAfter: Option[Int] = None,
    damage: Option[Int] = None
  ): BattleProjectileTerminalState = {
    val projectile = advancedProjectile.projectile

    BattleProjectileTerminalState(
      projectileId = projectile.projectileId,
      kind = projectile.kind,
      ownerPlayerId = projectile.ownerPlayerId,
      ownerHeroId = projectile.ownerHeroId,
      reason = reason,
      start = advancedProjectile.start,
      end = advancedProjectile.end,
      terminalPosition = pointAtSegmentT(advancedProjectile.start, advancedProjectile.end, terminalT),
      ttlBefore = math.max(0L, advancedProjectile.ttlBefore),
      ttlAfter = math.max(0L, projectile.ttlMs),
      elapsedMs = math.max(0L, elapsedMs),
      targetPlayerId = targetPlayerId,
      targetHeroId = targetHeroId,
      hpBefore = hpBefore,
      hpAfter = hpAfter,
      damage = damage
    )
  }

  private def advancePickups(pickups: Vector[BattlePickupState], deltaMs: Long): Vector[BattlePickupState] =
    pickups.map { pickup =>
      if (pickup.available) {
        pickup.copy(respawnMs = 0L)
      } else {
        val nextRespawnMs = math.max(0L, pickup.respawnMs - deltaMs)
        pickup.copy(
          available = nextRespawnMs <= 0L,
          respawnMs = nextRespawnMs
        )
      }
    }

  private def resolvePickups(
    players: Vector[BattlePlayerState],
    pickups: Vector[BattlePickupState],
    elapsedMs: Long
  ): (Vector[BattlePlayerState], Vector[BattlePickupState], Vector[BattleEventState]) = {
    val nextPlayers = players.toArray
    val nextPickups = pickups.toArray
    val pickupEvents = Vector.newBuilder[BattleEventState]

    nextPickups.indices.foreach { pickupIndex =>
      val pickup = nextPickups(pickupIndex)
      if (pickup.available) {
        val maybePlayerIndex = pickup.kind match {
          case `medkitPickupKind` =>
            closestPickupTarget(nextPlayers, pickup, medkitPickupRadius)
          case `weaponPickupKind` if pickup.weaponKind.exists(isKnownWeaponKind) =>
            closestPickupTarget(nextPlayers, pickup, weaponPickupRadius)
          case _ =>
            None
        }

        maybePlayerIndex.foreach { playerIndex =>
          val player = nextPlayers(playerIndex)
          pickup.kind match {
            case `medkitPickupKind` =>
              nextPlayers(playerIndex) = player.copy(hp = math.min(player.maxHp, player.hp + medkitHealAmount))
              nextPickups(pickupIndex) = pickup.copy(
                available = false,
                respawnMs = medkitRespawnMs
              )
              pickupEvents += resolveMedkitPickupEvent(player, pickup, elapsedMs)

            case `weaponPickupKind` =>
              nextPlayers(playerIndex) = pickup.weaponKind match {
                case Some(weaponKind) => equipOrRefillWeapon(player, weaponKind)
                case None => player
              }
              nextPickups(pickupIndex) = pickup.copy(
                available = false,
                respawnMs = weaponPickupRespawnMs
              )
              pickupEvents += resolveWeaponPickupEvent(player, pickup, elapsedMs)

            case _ =>
          }
        }
      }
    }

    (nextPlayers.toVector, nextPickups.toVector, pickupEvents.result())
  }

  private def closestPickupTarget(
    players: Array[BattlePlayerState],
    pickup: BattlePickupState,
    radius: Double
  ): Option[Int] =
    players.indices
      .filter(index => isPickupTarget(players(index), pickup, radius))
      .minByOption(index => distanceSquared(players(index).position, pickup.position))

  private def equipOrRefillWeapon(player: BattlePlayerState, weaponKind: String): BattlePlayerState = {
    val definition = weaponDefinitionFor(weaponKind)
    val (weapons, currentIndex) = normalizedWeaponInventory(player)
    val existingIndex = weapons.indexWhere(_.weaponKind == definition.weaponKind)
    val weapon =
      BattleWeaponState(
        weaponKind = definition.weaponKind,
        ammoInMagazine = definition.magazineSize,
        magazineSize = definition.magazineSize,
        reserveAmmo = existingIndex match {
          case index if index >= 0 =>
            weapons(index).reserveAmmo + definition.pickupAmmo
          case _ =>
            definition.reserveAmmo
        },
        fireCooldownMs = 0L,
        reloadRemainingMs = 0L
      )

    if (existingIndex >= 0) {
      syncWeaponInventory(player, weapons.updated(existingIndex, normalizeWeaponState(weapon)), currentIndex)
    } else {
      val nextWeapons = weapons :+ normalizeWeaponState(weapon)
      syncWeaponInventory(player, nextWeapons, currentIndex)
    }
  }

  private def resolveMedkitPickupEvent(
    player: BattlePlayerState,
    pickup: BattlePickupState,
    elapsedMs: Long
  ): BattleEventState = {
    val participant = BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    )

    BattleEventState(
      eventId = s"heal-${elapsedMs}-${pickup.pickupId}-${player.playerId.value}",
      eventType = "heal",
      kind = "heal",
      elapsedMs = elapsedMs,
      message = s"${player.displayName} 鎷惧彇浜嗗尰鐤楀寘",
      source = participant,
      target = participant
    )
  }

  private def resolveWeaponPickupEvent(
    player: BattlePlayerState,
    pickup: BattlePickupState,
    elapsedMs: Long
  ): BattleEventState = {
    val pickupWeaponLabel = pickup.weaponKind.getOrElse("Weapon")
    val participant = BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    )

    BattleEventState(
      eventId = s"pickup-${elapsedMs}-${pickup.pickupId}-${player.playerId.value}",
      eventType = "pickup",
      kind = "pickup",
      elapsedMs = elapsedMs,
      message = s"${player.displayName} 拾取了 ${pickupWeaponLabel}",
      source = participant,
      target = participant
    )
  }

  private def awardProjectileKillScore(
    projectile: BattleProjectileState,
    target: BattlePlayerState,
    players: Array[BattlePlayerState]
  ): Unit = {
    val ownerIndex = players.indexWhere(_.playerId == projectile.ownerPlayerId)
    if (ownerIndex >= 0 && players(ownerIndex).playerId != target.playerId) {
      val owner = players(ownerIndex)
      players(ownerIndex) = owner.copy(
        kills = owner.kills + 1,
        score = owner.score + 1
      )
    }
  }

  private def resolveKillEvent(
    projectile: BattleProjectileState,
    target: BattlePlayerState,
    players: Vector[BattlePlayerState],
    elapsedMs: Long
  ): Option[BattleEventState] =
    players.find(_.playerId == projectile.ownerPlayerId).map { source =>
      val sourceParticipant = BattleEventParticipant(
        playerId = source.playerId,
        heroId = source.heroId,
        displayName = source.displayName
      )
      val targetParticipant = BattleEventParticipant(
        playerId = target.playerId,
        heroId = target.heroId,
        displayName = target.displayName
      )

      BattleEventState(
        eventId = s"kill-${elapsedMs}-${projectile.projectileId}-${target.playerId.value}",
        eventType = "kill",
        kind = "kill",
        elapsedMs = elapsedMs,
        message = s"${source.displayName} 娣樻卑浜?${target.displayName}",
        source = sourceParticipant,
        target = targetParticipant
      )
    }

  private def eliminatePlayer(player: BattlePlayerState, elapsedMs: Long): BattlePlayerState =
    if (!player.alive) {
      player
    } else {
      clearWeaponRuntime(player.copy(
        hp = 0,
        alive = false,
        velocity = zeroVector,
        movementIntent = zeroVector,
        primaryHeld = false,
        reloadPressed = false,
        eliminatedAtMs = player.eliminatedAtMs.orElse(Some(elapsedMs)),
        respawnMs = 0L,
        skills = player.skills.map(skill => skill.copy(activeMs = 0L, cooldownMs = math.max(0L, skill.cooldownMs)))
      ))
    }

  private def updateReplayFrames(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: Long,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState],
    hasRuntimeEvents: Boolean,
    finished: Boolean
  ): Vector[BattleReplayFrameState] =
    if (hasRuntimeEvents || finished || shouldRecordIntervalFrame(frames, elapsedMs)) {
      appendReplayFrame(frames, elapsedMs, players, projectiles, pickups)
    } else {
      frames
    }

  private def shouldRecordIntervalFrame(frames: Vector[BattleReplayFrameState], elapsedMs: Long): Boolean =
    elapsedMs > 0L && {
      val latestElapsedMs = frames.map(_.elapsedMs).maxOption.getOrElse(0L)
      elapsedMs / replayFrameSampleIntervalMs > latestElapsedMs / replayFrameSampleIntervalMs
    }

  private def appendReplayFrame(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: Long,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): Vector[BattleReplayFrameState] =
    retainReplayFrames(
      frames.filterNot(_.elapsedMs == elapsedMs) :+ captureReplayFrame(elapsedMs, players, projectiles, pickups)
    )

  private def captureReplayFrame(
    elapsedMs: Long,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): BattleReplayFrameState =
    BattleReplayFrameState(
      elapsedMs = math.max(0L, elapsedMs),
      heroes = players.sortBy(_.seat).map { player =>
        BattleReplayHeroFrameState(
          playerId = player.playerId,
          heroId = player.heroId,
          handle = player.handle,
          displayName = player.displayName,
          seat = player.seat,
          position = player.position,
          hp = math.max(0, player.hp),
          maxHp = math.max(1, player.maxHp),
          alive = player.alive,
          score = player.score,
          facing = player.facing,
          currentWeaponKind = Option(player.currentWeaponKind).map(_.trim).filter(_.nonEmpty).getOrElse(pistolWeaponKind),
          eliminatedAtMs = player.eliminatedAtMs.map(value => math.max(0L, value))
        )
      },
      projectiles = projectiles.map { projectile =>
        BattleReplayProjectileFrameState(
          projectileId = projectile.projectileId,
          kind = projectile.kind,
          position = projectile.position,
          facing = projectile.facing,
          ttlMs = math.max(0L, projectile.ttlMs),
          splashRadius = math.max(0.0, projectile.splashRadius)
        )
      },
      pickups = pickups.map { pickup =>
        BattleReplayPickupFrameState(
          pickupId = pickup.pickupId,
          kind = pickup.kind,
          weaponKind = pickup.weaponKind,
          position = pickup.position,
          available = pickup.available,
          respawnMs = math.max(0L, pickup.respawnMs)
        )
      }
    )

  private def retainReplayFrames(frames: Vector[BattleReplayFrameState]): Vector[BattleReplayFrameState] = {
    val distinctFrames = frames.sortBy(_.elapsedMs).foldLeft(Vector.empty[BattleReplayFrameState]) {
      case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
        accumulator.dropRight(1) :+ frame
      case (accumulator, frame) =>
        accumulator :+ frame
    }

    if (distinctFrames.length <= retainedReplayFrameCount) {
      distinctFrames
    } else {
      val initialFrame = distinctFrames.headOption.filter(_.elapsedMs == 0L).toVector
      val retainedTail = distinctFrames.drop(initialFrame.length).takeRight(retainedReplayFrameCount - initialFrame.length)
      initialFrame ++ retainedTail
    }
  }

  private def finishBattle(
    state: BattleAggregateState,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState]
  ): BattleAggregateState = {
    val finishedPlayers = players.map(clearFinishedPlayerRuntime)
    val winningPlayer = resolveWinningPlayer(finishedPlayers)
    val finalElapsedMs = math.max(0L, math.min(state.elapsedMs, state.durationMs))

    state.copy(
      phase = "finished",
      elapsedMs = finalElapsedMs,
      players = finishedPlayers,
      projectiles = Vector.empty,
      replayFrames = appendReplayFrame(state.replayFrames, finalElapsedMs, finishedPlayers, Vector.empty, state.pickups),
      winnerPlayerId = winningPlayer.map(_.playerId),
      winnerHeroId = winningPlayer.map(_.heroId)
    )
  }

  private def clearFinishedPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    clearWeaponRuntime(player.copy(
      primaryHeld = false,
      reloadPressed = false,
      respawnMs = 0L,
      skills = player.skills.map(skill => skill.copy(activeMs = 0L))
    ))

  private def initialWeapon(weaponKind: String): BattleWeaponState = {
    val definition = weaponDefinitionFor(weaponKind)
    BattleWeaponState(
      weaponKind = definition.weaponKind,
      ammoInMagazine = definition.magazineSize,
      magazineSize = definition.magazineSize,
      reserveAmmo = definition.reserveAmmo,
      fireCooldownMs = 0L,
      reloadRemainingMs = 0L
    )
  }

  private def weaponDefinitionFor(weaponKind: String): WeaponDefinition =
    weaponDefinitions.getOrElse(Option(weaponKind).map(_.trim).filter(_.nonEmpty).getOrElse(pistolWeaponKind), weaponDefinitions(pistolWeaponKind))

  private def isKnownWeaponKind(weaponKind: String): Boolean =
    weaponDefinitions.contains(weaponKind)

  private def primaryWeapon(player: BattlePlayerState): BattleWeaponState =
    normalizedWeaponInventory(player) match {
      case (weapons, currentIndex) =>
        weapons.lift(currentIndex).getOrElse(initialWeapon(pistolWeaponKind))
    }

  private def normalizedWeaponInventory(player: BattlePlayerState): (Vector[BattleWeaponState], Int) = {
    val sourceWeapons =
      if (player.weapons.nonEmpty) {
        player.weapons
      } else {
        Vector(
          BattleWeaponState(
            weaponKind = Option(player.currentWeaponKind).map(_.trim).filter(_.nonEmpty).getOrElse(pistolWeaponKind),
            ammoInMagazine = player.ammoInMagazine,
            magazineSize = player.magazineSize,
            reserveAmmo = player.reserveAmmo,
            fireCooldownMs = player.fireCooldownMs,
            reloadRemainingMs = player.reloadRemainingMs
          )
        )
      }
    val normalizedWeapons = sourceWeapons.map(normalizeWeaponState).filter(weapon => isKnownWeaponKind(weapon.weaponKind))
    val safeWeapons = if (normalizedWeapons.nonEmpty) normalizedWeapons else Vector(initialWeapon(pistolWeaponKind))
    val currentIndex = math.max(0, math.min(player.currentWeaponIndex, safeWeapons.length - 1))
    (safeWeapons, currentIndex)
  }

  private def normalizeWeaponState(weapon: BattleWeaponState): BattleWeaponState = {
    val definition = weaponDefinitionFor(weapon.weaponKind)
    weapon.copy(
      weaponKind = definition.weaponKind,
      ammoInMagazine = math.max(0, math.min(weapon.ammoInMagazine, math.max(1, definition.magazineSize))),
      magazineSize = definition.magazineSize,
      reserveAmmo = math.max(0, weapon.reserveAmmo),
      fireCooldownMs = math.max(0L, weapon.fireCooldownMs),
      reloadRemainingMs = math.max(0L, weapon.reloadRemainingMs)
    )
  }

  private def syncWeaponInventory(
    player: BattlePlayerState,
    weapons: Vector[BattleWeaponState],
    currentWeaponIndex: Int
  ): BattlePlayerState = {
    val safeWeapons = if (weapons.nonEmpty) weapons.map(normalizeWeaponState) else Vector(initialWeapon(pistolWeaponKind))
    val safeIndex = math.max(0, math.min(currentWeaponIndex, safeWeapons.length - 1))
    val currentWeapon = safeWeapons(safeIndex)
    player.copy(
      currentWeaponIndex = safeIndex,
      weapons = safeWeapons,
      currentWeaponKind = currentWeapon.weaponKind,
      ammoInMagazine = currentWeapon.ammoInMagazine,
      magazineSize = currentWeapon.magazineSize,
      reserveAmmo = currentWeapon.reserveAmmo,
      fireCooldownMs = currentWeapon.fireCooldownMs,
      reloadRemainingMs = currentWeapon.reloadRemainingMs
    )
  }

  private def applyWeaponSwitchRequest(player: BattlePlayerState, direction: Int): BattlePlayerState = {
    val switchDirection =
      if (direction < 0) {
        -1
      } else if (direction > 0) {
        1
      } else {
        0
      }
    val (weapons, currentIndex) = normalizedWeaponInventory(player)
    if (!player.alive || switchDirection == 0 || weapons.length <= 1) {
      syncWeaponInventory(player, weapons, currentIndex)
    } else {
      val nextIndex = (currentIndex + switchDirection + weapons.length) % weapons.length
      val cancelledReloadWeapon = weapons(currentIndex).copy(reloadRemainingMs = 0L)
      syncWeaponInventory(player, weapons.updated(currentIndex, cancelledReloadWeapon), nextIndex)
    }
  }

  private def applyWeaponRecoil(
    player: BattlePlayerState,
    direction: BattleVector2,
    weaponDefinition: WeaponDefinition
  ): BattlePlayerState = {
    val recoilDistance = math.min(24.0, math.max(0.0, weaponDefinition.recoilStrength) * 0.18)
    val recoilDirection = normalizeVector(BattleVector2(-direction.x, -direction.y))
    if (recoilDistance <= 0.0 || isZeroVector(recoilDirection)) {
      player
    } else {
      val resolvedMotion = AuthoritativeArenaGeometry.findMotionDestination(
        position = player.position,
        direction = recoilDirection,
        distance = recoilDistance,
        radius = playerHitRadius
      )
      player.copy(position = resolvedMotion.destination)
    }
  }

  private def withPrimaryWeapon(player: BattlePlayerState, weapon: BattleWeaponState): BattlePlayerState = {
    val (weapons, currentIndex) = normalizedWeaponInventory(player)
    val normalizedWeapon = normalizeWeaponState(weapon)
    val nextWeapons =
      if (weapons.nonEmpty) {
        weapons.updated(currentIndex, normalizedWeapon)
      } else {
        Vector(normalizedWeapon)
      }

    syncWeaponInventory(player, nextWeapons, currentIndex)
  }

  private def clearWeaponRuntime(player: BattlePlayerState): BattlePlayerState =
    withPrimaryWeapon(
      player,
      primaryWeapon(player).copy(
        fireCooldownMs = 0L,
        reloadRemainingMs = 0L
      )
    )

  private def castDashIfReady(
    player: BattlePlayerState,
    movementIntent: BattleVector2,
    aim: BattleVector2
  ): PlayerSkillCastResult =
    player.skills.indexWhere(_.kind == dashSkillKind) match {
      case dashIndex if dashIndex < 0 =>
        PlayerSkillCastResult(player, skillNoop(dashSkillKind, "skill_not_owned"))
      case dashIndex if player.skills(dashIndex).cooldownMs > 0L =>
        PlayerSkillCastResult(player, skillNoop(dashSkillKind, "cooldown"))
      case dashIndex =>
        val direction = if (isZeroVector(movementIntent)) aim else movementIntent
        if (isZeroVector(direction)) {
          PlayerSkillCastResult(player, skillNoop(dashSkillKind, "no_direction"))
        } else {
          val resolvedMotion = AuthoritativeArenaGeometry.findMotionDestination(
            position = player.position,
            direction = direction,
            distance = dashDistance,
            radius = playerHitRadius
          )
          if (distanceSquared(player.position, resolvedMotion.destination) <= 0.0001) {
            PlayerSkillCastResult(player, skillNoop(dashSkillKind, "blocked"))
          } else {
            val dash = player.skills(dashIndex)
            PlayerSkillCastResult(
              player.copy(
                position = resolvedMotion.destination,
                skills = player.skills.updated(
                  dashIndex,
                  dash.copy(cooldownMs = dashCooldownMs, activeMs = dashActiveMs)
                )
              ),
              skillApplied(dashSkillKind)
            )
          }
        }
    }

  private def castBlinkIfReady(
    player: BattlePlayerState,
    pointerWorld: Option[BattleCommandVector]
  ): PlayerSkillCastResult =
    player.skills.indexWhere(_.kind == blinkSkillKind) match {
      case blinkIndex if blinkIndex < 0 =>
        PlayerSkillCastResult(player, skillNoop(blinkSkillKind, "skill_not_owned"))
      case blinkIndex if player.skills(blinkIndex).cooldownMs > 0L =>
        PlayerSkillCastResult(player, skillNoop(blinkSkillKind, "cooldown"))
      case blinkIndex =>
        pointerWorld match {
          case Some(target) =>
            val destination = BattleVector2(target.x, target.y)
            if (
              !AuthoritativeArenaGeometry.canOccupy(destination, playerHitRadius)
            ) {
              val reason =
                if (AuthoritativeArenaGeometry.isInsideWorld(destination, playerHitRadius)) "blocked"
                else "invalid_target"
              PlayerSkillCastResult(player, skillNoop(blinkSkillKind, reason))
            } else if (distanceSquared(player.position, destination) > math.pow(blinkRange, 2)) {
              PlayerSkillCastResult(player, skillNoop(blinkSkillKind, "out_of_range"))
            } else {
              val blink = player.skills(blinkIndex)
              PlayerSkillCastResult(
                player.copy(
                  position = destination,
                  skills = player.skills.updated(
                    blinkIndex,
                    blink.copy(cooldownMs = blinkCooldownMs, activeMs = blinkActiveMs)
                  )
                ),
                skillApplied(blinkSkillKind)
              )
            }
          case None =>
            PlayerSkillCastResult(player, skillNoop(blinkSkillKind, "missing_target"))
        }
    }

  private def castFreezeIfReady(
    state: BattleAggregateState,
    player: BattlePlayerState,
    pointerWorld: Option[BattleCommandVector]
  ): FreezeSkillCastResult =
    player.skills.indexWhere(_.kind == freezeSkillKind) match {
      case freezeIndex if freezeIndex < 0 =>
        FreezeSkillCastResult(player, state.slowFields, skillNoop(freezeSkillKind, "skill_not_owned"))
      case freezeIndex if player.skills(freezeIndex).cooldownMs > 0L =>
        FreezeSkillCastResult(player, state.slowFields, skillNoop(freezeSkillKind, "cooldown"))
      case freezeIndex =>
        pointerWorld match {
          case Some(target) =>
            val position = BattleVector2(target.x, target.y)
            if (!isWithinWorld(position)) {
              FreezeSkillCastResult(player, state.slowFields, skillNoop(freezeSkillKind, "invalid_target"))
            } else if (distanceSquared(player.position, position) > math.pow(freezeRange, 2)) {
              FreezeSkillCastResult(player, state.slowFields, skillNoop(freezeSkillKind, "out_of_range"))
            } else {
              val freeze = player.skills(freezeIndex)
              val nextPlayer = player.copy(
                skills = player.skills.updated(
                  freezeIndex,
                  freeze.copy(cooldownMs = freezeCooldownMs, activeMs = freezeDurationMs)
                )
              )
              val nextField = BattleSlowFieldState(
                fieldId = s"freeze-${state.tick}-${player.seat}-${state.slowFields.length + 1}",
                ownerPlayerId = player.playerId,
                ownerHeroId = player.heroId,
                position = position,
                radius = freezeRadius,
                ttlMs = freezeDurationMs,
                durationMs = freezeDurationMs
              )
              FreezeSkillCastResult(nextPlayer, state.slowFields :+ nextField, skillApplied(freezeSkillKind))
            }
          case None =>
            FreezeSkillCastResult(player, state.slowFields, skillNoop(freezeSkillKind, "missing_target"))
        }
    }

  private def skillApplied(action: String): BattleCommandSkillOutcome =
    BattleCommandSkillOutcome(action = action, status = "applied")

  private def skillNoop(action: String, reason: String): BattleCommandSkillOutcome =
    BattleCommandSkillOutcome(action = action, status = "noop", reason = Some(reason))

  private def advanceSkills(skills: Vector[BattlePlayerSkillState], deltaMs: Long): Vector[BattlePlayerSkillState] =
    skills.map(skill =>
      skill.copy(
        cooldownMs = math.max(0L, skill.cooldownMs - deltaMs),
        activeMs = math.max(0L, skill.activeMs - deltaMs)
      )
    )

  private def advanceEliminatedSkills(skills: Vector[BattlePlayerSkillState], deltaMs: Long): Vector[BattlePlayerSkillState] =
    skills.map(skill =>
      skill.copy(
        cooldownMs = math.max(0L, skill.cooldownMs - deltaMs),
        activeMs = 0L
      )
    )

  private def shouldFinishBattle(players: Vector[BattlePlayerState], elapsedMs: Long, durationMs: Long): Boolean =
    elapsedMs >= durationMs || players.count(_.alive) <= 1

  private def resolveWinningPlayer(players: Vector[BattlePlayerState]): Option[BattlePlayerState] = {
    val alivePlayers = players.filter(_.alive)
    alivePlayers.headOption.filter(_ => alivePlayers.size == 1)
  }

  private def retainRecentEvents(events: Vector[BattleEventState]): Vector[BattleEventState] =
    events.takeRight(retainedEventCount)

  private def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState]
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(retainedProjectileTerminalCount)

  private def remainingDurationMs(state: BattleAggregateState, elapsedMs: Long): Long =
    math.max(0L, state.durationMs - elapsedMs)

  private def elapsedAt(state: BattleAggregateState, now: Long): Long =
    elapsedAt(state.startedAt, state.durationMs, now)

  private def elapsedAt(startedAt: Long, durationMs: Long, now: Long): Long =
    math.max(0L, math.min(durationMs, now - startedAt))

  private def projectileHitCandidate(
    advancedProjectile: AdvancedProjectile,
    player: BattlePlayerState,
    playerIndex: Int
  ): Option[ProjectileHitCandidate] =
    if (!isProjectileTargetCandidate(advancedProjectile.projectile, player)) {
      None
    } else {
      segmentCircleHitT(
        start = advancedProjectile.start,
        end = advancedProjectile.end,
        center = player.position,
        radius = advancedProjectile.projectile.radius + playerHitRadius + projectileShooterAdvantageRadius
      ).filter(hitT => hitT <= advancedProjectile.block.map(_.blockedAtT).getOrElse(1.0) + 0.000001)
        .map(hitT => ProjectileHitCandidate(playerIndex, hitT))
    }

  private def isProjectileTargetCandidate(projectile: BattleProjectileState, player: BattlePlayerState): Boolean =
    player.alive &&
      player.playerId != projectile.ownerPlayerId &&
      player.heroId != projectile.ownerHeroId

  private def isPickupTarget(player: BattlePlayerState, pickup: BattlePickupState, radius: Double): Boolean =
    player.alive &&
      distanceSquared(player.position, pickup.position) <= math.pow(radius, 2)

  private def initialPickups: Vector[BattlePickupState] = {
    val fixedPickups = medkitPickupDefinitions.map { pickup =>
      BattlePickupState(
        pickupId = pickup.pickupId,
        kind = pickup.kind,
        weaponKind = None,
        position = pickup.position,
        available = true,
        respawnMs = 0L
      )
    }
    fixedPickups ++ weaponPickupDefinitions.map { pickup =>
      BattlePickupState(
        pickupId = pickup.pickupId,
        kind = weaponPickupKind,
        weaponKind = Some(pickup.weaponKind),
        position = pickup.position,
        available = true,
        respawnMs = 0L
      )
    }
  }

  private def initialSkills: Vector[BattlePlayerSkillState] =
    Vector(
      BattlePlayerSkillState(kind = blinkSkillKind, cooldownMs = 0L, activeMs = 0L),
      BattlePlayerSkillState(kind = dashSkillKind, cooldownMs = 0L, activeMs = 0L),
      BattlePlayerSkillState(kind = freezeSkillKind, cooldownMs = 0L, activeMs = 0L)
    )

  private def resolvePlayerControl(player: BattlePlayerState, state: BattleAggregateState): PlayerControl =
    if (player.isBot) {
      resolveBotControl(player, state)
    } else {
      PlayerControl(
        movement = player.movementIntent,
        aim = player.aim,
        primaryHeld = player.primaryHeld,
        sprint = latestHumanSprints.getOrElse(playerControlKey(state, player), false),
        reloadPressed = player.reloadPressed
      )
    }

  private def resolveBotControl(player: BattlePlayerState, state: BattleAggregateState): PlayerControl = {
    val aliveOpponents = state.players.filter(candidate => candidate.playerId != player.playerId && candidate.alive)
    val preferredTargets = aliveOpponents.filterNot(_.isBot)
    val targetPool = if (preferredTargets.nonEmpty) preferredTargets else aliveOpponents

    targetPool.minByOption(candidate => distanceSquared(player.position, candidate.position)) match {
      case Some(target) =>
        val toTarget = subtract(target.position, player.position)
        val distance = math.sqrt(distanceSquared(player.position, target.position))
        val aim = normalizeAim(player.aim, toTarget)
        val orbitDirection = if (((state.tick + player.seat) & 1L) == 0L) 1.0 else -1.0
        val orbit = perpendicular(aim, orbitDirection)
        val radial =
          if (distance > botPreferredRange + 120.0) aim
          else if (distance < botPreferredRange - 90.0) scale(aim, -1.0)
          else zeroVector
        val movement = normalizeVector(add(scale(radial, 0.86), scale(orbit, 0.52)))

        PlayerControl(
          movement = if (isZeroVector(movement)) orbit else movement,
          aim = aim,
          primaryHeld = distance <= botFireRangeForTarget(target) && canBotFireAtTarget(target, state),
          sprint = false,
          reloadPressed = shouldBotReload(player)
        )

      case None =>
        val patrolAngle = (state.tick + player.seat.toLong * 11L).toDouble * 0.18
        val patrolTarget = BattleVector2(
          x = clamp(player.spawnAnchor.x + math.cos(patrolAngle) * 140.0, 0.0, maxWorldX),
          y = clamp(player.spawnAnchor.y + math.sin(patrolAngle) * 110.0, 0.0, maxWorldY)
        )
        val patrolMovement = normalizeVector(subtract(patrolTarget, player.position))
        val aim = normalizeAim(player.aim, patrolMovement)

        PlayerControl(
          movement = patrolMovement,
          aim = aim,
          primaryHeld = false,
          sprint = false,
          reloadPressed = shouldBotReload(player)
        )
    }
  }

  private def playerControlKey(state: BattleAggregateState, player: BattlePlayerState): (String, String) =
    (state.battleId.value, player.playerId.value)

  private def canBotFireAtTarget(target: BattlePlayerState, state: BattleAggregateState): Boolean =
    target.isBot || state.elapsedMs >= botHumanOpeningFireDelayMs

  private def botFireRangeForTarget(target: BattlePlayerState): Double =
    if (target.isBot) botFireRange else botHumanFireRange

  private def spawnPointFor(index: Int): BattleVector2 =
    spawnPoints.lift(index).getOrElse {
      val fallbackX = 240.0 + (index % 3) * 320.0
      val fallbackY = 240.0 + (index / 3) * 260.0
      BattleVector2(fallbackX, fallbackY)
    }

  private def normalizeVector(vector: BattleCommandVector): BattleVector2 =
    normalizeVector(BattleVector2(vector.x, vector.y))

  private def normalizeVector(vector: BattleVector2): BattleVector2 = {
    val length = math.hypot(vector.x, vector.y)
    if (length <= 0.0001) {
      zeroVector
    } else {
      BattleVector2(vector.x / length, vector.y / length)
    }
  }

  private def normalizeAim(previous: BattleVector2, aim: BattleCommandVector): BattleVector2 =
    normalizeAim(previous, BattleVector2(aim.x, aim.y))

  private def normalizeAim(previous: BattleVector2, aim: BattleVector2): BattleVector2 = {
    val normalized = normalizeVector(aim)
    if (isZeroVector(normalized)) previous else normalized
  }

  private def vectorAngle(vector: BattleVector2, fallback: Double): Double =
    if (isZeroVector(vector)) fallback else math.atan2(vector.y, vector.x)

  private def spreadFacing(
    facing: BattleVector2,
    weaponDefinition: WeaponDefinition,
    projectileIndex: Int,
    projectileCount: Int
  ): BattleVector2 =
    if (projectileCount <= 1 || weaponDefinition.spreadRadians <= 0.0) {
      facing
    } else {
      val centerOffset = (projectileCount - 1).toDouble / 2.0
      val step = weaponDefinition.spreadRadians / math.max(1.0, projectileCount - 1.0)
      val angle = vectorAngle(facing, 0.0) + (projectileIndex.toDouble - centerOffset) * step
      BattleVector2(math.cos(angle), math.sin(angle))
    }

  private def add(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x + right.x, left.y + right.y)

  private def subtract(left: BattleVector2, right: BattleVector2): BattleVector2 =
    BattleVector2(left.x - right.x, left.y - right.y)

  private def scale(vector: BattleVector2, multiplier: Double): BattleVector2 =
    BattleVector2(vector.x * multiplier, vector.y * multiplier)

  private def pointAtSegmentT(start: BattleVector2, end: BattleVector2, t: Double): BattleVector2 = {
    val clampedT = clamp(t, 0.0, 1.0)
    BattleVector2(
      x = start.x + (end.x - start.x) * clampedT,
      y = start.y + (end.y - start.y) * clampedT
    )
  }

  private def perpendicular(vector: BattleVector2, direction: Double): BattleVector2 =
    normalizeVector(BattleVector2(-vector.y * direction, vector.x * direction))

  private def distanceSquared(left: BattleVector2, right: BattleVector2): Double = {
    val dx = left.x - right.x
    val dy = left.y - right.y
    dx * dx + dy * dy
  }

  private def segmentCircleHitT(
    start: BattleVector2,
    end: BattleVector2,
    center: BattleVector2,
    radius: Double
  ): Option[Double] = {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val radiusSquared = radius * radius
    val startToCenterX = start.x - center.x
    val startToCenterY = start.y - center.y

    if (startToCenterX * startToCenterX + startToCenterY * startToCenterY <= radiusSquared) {
      Some(0.0)
    } else {
      val a = dx * dx + dy * dy
      if (a <= 0.000001) {
        None
      } else {
        val b = 2.0 * (startToCenterX * dx + startToCenterY * dy)
        val c = startToCenterX * startToCenterX + startToCenterY * startToCenterY - radiusSquared
        val discriminant = b * b - 4.0 * a * c

        if (discriminant < 0.0) {
          None
        } else {
          val sqrtDiscriminant = math.sqrt(discriminant)
          val firstT = (-b - sqrtDiscriminant) / (2.0 * a)
          val secondT = (-b + sqrtDiscriminant) / (2.0 * a)
          if (firstT >= 0.0 && firstT <= 1.0) {
            Some(firstT)
          } else if (secondT >= 0.0 && secondT <= 1.0) {
            Some(secondT)
          } else {
            None
          }
        }
      }
    }
  }

  private def firstProjectileBlock(start: BattleVector2, end: BattleVector2, radius: Double): Option[ProjectileBlock] = {
    val worldExitT = firstSegmentWorldExitT(start, end, radius)
    val obstacleEnterT = AuthoritativeArenaGeometry.Obstacles
      .flatMap(obstacle => firstSegmentObstacleEnterT(start, end, radius, obstacle))
      .minOption

    (worldExitT, obstacleEnterT) match {
      case (Some(worldT), Some(obstacleT)) if worldT <= obstacleT =>
        Some(ProjectileBlock(worldT, "world"))
      case (Some(_), Some(obstacleT)) =>
        Some(ProjectileBlock(obstacleT, "obstacle"))
      case (Some(worldT), None) =>
        Some(ProjectileBlock(worldT, "world"))
      case (None, Some(obstacleT)) =>
        Some(ProjectileBlock(obstacleT, "obstacle"))
      case (None, None) =>
        None
    }
  }

  private def firstSegmentWorldExitT(start: BattleVector2, end: BattleVector2, radius: Double): Option[Double] = {
    val minX = radius
    val maxX = maxWorldX - radius
    val minY = radius
    val maxY = maxWorldY - radius
    if (!isPointInAabb(start, minX, maxX, minY, maxY)) {
      Some(0.0)
    } else if (isPointInAabb(end, minX, maxX, minY, maxY)) {
      None
    } else {
      val dx = end.x - start.x
      val dy = end.y - start.y
      val exitXT =
        if (dx > 0.0) Some((maxX - start.x) / dx)
        else if (dx < 0.0) Some((minX - start.x) / dx)
        else None
      val exitYT =
        if (dy > 0.0) Some((maxY - start.y) / dy)
        else if (dy < 0.0) Some((minY - start.y) / dy)
        else None

      (exitXT.toVector ++ exitYT.toVector)
        .filter(exitT => exitT >= 0.0 && exitT <= 1.0)
        .minOption
    }
  }

  private def firstSegmentObstacleEnterT(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double,
    obstacle: AuthoritativeArenaObstacle
  ): Option[Double] = {
    val minX = obstacle.position.x - obstacle.size.x / 2.0 - radius
    val maxX = obstacle.position.x + obstacle.size.x / 2.0 + radius
    val minY = obstacle.position.y - obstacle.size.y / 2.0 - radius
    val maxY = obstacle.position.y + obstacle.size.y / 2.0 + radius
    firstSegmentAabbEnterT(start, end, minX, maxX, minY, maxY)
  }

  private def firstSegmentAabbEnterT(
    start: BattleVector2,
    end: BattleVector2,
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double
  ): Option[Double] =
    if (isPointInAabb(start, minX, maxX, minY, maxY)) {
      Some(0.0)
    } else {
      val dx = end.x - start.x
      val dy = end.y - start.y
      val maybeXInterval = segmentAxisInterval(start.x, dx, minX, maxX)
      val maybeYInterval = segmentAxisInterval(start.y, dy, minY, maxY)

      (maybeXInterval, maybeYInterval) match {
        case (Some((xEnter, xExit)), Some((yEnter, yExit))) =>
          val enterT = math.max(xEnter, yEnter)
          val exitT = math.min(xExit, yExit)
          if (enterT <= exitT && exitT >= 0.0 && enterT <= 1.0) {
            Some(math.max(0.0, enterT))
          } else {
            None
          }
        case _ =>
          None
      }
    }

  private def segmentAxisInterval(start: Double, delta: Double, min: Double, max: Double): Option[(Double, Double)] =
    if (math.abs(delta) <= 0.000001) {
      Option.when(start >= min && start <= max)((Double.NegativeInfinity, Double.PositiveInfinity))
    } else {
      val firstT = (min - start) / delta
      val secondT = (max - start) / delta
      Some((math.min(firstT, secondT), math.max(firstT, secondT)))
    }

  private def isPointInAabb(position: BattleVector2, minX: Double, maxX: Double, minY: Double, maxY: Double): Boolean =
    position.x >= minX && position.x <= maxX && position.y >= minY && position.y <= maxY

  private def isWithinWorld(position: BattleVector2): Boolean =
    AuthoritativeArenaGeometry.isCenterInsideWorld(position)

  private def advanceSlowFields(fields: Vector[BattleSlowFieldState], deltaMs: Long): Vector[BattleSlowFieldState] =
    fields
      .map(field => field.copy(ttlMs = field.ttlMs - deltaMs))
      .filter(_.ttlMs > 0L)

  private def slowSpeedMultiplier(position: BattleVector2, fields: Vector[BattleSlowFieldState]): Double =
    if (fields.exists(field => distanceSquared(position, field.position) <= math.pow(field.radius, 2))) {
      freezeSpeedMultiplier
    } else {
      1.0
    }

  private def isZeroVector(vector: BattleVector2): Boolean =
    math.abs(vector.x) <= 0.0001 && math.abs(vector.y) <= 0.0001

  private def clamp(value: Double, min: Double, max: Double): Double =
    math.max(min, math.min(max, value))

  private val zeroVector = BattleVector2(0.0, 0.0)
}

object InMemoryAuthoritativeBattleRuntime {
  val DefaultBattleDurationMs: Long = BattleContentCatalog.DefaultBattleDurationMs
}
