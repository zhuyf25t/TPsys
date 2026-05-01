package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest, BattleCommandSkillOutcome}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleArenaCollision.*
import slaydemo.backend.battle.services.BattleEventFactory.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleReplayFrameRecorder.*
import slaydemo.backend.battle.services.BattleSkillRules.*
import slaydemo.backend.battle.services.BattleWeaponRules.*
import slaydemo.backend.identity.objects.DisplayName

final case class BattleCommandOwnership(
  playerId: PlayerId,
  ticketId: TicketId
)

final case class BattleSessionSeed(
  roomId: RoomId,
  descriptor: BattleSessionDescriptor,
  commandOwnership: Vector[BattleCommandOwnership]
)

trait BattleSessionLookup {
  def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed]
}

enum BattleStateReadError {
  case BattleNotFound
}

enum BattleCommandSubmitError {
  case BattleNotFound
  case PlayerNotFound
  case BotCommandsNotSupported
  case CommandNotAuthorized
}

trait BattleStateService {
  def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState]
  def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted]
}

final class InMemoryBattleStateService(
  sessionLookup: BattleSessionLookup,
  currentTimeMillis: () => Long,
  battleDuration: DurationMillis,
  finishProjector: BattleFinishProjector,
  roomLifecycleSink: BattleRoomLifecycleSink
) extends BattleStateService {
  private final case class StoredBattle(
    state: BattleAggregateState,
    commandOwnershipByPlayerId: Map[PlayerId, TicketId],
    finishProjectionStatus: FinishProjectionStatus,
    lastUpdatedAt: EpochMillis,
    pendingStepMs: Long
  )

  private enum FinishProjectionStatus {
    case Pending
    case InProgress
    case Ready
    case NotConfigured
    case Failed(message: String)
  }

  private final case class StateRead(
    result: Either[BattleStateReadError, BattleAggregateState],
    projectionCandidate: Option[BattleAggregateState]
  )

  private final case class CommandSubmission(
    result: Either[BattleCommandSubmitError, BattleCommandAccepted],
    projectionCandidate: Option[BattleAggregateState]
  )

  private final case class CommandApplication(
    state: BattleAggregateState,
    outcomes: Vector[BattleCommandSkillOutcome]
  )

  private final case class SteppedMotionResult(
    destination: BattleVector2,
    blocked: Boolean,
    hitBlocker: Boolean
  )

  private final case class SteppedMotionScan(
    lastValid: BattleVector2,
    hitBlocker: Boolean
  )

  private final case class ProjectileMotionResult(
    destination: BattleVector2,
    segmentEnd: BattleVector2,
    terminalReason: Option[ProjectileTerminalReason]
  )

  private final case class ProjectileAdvance(
    state: BattleAggregateState,
    activeProjectiles: Vector[BattleProjectileState]
  )

  private final case class ProjectileBlock(
    t: Double,
    reason: ProjectileTerminalReason
  )

  private final case class ProjectilePlayerHit(
    player: BattlePlayerState,
    position: BattleVector2,
    distance: Double
  )

  private final case class ProjectileDamageReport(
    targetBefore: BattlePlayerState,
    targetAfter: BattlePlayerState
  )

  private val lock = Object()
  private var battles: Map[BattleId, StoredBattle] = Map.empty

  override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] = {
    val read = lock.synchronized {
      val now = EpochMillis(currentTimeMillis())
      findOrInitialize(battleId, now) match {
        case None =>
          StateRead(Left(BattleStateReadError.BattleNotFound), None)
        case Some(storedBattle) =>
          val (advanced, projectionCandidate) = prepareProjection(advanceStoredBattle(storedBattle, now))
          battles = battles.updated(battleId, advanced)
          StateRead(Right(advanced.state), projectionCandidate)
      }
    }

    read.projectionCandidate match {
      case Some(candidate) => Right(completeProjection(candidate.battleId, candidate))
      case None            => read.result
    }
  }

  override def acceptCommand(
    request: BattleCommandRequest
  ): Either[BattleCommandSubmitError, BattleCommandAccepted] = {
    val submission = lock.synchronized {
      val now = EpochMillis(currentTimeMillis())
      findOrInitialize(request.battleId, now) match {
        case None =>
          CommandSubmission(Left(BattleCommandSubmitError.BattleNotFound), None)
        case Some(storedBattle) =>
          val (advanced, projectionCandidate) = prepareProjection(advanceStoredBattle(storedBattle, now))
          advanced.state.players.find(_.playerId == request.playerId) match {
            case None =>
              battles = battles.updated(request.battleId, advanced)
              CommandSubmission(Left(BattleCommandSubmitError.PlayerNotFound), projectionCandidate)
            case Some(player) if player.isBot =>
              battles = battles.updated(request.battleId, advanced)
              CommandSubmission(Left(BattleCommandSubmitError.BotCommandsNotSupported), projectionCandidate)
            case Some(_) if advanced.commandOwnershipByPlayerId.get(request.playerId).forall(_ != request.ticketId) =>
              battles = battles.updated(request.battleId, advanced)
              CommandSubmission(Left(BattleCommandSubmitError.CommandNotAuthorized), projectionCandidate)
            case Some(player) if advanced.state.phase != BattlePhase.Active || !player.alive =>
              val ignored = BattleCommandAccepted(
                battleId = advanced.state.battleId,
                acceptedTick = advanced.state.tick,
                acceptedCommandSeq = player.lastClientCommandSeq,
                serverTime = now,
                commandStatus = BattleCommandStatus.Ignored,
                commandReason = Some(
                  if !player.alive then BattleCommandReason.PlayerDead
                  else if advanced.state.phase == BattlePhase.Finished then BattleCommandReason.BattleFinished
                  else BattleCommandReason.BattleInactive
                ),
                outcomes = Vector.empty
              )
              battles = battles.updated(request.battleId, advanced)
              CommandSubmission(Right(ignored), projectionCandidate)
            case Some(player) =>
              val applied = applyCommand(advanced.state, player, request)
              val nextState = applied.state
              battles = battles.updated(request.battleId, advanced.copy(state = nextState))
              CommandSubmission(
                Right(
                BattleCommandAccepted(
                  battleId = nextState.battleId,
                  acceptedTick = nextState.tick,
                  acceptedCommandSeq = lastClientCommandSeq(nextState, request.playerId),
                  serverTime = now,
                  commandStatus = BattleCommandStatus.Applied,
                  commandReason = None,
                  outcomes = applied.outcomes
                )
                ),
                projectionCandidate
              )
          }
      }
    }

    submission.projectionCandidate.foreach(candidate => completeProjection(candidate.battleId, candidate))
    submission.result
  }

  private def findOrInitialize(battleId: BattleId, now: EpochMillis): Option[StoredBattle] =
    battles.get(battleId).orElse {
      sessionLookup.activeBattleSession(battleId).map { seed =>
        val initialState = createInitialState(seed, now)
        val storedBattle = StoredBattle(
          state = initialState,
          commandOwnershipByPlayerId = seed.commandOwnership.map(entry => entry.playerId -> entry.ticketId).toMap,
          finishProjectionStatus = FinishProjectionStatus.Pending,
          lastUpdatedAt = initialState.serverTime,
          pendingStepMs = 0L
        )
        battles = battles.updated(battleId, storedBattle)
        storedBattle
      }
    }

  private def createInitialState(seed: BattleSessionSeed, now: EpochMillis): BattleAggregateState = {
    val startedAt = if seed.descriptor.startedAt.value > 0L then seed.descriptor.startedAt else now
    val players = seats(seed.descriptor).map(toPlayerState)
    val pickups = initialPickups
    BattleAggregateState(
      battleId = seed.descriptor.battleId,
      roomId = seed.roomId,
      phase = BattlePhase.Active,
      serverTime = startedAt,
      startedAt = startedAt,
      durationMs = battleDuration,
      elapsedMs = ElapsedMillis(0L),
      endsAt = EpochMillis(startedAt.value + battleDuration.value),
      worldSize = InMemoryBattleStateCatalog.WorldSize,
      tick = BattleTick(0L),
      artifactStatus = BattleArtifactStatus.Pending,
      players = players,
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = pickups,
      replayFrames = Vector(captureFrame(ElapsedMillis(0L), players, Vector.empty, pickups)),
      events = Vector.empty,
      winnerPlayerId = None,
      winnerHeroId = None
    )
  }

  private def seats(descriptor: BattleSessionDescriptor): Vector[BattleSessionBootstrapSeat] =
    descriptor.bootstrap.map(_.seats).getOrElse {
      descriptor.roster.map { entry =>
        BattleSessionBootstrapSeat(
          seat = entry.seat,
          playerId = entry.playerId,
          heroId = HeroId(s"hero-${entry.playerId.value}"),
          handle = entry.handle,
          displayName = DisplayName(entry.handle.value),
          joinedAt = entry.joinedAt,
          isBot = false,
          spawnPointIndex = SpawnPointIndex(entry.seat.value),
          rating = entry.rating,
          avatar = entry.avatar,
          skin = entry.skin
        )
      }
    }.sortBy(_.seat.value)

  private def toPlayerState(seat: BattleSessionBootstrapSeat): BattlePlayerState = {
    val weapon = createWeaponState(WeaponKind.Pistol)

    BattlePlayerState(
      playerId = seat.playerId,
      heroId = seat.heroId,
      handle = seat.handle,
      displayName = seat.displayName,
      seat = seat.seat,
      isBot = seat.isBot,
      position = spawnPointFor(seat.spawnPointIndex),
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = InMemoryBattleStateCatalog.ZeroVector,
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lastClientCommandSeq = ClientCommandSeq(0L),
      currentWeaponIndex = 0,
      weapons = Vector(weapon),
      currentWeaponKind = WeaponKind.Pistol,
      hp = HitPoints(100),
      maxHp = HitPoints(100),
      stamina = Stamina(100),
      maxStamina = Stamina(100),
      score = Score(0),
      kills = 0,
      skills = Vector(
        BattlePlayerSkillState(SkillKind.Blink, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Dash, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Freeze, CooldownMillis(0), DurationMillis(0L))
      ),
      alive = true,
      eliminatedAtMs = None,
      respawnMs = DurationMillis(0L)
    )
  }

  private def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication = {
    val inputPlayer = applyCommandToPlayer(player, request)
    val baseApplication = CommandApplication(replacePlayer(state, inputPlayer), Vector.empty)
    val skillApplications = Vector(
      Option.when(request.castBlink)((currentState: BattleAggregateState) => applyBlinkCommand(currentState, inputPlayer.playerId, request)),
      Option.when(request.castDash)((currentState: BattleAggregateState) => applyDashCommand(currentState, inputPlayer.playerId, request)),
      Option.when(request.castFreeze)((currentState: BattleAggregateState) => applyFreezeCommand(currentState, inputPlayer.playerId, request))
    ).flatten

    skillApplications.foldLeft(baseApplication) { case (currentApplication, applySkill) =>
      val applied = applySkill(currentApplication.state)
      CommandApplication(
        state = applied.state,
        outcomes = currentApplication.outcomes ++ applied.outcomes
      )
    }
  }

  private def applyCommandToPlayer(player: BattlePlayerState, request: BattleCommandRequest): BattlePlayerState = {
    val aim = normalizeAim(player.aim, BattleVector2(request.aim.x, request.aim.y))
    val movement = normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
    val suppressPrimaryHeld = request.castDash || request.castBlink || request.castFreeze
    val inputPlayer = player.copy(
      aim = aim,
      facing = FacingRadians(math.atan2(aim.y, aim.x)),
      movement = movement,
      sprint = request.sprint,
      primaryHeld = request.primaryHeld && !suppressPrimaryHeld,
      reloadPressed = request.reloadPressed,
      lastClientCommandSeq = maxClientCommandSeq(player.lastClientCommandSeq, request.clientCommandSeq)
    )
    applyWeaponSwitchRequest(inputPlayer, request.switchWeaponDirection, request.switchWeaponIndex)
  }

  private def maxClientCommandSeq(left: ClientCommandSeq, right: ClientCommandSeq): ClientCommandSeq =
    ClientCommandSeq(math.max(left.value, right.value))

  private def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): ClientCommandSeq =
    state.players.find(_.playerId == playerId).map(_.lastClientCommandSeq).getOrElse(ClientCommandSeq(0L))

  private def applyBlinkCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest
  ): CommandApplication =
    state.players.find(_.playerId == playerId) match {
      case None =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Blink).contains(SkillOutcomeReason.SkillNotOwned) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Blink).contains(SkillOutcomeReason.Cooldown) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Cooldown))))
      case Some(player) =>
        request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
          case None =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
          case Some(target) if !isInWorld(target, InMemoryBattleStateCatalog.PlayerCollisionRadius) =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
          case Some(target) if collidesWithArenaObstacles(target, InMemoryBattleStateCatalog.PlayerCollisionRadius) =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
          case Some(target) if !isBlinkTargetAllowed(player.position, target) =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
          case Some(target) =>
            val destination = blinkDestination(target)
            if isBlockedPoint(destination) then
              CommandApplication(state, Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
            else {
              val movedPlayer = player
                .copy(
                  position = destination,
                  skills = updateSkill(player.skills, SkillKind.Blink, InMemoryBattleStateCatalog.BlinkCooldownMs, InMemoryBattleStateCatalog.BlinkActiveMs)
                )
              CommandApplication(
                replacePlayer(state, movedPlayer),
                Vector(skillOutcome(SkillKind.Blink, SkillOutcomeStatus.Applied, None))
              )
            }
        }
    }

  private def applyDashCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest
  ): CommandApplication =
    state.players.find(_.playerId == playerId) match {
      case None =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Dash).contains(SkillOutcomeReason.SkillNotOwned) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Dash).contains(SkillOutcomeReason.Cooldown) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Cooldown))))
      case Some(player) =>
        val direction = normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
        val dashDirection =
          if vectorLength(direction) > 0.0 then direction else player.aim
        if vectorLength(dashDirection) <= 0.0 then
          CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.NoDirection))))
        else {
          val motion = findMotionDestination(
            position = player.position,
            direction = dashDirection,
            distance = InMemoryBattleStateCatalog.DashDistance,
            radius = InMemoryBattleStateCatalog.PlayerCollisionRadius
          )
          if distanceBetween(player.position, motion.destination) <= 0.001 then
            CommandApplication(state, Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Blocked))))
          else {
            val movedPlayer = player.copy(
              position = motion.destination,
              skills = updateSkill(player.skills, SkillKind.Dash, InMemoryBattleStateCatalog.DashCooldownMs, InMemoryBattleStateCatalog.DashActiveMs)
            )
            CommandApplication(
              replacePlayer(state, movedPlayer),
              Vector(skillOutcome(SkillKind.Dash, SkillOutcomeStatus.Applied, None))
            )
          }
        }
    }

  private def applyFreezeCommand(
    state: BattleAggregateState,
    playerId: PlayerId,
    request: BattleCommandRequest
  ): CommandApplication =
    state.players.find(_.playerId == playerId) match {
      case None =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Freeze).contains(SkillOutcomeReason.SkillNotOwned) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.SkillNotOwned))))
      case Some(player) if availabilityFailure(player.skills, SkillKind.Freeze).contains(SkillOutcomeReason.Cooldown) =>
        CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.Cooldown))))
      case Some(player) =>
        request.pointerWorld.map(vector => BattleVector2(vector.x, vector.y)) match {
          case None =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.MissingTarget))))
          case Some(target) if !isInWorld(target) =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.InvalidTarget))))
          case Some(target) if distanceBetween(player.position, target) > InMemoryBattleStateCatalog.FreezeCastRange =>
            CommandApplication(state, Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Noop, Some(SkillOutcomeReason.OutOfRange))))
          case Some(target) =>
            val field = BattleSlowFieldState(
              fieldId = SlowFieldId(s"slow-${player.playerId.value}-${request.clientCommandSeq.value}"),
              ownerPlayerId = player.playerId,
              ownerHeroId = player.heroId,
              position = target,
              radius = Radius(InMemoryBattleStateCatalog.FreezeRadius),
              ttlMs = DurationMillis(InMemoryBattleStateCatalog.FreezeDurationMs),
              durationMs = DurationMillis(InMemoryBattleStateCatalog.FreezeDurationMs)
            )
            val updatedPlayer = player.copy(
              skills = updateSkill(player.skills, SkillKind.Freeze, InMemoryBattleStateCatalog.FreezeCooldownMs, InMemoryBattleStateCatalog.FreezeDurationMs)
            )
            CommandApplication(
              replacePlayer(state, updatedPlayer).copy(slowFields = state.slowFields :+ field),
              Vector(skillOutcome(SkillKind.Freeze, SkillOutcomeStatus.Applied, None))
            )
        }
    }

  private def resolveHeldPrimaryFire(state: BattleAggregateState): BattleAggregateState =
    state.players.foldLeft(state) { (currentState, snapshotPlayer) =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.primaryHeld =>
          applyPrimaryFire(currentState, player, runtimeFireCommandSeq(currentState, player))
        case _ => currentState
      }
    }

  private def applyPrimaryFire(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq
  ): BattleAggregateState =
    currentWeapon(shooter) match {
      case Some(weapon) if weapon.weaponKind == WeaponKind.Pistol && canFireMagazineWeapon(weapon) =>
        val chargedWeapon = chargeMagazineWeapon(weapon, InMemoryBattleStateCatalog.PistolFireCooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, InMemoryBattleStateCatalog.PistolRecoilStrength)
        resolvePistolShot(replacePlayer(state, recoiledShooter), chargedShooter, commandSeq)
      case Some(weapon) if weapon.weaponKind == WeaponKind.RocketLauncher && canFireMagazineWeapon(weapon) =>
        val chargedWeapon = chargeMagazineWeapon(weapon, InMemoryBattleStateCatalog.RocketCooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, InMemoryBattleStateCatalog.RocketRecoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectileKind = ProjectileKind.Rocket,
            projectileSpeed = InMemoryBattleStateCatalog.RocketProjectileSpeed,
            projectileDamage = InMemoryBattleStateCatalog.RocketDamage,
            projectileRadius = InMemoryBattleStateCatalog.RocketProjectileRadius,
            projectileLifetimeMs = InMemoryBattleStateCatalog.RocketProjectileLifetimeMs,
            splashRadius = InMemoryBattleStateCatalog.RocketSplashRadius,
            pellets = 1,
            spreadRadians = 0.0
          )
        )
      case Some(weapon) if weapon.weaponKind == WeaponKind.Gatling && canFireHeatWeapon(weapon) =>
        val chargedWeapon = chargeGatlingWeapon(weapon)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, InMemoryBattleStateCatalog.GatlingRecoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectileKind = ProjectileKind.GatlingBullet,
            projectileSpeed = InMemoryBattleStateCatalog.GatlingProjectileSpeed,
            projectileDamage = InMemoryBattleStateCatalog.GatlingDamage,
            projectileRadius = InMemoryBattleStateCatalog.GatlingProjectileRadius,
            projectileLifetimeMs = InMemoryBattleStateCatalog.GatlingProjectileLifetimeMs,
            splashRadius = 0.0,
            pellets = 1,
            spreadRadians = InMemoryBattleStateCatalog.GatlingSpreadRadians
          )
        )
      case Some(weapon) if weapon.weaponKind == WeaponKind.Shotgun && canFireMagazineWeapon(weapon) =>
        val chargedWeapon = chargeMagazineWeapon(weapon, InMemoryBattleStateCatalog.ShotgunCooldownMs)
        val chargedShooter = updateCurrentWeapon(shooter, chargedWeapon)
        val recoiledShooter = applyWeaponRecoil(chargedShooter, chargedShooter.aim, InMemoryBattleStateCatalog.ShotgunRecoilStrength)
        replacePlayer(state, recoiledShooter).copy(
          projectiles = state.projectiles ++ weaponProjectiles(
            shooter = chargedShooter,
            commandSeq = commandSeq,
            projectileKind = ProjectileKind.ShotgunPellet,
            projectileSpeed = InMemoryBattleStateCatalog.ShotgunProjectileSpeed,
            projectileDamage = InMemoryBattleStateCatalog.ShotgunDamage,
            projectileRadius = InMemoryBattleStateCatalog.ShotgunProjectileRadius,
            projectileLifetimeMs = InMemoryBattleStateCatalog.ShotgunProjectileLifetimeMs,
            splashRadius = 0.0,
            pellets = InMemoryBattleStateCatalog.ShotgunPellets,
            spreadRadians = InMemoryBattleStateCatalog.ShotgunSpreadRadians
          )
        )
      case Some(weapon) if shouldAutoReload(weapon) =>
        replacePlayer(state, updateCurrentWeapon(shooter, startMagazineReload(weapon)))
      case _ => state
    }

  private def applyWeaponRecoil(
    player: BattlePlayerState,
    direction: BattleVector2,
    recoilStrength: Double
  ): BattlePlayerState = {
    val recoilDistance = math.min(24.0, math.max(0.0, recoilStrength) * 0.18)
    val recoilDirection = normalizeMovement(scale(direction, -1.0))
    if recoilDistance <= 0.0 || vectorLength(recoilDirection) <= 0.0001 then player
    else {
      val motion = findMotionDestination(
        position = player.position,
        direction = recoilDirection,
        distance = recoilDistance,
        radius = InMemoryBattleStateCatalog.PlayerCollisionRadius
      )
      player.copy(position = motion.destination)
    }
  }

  private def resolveRequestedReloads(state: BattleAggregateState): BattleAggregateState =
    state.players.foldLeft(state) { (currentState, snapshotPlayer) =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.reloadPressed =>
          currentWeapon(player) match {
            case Some(weapon) if canStartMagazineReload(weapon) =>
              replacePlayer(
                currentState,
                updateCurrentWeapon(player.copy(reloadPressed = false), startMagazineReload(weapon))
              )
            case _ =>
              replacePlayer(currentState, player.copy(reloadPressed = false))
          }
        case _ => currentState
      }
    }

  private def runtimeFireCommandSeq(state: BattleAggregateState, player: BattlePlayerState): ClientCommandSeq =
    ClientCommandSeq(-(((state.tick.value + 1L) * 1_000L) + player.seat.value.toLong + 1L))

  private def chargeGatlingWeapon(weapon: BattleWeaponState): BattleWeaponState = {
    val heatAfter = math.min(
      InMemoryBattleStateCatalog.GatlingMaxHeat,
      weapon.heat + InMemoryBattleStateCatalog.GatlingHeatPerShot
    )
    val overheated = heatAfter >= InMemoryBattleStateCatalog.GatlingMaxHeat
    weapon.copy(
      fireCooldownMs = CooldownMillis(InMemoryBattleStateCatalog.GatlingCooldownMs),
      heat = heatAfter,
      overheated = overheated,
      overheatRemainingMs =
        if overheated then CooldownMillis(InMemoryBattleStateCatalog.GatlingOverheatLockMs)
        else weapon.overheatRemainingMs
    )
  }

  private def weaponProjectiles(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectileKind: ProjectileKind,
    projectileSpeed: Double,
    projectileDamage: Int,
    projectileRadius: Double,
    projectileLifetimeMs: Long,
    splashRadius: Double,
    pellets: Int,
    spreadRadians: Double
  ): Vector[BattleProjectileState] = {
    val projectileCount = math.max(1, pellets)
    (0 until projectileCount).toVector.map { index =>
      val direction = spreadDirection(shooter.aim, commandSeq, index, projectileCount, spreadRadians)
      BattleProjectileState(
        projectileId = projectileId(shooter, commandSeq, index, projectileCount),
        ownerHeroId = shooter.heroId,
        projectileKind = projectileKind,
        position = projectileBirthPosition(shooter, direction, projectileRadius),
        velocity = scale(direction, projectileSpeed),
        facing = FacingRadians(math.atan2(direction.y, direction.x)),
        radius = Radius(projectileRadius),
        damage = Damage(projectileDamage),
        ttlMs = DurationMillis(projectileLifetimeMs),
        maxLifetimeMs = DurationMillis(projectileLifetimeMs),
        splashRadius = Radius(splashRadius)
      )
    }
  }

  private def projectileId(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    projectileIndex: Int,
    projectileCount: Int
  ): ProjectileId = {
    val suffix = if projectileCount == 1 then "" else s"-$projectileIndex"
    ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}$suffix")
  }

  private def spreadDirection(
    direction: BattleVector2,
    commandSeq: ClientCommandSeq,
    projectileIndex: Int,
    projectileCount: Int,
    spreadRadians: Double
  ): BattleVector2 =
    if projectileCount <= 1 || spreadRadians == 0.0 then direction
    else {
      val offset =
        ((projectileIndex.toDouble / (projectileCount - 1).toDouble) - 0.5) * spreadRadians
      rotate(direction, offset)
    }

  private def rotate(direction: BattleVector2, radians: Double): BattleVector2 = {
    val cos = math.cos(radians)
    val sin = math.sin(radians)
    normalizeMovement(
      BattleVector2(
        direction.x * cos - direction.y * sin,
        direction.x * sin + direction.y * cos
      )
    )
  }

  private def resolvePistolShot(
    state: BattleAggregateState,
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq
  ): BattleAggregateState = {
    val direction = shooter.aim
    val start = projectileBirthPosition(shooter, direction, InMemoryBattleStateCatalog.PistolProjectileRadius)
    state.copy(projectiles = state.projectiles :+ pistolProjectile(shooter, commandSeq, start, direction))
  }

  private def pistolProjectile(
    shooter: BattlePlayerState,
    commandSeq: ClientCommandSeq,
    start: BattleVector2,
    direction: BattleVector2
  ): BattleProjectileState =
    BattleProjectileState(
      projectileId = ProjectileId(s"projectile-${shooter.playerId.value}-${commandSeq.value}"),
      ownerHeroId = shooter.heroId,
      projectileKind = ProjectileKind.PistolBullet,
      position = start,
      velocity = scale(direction, InMemoryBattleStateCatalog.PistolProjectileSpeed),
      facing = shooter.facing,
      radius = Radius(InMemoryBattleStateCatalog.PistolProjectileRadius),
      damage = Damage(InMemoryBattleStateCatalog.PistolDamage),
      ttlMs = DurationMillis(InMemoryBattleStateCatalog.PistolProjectileLifetimeMs),
      maxLifetimeMs = DurationMillis(InMemoryBattleStateCatalog.PistolProjectileLifetimeMs),
      splashRadius = Radius(0.0)
    )

  private def projectileBirthPosition(
    shooter: BattlePlayerState,
    direction: BattleVector2,
    projectileRadius: Double
  ): BattleVector2 =
    add(
      shooter.position,
      scale(
        normalizeMovement(direction),
        InMemoryBattleStateCatalog.PlayerCollisionRadius +
          projectileRadius +
          InMemoryBattleStateCatalog.ProjectileBirthClearance
      )
    )

  private def collectPickups(state: BattleAggregateState): BattleAggregateState =
    state.pickups.filter(_.available).foldLeft(state) { (currentState, pickup) =>
      val contact = currentState.players
        .filter(player => player.alive && distanceBetween(player.position, pickup.position) <= InMemoryBattleStateCatalog.PickupContactRadius)
        .minByOption(player => distanceBetween(player.position, pickup.position))
      contact match {
        case None =>
          currentState
        case Some(player) =>
          val updatedPlayer = pickup.pickupKind match {
            case PickupKind.Medkit =>
              player.copy(hp = HitPoints(math.min(player.maxHp.value, player.hp.value + InMemoryBattleStateCatalog.MedkitHeal)))
            case PickupKind.Weapon =>
              pickup.weaponKind match {
                case Some(weaponKind) => equipOrRefillWeapon(player, weaponKind)
                case _ => player
              }
          }

          val consumedPickup = pickup.copy(
            available = false,
            respawnMs = DurationMillis(InMemoryBattleStateCatalog.PickupRespawnMs)
          )
          val eventKind =
            if pickup.pickupKind == PickupKind.Medkit then BattleEventKind.Heal else BattleEventKind.Pickup
          val eventMessage =
            pickup.pickupKind match {
              case PickupKind.Medkit =>
                None
              case PickupKind.Weapon =>
                Some(weaponPickupEventMessage(updatedPlayer, pickup))
            }

          currentState.copy(
            players = currentState.players.map(existing =>
              if existing.playerId == updatedPlayer.playerId then updatedPlayer else existing
            ),
            pickups = currentState.pickups.map(existing =>
              if existing.pickupId == consumedPickup.pickupId then consumedPickup else existing
            ),
            events = retainRecentEvents(currentState.events :+ battleEvent(
              currentState,
              eventKind,
              updatedPlayer,
              updatedPlayer,
              eventMessage,
              Some(pickupEventId(eventKind, pickup, updatedPlayer, currentState.elapsedMs))
            ))
          )
      }
    }

  private def skillOutcome(
    skill: SkillKind,
    status: SkillOutcomeStatus,
    reason: Option[SkillOutcomeReason]
  ): BattleCommandSkillOutcome =
    BattleCommandSkillOutcome(skill, status, reason)

  private def advanceStoredBattle(storedBattle: StoredBattle, now: EpochMillis): StoredBattle = {
    val safeNow =
      if now.value >= storedBattle.lastUpdatedAt.value then now
      else storedBattle.lastUpdatedAt

    if storedBattle.state.phase == BattlePhase.Finished then
      storedBattle.copy(
        state = storedBattle.state.copy(serverTime = safeNow),
        lastUpdatedAt = safeNow,
        pendingStepMs = 0L
      )
    else {
      val elapsedSinceLastUpdate = math.max(0L, safeNow.value - storedBattle.lastUpdatedAt.value)
      val accumulatedMs = storedBattle.pendingStepMs + elapsedSinceLastUpdate
      val steps = accumulatedMs / InMemoryBattleStateCatalog.TickStepMs
      val remainderMs = accumulatedMs % InMemoryBattleStateCatalog.TickStepMs

      val advancedState =
        if steps <= 0L then advanceStateStep(storedBattle.state, 0L, safeNow)
        else {
          val steppedThroughAt = safeNow.value - remainderMs
          val steppedState = (0L until steps).foldLeft(storedBattle.state) { case (currentState, stepIndex) =>
            val stepNow = EpochMillis(steppedThroughAt - ((steps - stepIndex - 1L) * InMemoryBattleStateCatalog.TickStepMs))
            advanceStateStep(currentState, InMemoryBattleStateCatalog.TickStepMs, stepNow)
          }
          advanceStateStep(steppedState, 0L, safeNow)
        }
      markRoomFinishedIfNeeded(storedBattle.state, advancedState)

      storedBattle.copy(
        state = advancedState,
        lastUpdatedAt = safeNow,
        pendingStepMs = if advancedState.phase == BattlePhase.Finished then 0L else remainderMs
      )
    }
  }

  private def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    if state.phase == BattlePhase.Finished then state.copy(serverTime = now)
    else {
      val targetElapsed = elapsedAt(state.startedAt, state.durationMs, now)
      val previousElapsed = elapsedAt(state.startedAt, state.durationMs, EpochMillis(now.value - math.max(0L, requestedDeltaMs)))
      val deltaMs = math.max(0L, targetElapsed - previousElapsed)
      val advancedRuntime =
        if deltaMs <= 0L then finalizeRuntimeStep(state, targetElapsed, now)
        else
          val clockedState = state.copy(
            serverTime = now,
            elapsedMs = ElapsedMillis(targetElapsed),
            tick = BattleTick(targetElapsed / InMemoryBattleStateCatalog.TickStepMs)
          )
          val afterSlowFields = advanceSlowFields(clockedState, deltaMs)
          val afterPlayers = advancePlayers(afterSlowFields, deltaMs)
          val afterPickups = advancePickups(afterPlayers, deltaMs)
          val afterRequestedReloads = resolveRequestedReloads(afterPickups)
          val afterHeldFire = resolveHeldPrimaryFire(afterRequestedReloads)
          val afterProjectiles = advanceProjectiles(afterHeldFire, deltaMs)
          val afterCollected = collectPickups(afterProjectiles)
          finalizeRuntimeStep(afterCollected, targetElapsed, now)

      advancedRuntime.copy(serverTime = now)
    }
  }

  private def elapsedAt(startedAt: EpochMillis, duration: DurationMillis, now: EpochMillis): Long =
    math.max(0L, math.min(duration.value, now.value - startedAt.value))

  private def finalizeRuntimeStep(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val phase = if isBattleFinished(state, elapsed) then BattlePhase.Finished else BattlePhase.Active
    if phase == BattlePhase.Finished then finishRuntimeState(state, elapsed, now)
    else
      val activeState = state.copy(
        phase = BattlePhase.Active,
        serverTime = now,
        elapsedMs = ElapsedMillis(elapsed),
        tick = BattleTick(elapsed / InMemoryBattleStateCatalog.TickStepMs),
        winnerPlayerId = None,
        winnerHeroId = None
      )
      activeState.copy(
        replayFrames = updateFrames(
          activeState.replayFrames,
          activeState.elapsedMs,
          activeState.players,
          activeState.projectiles,
          activeState.pickups,
          hasRuntimeEvents = activeState.events.exists(_.elapsedMs == activeState.elapsedMs),
          finished = false
        )
      )
  }

  private def advancePlayers(state: BattleAggregateState, deltaMs: Long): BattleAggregateState = {
    val nextElapsed = state.elapsedMs.value
    val previousElapsed = math.max(0L, nextElapsed - deltaMs)
    val advancedPlayers = state.players.map { player =>
      val withTimers = advancePlayerTimers(player, deltaMs, previousElapsed, nextElapsed)
      val controlledPlayer =
        if withTimers.alive && withTimers.isBot then applyBotControl(withTimers, state)
        else withTimers
      if controlledPlayer.alive then movePlayer(controlledPlayer, deltaMs, state.slowFields, previousElapsed, nextElapsed)
      else
        clearDeadPlayerRuntime(controlledPlayer)
    }

    state.copy(players = advancedPlayers)
  }

  private def applyBotControl(player: BattlePlayerState, state: BattleAggregateState): BattlePlayerState = {
    val aliveOpponents = state.players.filter(candidate => candidate.playerId != player.playerId && candidate.alive)
    val preferredTargets = aliveOpponents.filterNot(_.isBot)
    val targetPool = if preferredTargets.nonEmpty then preferredTargets else aliveOpponents

    targetPool.minByOption(candidate => distanceBetween(player.position, candidate.position)) match {
      case Some(target) =>
        val toTarget = subtract(target.position, player.position)
        val distance = vectorLength(toTarget)
        val aim = normalizeAim(player.aim, toTarget)
        val orbitDirection =
          if (state.tick.value + player.seat.value.toLong) % 2L == 0L then 1.0
          else -1.0
        val orbit = perpendicular(aim, orbitDirection)
        val radial =
          if distance > InMemoryBattleStateCatalog.BotPreferredRange + 120.0 then aim
          else if distance < InMemoryBattleStateCatalog.BotPreferredRange - 90.0 then scale(aim, -1.0)
          else InMemoryBattleStateCatalog.ZeroVector
        val movement = normalizeMovement(add(scale(radial, 0.86), scale(orbit, 0.52)))
        val resolvedMovement =
          if vectorLength(movement) <= 0.0001 then orbit
          else movement

        player.copy(
          aim = aim,
          facing = FacingRadians(math.atan2(aim.y, aim.x)),
          movement = resolvedMovement,
          sprint = false,
          primaryHeld = distance <= botFireRangeForTarget(target) && canBotFireAtTarget(target, state),
          reloadPressed = shouldBotReload(player)
        )

      case None =>
        val spawnAnchor = spawnPointFor(SpawnPointIndex(player.seat.value))
        val patrolAngle = (state.tick.value + player.seat.value.toLong * 11L).toDouble * 0.18
        val patrolTarget = BattleVector2(
          clampDouble(spawnAnchor.x + math.cos(patrolAngle) * 140.0, 0.0, InMemoryBattleStateCatalog.WorldSize.x),
          clampDouble(spawnAnchor.y + math.sin(patrolAngle) * 110.0, 0.0, InMemoryBattleStateCatalog.WorldSize.y)
        )
        val movement = normalizeMovement(subtract(patrolTarget, player.position))
        val aim = normalizeAim(player.aim, movement)

        player.copy(
          aim = aim,
          facing = FacingRadians(math.atan2(aim.y, aim.x)),
          movement = movement,
          sprint = false,
          primaryHeld = false,
          reloadPressed = shouldBotReload(player)
        )
    }
  }

  private def shouldBotReload(player: BattlePlayerState): Boolean =
    player.isBot && currentWeapon(player).exists(weapon =>
      weapon.ammoInMagazine.value <= 0 && canStartMagazineReload(weapon)
    )

  private def canBotFireAtTarget(target: BattlePlayerState, state: BattleAggregateState): Boolean =
    target.isBot || state.elapsedMs.value >= InMemoryBattleStateCatalog.BotHumanOpeningFireDelayMs

  private def botFireRangeForTarget(target: BattlePlayerState): Double =
    if target.isBot then InMemoryBattleStateCatalog.BotFireRange
    else InMemoryBattleStateCatalog.BotHumanFireRange

  private def isBattleFinished(state: BattleAggregateState, elapsed: Long): Boolean =
    state.phase == BattlePhase.Finished ||
      elapsed >= state.durationMs.value ||
      state.players.count(player => player.alive && player.hp.value > 0) <= 1

  private def finishRuntimeState(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val finishedPlayers = state.players.map(clearFinishedPlayerRuntime)
    val winner = winnerFor(finishedPlayers)
    state.copy(
      phase = BattlePhase.Finished,
      serverTime = now,
      elapsedMs = ElapsedMillis(elapsed),
      tick = BattleTick(elapsed / InMemoryBattleStateCatalog.TickStepMs),
      players = finishedPlayers,
      projectiles = Vector.empty,
      slowFields = state.slowFields,
      replayFrames = appendFrame(
        state.replayFrames,
        ElapsedMillis(elapsed),
        finishedPlayers,
        Vector.empty,
        state.pickups
      ),
      winnerPlayerId = winner.map(_.playerId),
      winnerHeroId = winner.map(_.heroId)
    )
  }

  private def markRoomFinishedIfNeeded(
    previousState: BattleAggregateState,
    nextState: BattleAggregateState
  ): Unit =
    if previousState.phase != BattlePhase.Finished && nextState.phase == BattlePhase.Finished then
      roomLifecycleSink.markBattleFinished(nextState.roomId, finishedAtForRoom(nextState))

  private def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    if state.elapsedMs.value >= state.durationMs.value then state.endsAt
    else state.serverTime

  private def clearFinishedPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    clearDeadPlayerRuntime(player)

  private def clearDeadPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    player.copy(
      movement = InMemoryBattleStateCatalog.ZeroVector,
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      respawnMs = DurationMillis(0L),
      skills = player.skills.map(skill => skill.copy(activeMs = DurationMillis(0L))),
      weapons = player.weapons.map(weapon =>
        weapon.copy(
          fireCooldownMs = CooldownMillis(0),
          reloadRemainingMs = CooldownMillis(0)
        )
      )
    )

  private def winnerFor(players: Vector[BattlePlayerState]): Option[BattlePlayerState] =
    players.filter(player => player.alive && player.hp.value > 0) match {
      case Vector(winner) => Some(winner)
      case _              => None
    }

  private def advancePlayerTimers(
    player: BattlePlayerState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): BattlePlayerState = {
    val withSkills = player.copy(skills = player.skills.map { skill =>
      skill.copy(
        cooldownMs = CooldownMillis(decrementInt(skill.cooldownMs.value, deltaMs)),
        activeMs = DurationMillis(decrementLong(skill.activeMs.value, deltaMs))
      )
    })

    val timedWeapons = withSkills.weapons.zipWithIndex.map { case (weapon, index) =>
      val heatAdvanced = advanceWeaponHeat(weapon, deltaMs, previousElapsed, nextElapsed)
      if index == withSkills.currentWeaponIndex then
        val timerWeapon = heatAdvanced.copy(
          fireCooldownMs = CooldownMillis(decrementInt(heatAdvanced.fireCooldownMs.value, deltaMs))
        )
        if timerWeapon.reloadRemainingMs.value <= 0 then timerWeapon
        else {
          val remaining = decrementInt(timerWeapon.reloadRemainingMs.value, deltaMs)
          if remaining > 0 then timerWeapon.copy(reloadRemainingMs = CooldownMillis(remaining))
          else finishReload(timerWeapon)
        }
      else heatAdvanced
    }

    if timedWeapons.isEmpty then withSkills
    else {
      val currentIndex = clampWeaponIndex(withSkills.currentWeaponIndex, timedWeapons.length)
      withSkills.copy(
        currentWeaponIndex = currentIndex,
        currentWeaponKind = timedWeapons(currentIndex).weaponKind,
        weapons = timedWeapons
      )
    }
  }

  private def advanceWeaponHeat(
    weapon: BattleWeaponState,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): BattleWeaponState = {
    if !weaponUsesHeat(weapon.weaponKind) then weapon.copy(heat = 0, overheated = false, overheatRemainingMs = CooldownMillis(0))
    else {
      val timerWeapon = weapon.copy(overheatRemainingMs = CooldownMillis(decrementInt(weapon.overheatRemainingMs.value, deltaMs)))
      val heatDelta = elapsedRateDelta(InMemoryBattleStateCatalog.GatlingCoolRatePerSecond, previousElapsed, nextElapsed)
      val cooledWeapon = timerWeapon.copy(heat = math.max(0, timerWeapon.heat - heatDelta))
      if cooledWeapon.overheated && cooledWeapon.overheatRemainingMs.value <= 0 then cooledWeapon.copy(overheated = false)
      else cooledWeapon
    }
  }

  private def movePlayer(
    player: BattlePlayerState,
    deltaMs: Long,
    slowFields: Vector[BattleSlowFieldState],
    previousElapsed: Long,
    nextElapsed: Long
  ): BattlePlayerState = {
    val hasMovement = vectorLength(player.movement) > 0.0 && deltaMs > 0L
    val canSprint = player.sprint && hasMovement && player.stamina.value > 0.0
    val nextStamina = advanceStamina(player, canSprint, deltaMs, previousElapsed, nextElapsed)
    val withStamina = player.copy(stamina = nextStamina, sprint = canSprint)

    if !hasMovement then withStamina
    else {
      val baseSpeed =
        if player.isBot then InMemoryBattleStateCatalog.BotMoveSpeed
        else if canSprint then InMemoryBattleStateCatalog.SprintSpeed
        else InMemoryBattleStateCatalog.WalkSpeed
      val slowFactor =
        if slowFields.exists(field => distanceBetween(player.position, field.position) <= field.radius.value) then
          InMemoryBattleStateCatalog.SlowFieldMovementFactor
        else 1.0
      val distance = baseSpeed * slowFactor * deltaMs.toDouble / 1000.0
      val motion = findMotionDestination(
        position = player.position,
        direction = player.movement,
        distance = distance,
        radius = InMemoryBattleStateCatalog.PlayerCollisionRadius
      )
      withStamina.copy(position = motion.destination)
    }
  }

  private def advanceStamina(
    player: BattlePlayerState,
    sprinting: Boolean,
    deltaMs: Long,
    previousElapsed: Long,
    nextElapsed: Long
  ): Stamina = {
    if deltaMs <= 0L then player.stamina
    else {
      val delta =
        if sprinting then -InMemoryBattleStateCatalog.StaminaDrainPerSecond
        else InMemoryBattleStateCatalog.StaminaRecoverPerSecond
      val staminaDelta = elapsedRateDeltaDouble(math.abs(delta), previousElapsed, nextElapsed)
      val signedDelta = if delta < 0 then -staminaDelta else staminaDelta
      Stamina(math.max(0, math.min(player.maxStamina.value, player.stamina.value + signedDelta)))
    }
  }

  private def elapsedRateDeltaDouble(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Double =
    ratePerSecond * math.max(0L, nextElapsed - previousElapsed).toDouble / 1000.0

  private def elapsedRateDelta(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Int = {
    val previous = math.round(ratePerSecond * math.max(0L, previousElapsed).toDouble / 1000.0)
    val next = math.round(ratePerSecond * math.max(0L, nextElapsed).toDouble / 1000.0)
    math.max(0L, next - previous).toInt
  }

  private def findMotionDestination(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): SteppedMotionResult = {
    val normalized = normalizeMovement(direction)
    val clampedDistance = math.max(0.0, distance)
    val fullMotion = resolveSteppedMotion(position, normalized, clampedDistance, radius)

    if !fullMotion.hitBlocker then fullMotion
    else {
      val xDistance = math.abs(normalized.x * clampedDistance)
      val yDistance = math.abs(normalized.y * clampedDistance)
      val xMotion =
        if xDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(math.signum(normalized.x), 0.0), xDistance, radius)
        else fullMotion
      val yMotion =
        if yDistance > 0.0 then resolveSteppedMotion(position, BattleVector2(0.0, math.signum(normalized.y)), yDistance, radius)
        else fullMotion

      Vector(fullMotion, xMotion, yMotion).maxBy(motion => distanceBetween(position, motion.destination))
    }
  }

  private def resolveSteppedMotion(
    position: BattleVector2,
    direction: BattleVector2,
    distance: Double,
    radius: Double
  ): SteppedMotionResult = {
    val clampedDistance = math.max(0.0, distance)
    val steps = math.ceil(clampedDistance / InMemoryBattleStateCatalog.MotionStepSize).toInt
    val scan = (1 to steps).foldLeft(SteppedMotionScan(position, hitBlocker = false)) { (current, step) =>
      if current.hitBlocker then current
      else {
        val travel = math.min(clampedDistance, step.toDouble * InMemoryBattleStateCatalog.MotionStepSize)
        val candidate = add(position, scale(direction, travel))
        if canPlayerOccupy(candidate, radius) then current.copy(lastValid = candidate)
        else current.copy(hitBlocker = true)
      }
    }

    SteppedMotionResult(
      destination = scan.lastValid,
      blocked = scan.lastValid == position,
      hitBlocker = scan.hitBlocker
    )
  }

  private def advanceSlowFields(state: BattleAggregateState, deltaMs: Long): BattleAggregateState =
    state.copy(
      slowFields = state.slowFields
        .map(field => field.copy(ttlMs = DurationMillis(decrementLong(field.ttlMs.value, deltaMs))))
        .filter(_.ttlMs.value > 0L)
    )

  private def advancePickups(state: BattleAggregateState, deltaMs: Long): BattleAggregateState =
    state.copy(
      pickups = state.pickups.map { pickup =>
        if pickup.available then pickup
        else {
          val remaining = decrementLong(pickup.respawnMs.value, deltaMs)
          if remaining <= 0L then pickup.copy(available = true, respawnMs = DurationMillis(0L))
          else pickup.copy(respawnMs = DurationMillis(remaining))
        }
      }
    )

  private def advanceProjectiles(state: BattleAggregateState, deltaMs: Long): BattleAggregateState = {
    val advanced = state.projectiles.foldLeft(ProjectileAdvance(state, Vector.empty)) { (current, projectile) =>
      val travelMs = math.min(math.max(0L, deltaMs), math.max(0L, projectile.ttlMs.value))
      val speedFactor =
        if state.slowFields.exists(field => distanceBetween(projectile.position, field.position) <= field.radius.value) then
          InMemoryBattleStateCatalog.SlowFieldProjectileFactor
        else 1.0
      val motion = resolveProjectileMotion(projectile, speedFactor, travelMs)
      val nextTtl = decrementLong(projectile.ttlMs.value, travelMs)
      val playerHit = findProjectilePlayerHit(current.state.players, projectile, motion.destination)
      val reason = playerHit match {
        case Some(_) => Some(ProjectileTerminalReason.Hit)
        case None =>
          if nextTtl <= 0L then Some(ProjectileTerminalReason.Expired)
          else motion.terminalReason
      }

      reason match {
        case Some(terminalReason) if playerHit.nonEmpty =>
          val hit = playerHit.get
          current.copy(state = applyProjectileImpact(current.state, projectile, terminalReason, hit.position, motion.segmentEnd, nextTtl, Some(hit.player)))
        case Some(terminalReason) =>
          current.copy(state = applyProjectileImpact(current.state, projectile, terminalReason, motion.destination, motion.segmentEnd, nextTtl, None))
        case None =>
          current.copy(activeProjectiles = current.activeProjectiles :+ projectile.copy(position = motion.destination, ttlMs = DurationMillis(nextTtl)))
      }
    }

    advanced.state.copy(projectiles = advanced.activeProjectiles)
  }

  private def resolveProjectileMotion(
    projectile: BattleProjectileState,
    speedFactor: Double,
    deltaMs: Long
  ): ProjectileMotionResult = {
    val direction = normalizeMovement(projectile.velocity)
    val distance = vectorLength(projectile.velocity) * speedFactor * math.max(0L, deltaMs).toDouble / 1000.0
    val end = add(projectile.position, scale(direction, distance))
    val block = firstProjectileBlock(projectile.position, end, projectile.radius.value)

    block match {
      case Some(value) =>
        ProjectileMotionResult(
          destination = pointAtSegmentT(projectile.position, end, value.t),
          segmentEnd = end,
          terminalReason = Some(value.reason)
        )
      case None =>
        ProjectileMotionResult(destination = end, segmentEnd = end, terminalReason = None)
    }
  }

  private def firstProjectileBlock(
    start: BattleVector2,
    end: BattleVector2,
    radius: Double
  ): Option[ProjectileBlock] = {
    val worldExit = firstSegmentWorldExitT(start, end, radius).map(t => ProjectileBlock(t, ProjectileTerminalReason.OutOfBounds))
    val obstacleEnter = InMemoryBattleStateCatalog.ArenaObstacles
      .flatMap(obstacle => firstSegmentObstacleEnterT(start, end, radius, obstacle))
      .minOption
      .map(t => ProjectileBlock(t, ProjectileTerminalReason.Blocked))

    (worldExit, obstacleEnter) match {
      case (Some(world), Some(obstacle)) if world.t <= obstacle.t => Some(world)
      case (Some(_), Some(obstacle))                             => Some(obstacle)
      case (Some(world), None)                                   => Some(world)
      case (None, Some(obstacle))                                => Some(obstacle)
      case (None, None)                                          => None
    }
  }

  private def applyProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState]
  ): BattleAggregateState =
    if projectile.projectileKind == ProjectileKind.Rocket && projectile.splashRadius.value > 0.0 then
      applyRocketProjectileImpact(state, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, directTarget)
    else
      val (damagedState, report) = directTarget match {
        case Some(target) => damageProjectileTarget(state, projectile, target)
        case None         => state -> None
      }
      appendProjectileTerminal(
        damagedState,
        terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, report)
      )

  private def applyRocketProjectileImpact(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    directTarget: Option[BattlePlayerState]
  ): BattleAggregateState = {
    val splashTargets = state.players
      .filter(player =>
        player.alive &&
          player.heroId != projectile.ownerHeroId &&
          distanceBetween(player.position, terminalPosition) <= projectile.splashRadius.value + InMemoryBattleStateCatalog.PlayerCollisionRadius
      )
      .sortBy(player =>
        if directTarget.exists(_.playerId == player.playerId) then -1.0
        else distanceBetween(player.position, terminalPosition)
      )

    val (damagedState, reports) = splashTargets.foldLeft(state -> Vector.empty[ProjectileDamageReport]) {
      case ((currentState, currentReports), target) =>
        val currentTarget = currentState.players.find(_.playerId == target.playerId)
        currentTarget match {
          case Some(player) if player.alive =>
            val (nextState, report) = damageProjectileTarget(currentState, projectile, player)
            nextState -> (currentReports ++ report)
          case _ =>
            currentState -> currentReports
        }
    }

    appendProjectileTerminal(
      damagedState,
      terminalForProjectile(damagedState, projectile, reason, terminalPosition, segmentEnd, ttlAfterValue, reports.headOption)
    )
  }

  private def appendProjectileTerminal(
    state: BattleAggregateState,
    terminal: BattleProjectileTerminalState
  ): BattleAggregateState =
    state.copy(projectileTerminals = retainRecentProjectileTerminals(state.projectileTerminals :+ terminal))

  private def retainRecentProjectileTerminals(
    terminals: Vector[BattleProjectileTerminalState]
  ): Vector[BattleProjectileTerminalState] =
    terminals.takeRight(InMemoryBattleStateCatalog.RetainedProjectileTerminalCount)

  private def retainRecentEvents(events: Vector[BattleEventState]): Vector[BattleEventState] =
    events.takeRight(InMemoryBattleStateCatalog.RetainedBattleEventCount)

  private def damageProjectileTarget(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    target: BattlePlayerState
  ): (BattleAggregateState, Option[ProjectileDamageReport]) =
    if !target.alive || target.heroId == projectile.ownerHeroId then state -> None
    else {
      val hpBefore = target.hp
      val hpAfterValue = math.max(0, target.hp.value - projectile.damage.value)
      val eliminated = hpAfterValue <= 0
      val damagedTarget =
        if eliminated then
          clearDeadPlayerRuntime(target.copy(
            hp = HitPoints(0),
            alive = false,
            eliminatedAtMs = target.eliminatedAtMs.orElse(Some(state.elapsedMs)),
            respawnMs = DurationMillis(0L)
          ))
        else target.copy(hp = HitPoints(hpAfterValue))

      val creditedOwner = state.players.find(_.heroId == projectile.ownerHeroId).map { owner =>
        if eliminated then owner.copy(score = Score(owner.score.value + 1), kills = owner.kills + 1)
        else owner
      }

      val updatedPlayers = state.players.map { player =>
        if player.playerId == damagedTarget.playerId then damagedTarget
        else creditedOwner.filter(_.playerId == player.playerId).getOrElse(player)
      }
      val stateWithPlayers = state.copy(players = updatedPlayers)
      val stateWithEvents =
        if eliminated then
          creditedOwner match {
            case Some(owner) =>
              stateWithPlayers.copy(events = retainRecentEvents(stateWithPlayers.events :+ battleEvent(stateWithPlayers, BattleEventKind.Kill, owner, damagedTarget)))
            case None => stateWithPlayers
          }
        else stateWithPlayers

      stateWithEvents -> Some(ProjectileDamageReport(target.copy(hp = hpBefore), damagedTarget))
    }

  private def terminalForProjectile(
    state: BattleAggregateState,
    projectile: BattleProjectileState,
    reason: ProjectileTerminalReason,
    terminalPosition: BattleVector2,
    segmentEnd: BattleVector2,
    ttlAfterValue: Long,
    damageReport: Option[ProjectileDamageReport] = None
  ): BattleProjectileTerminalState = {
    val owner = state.players.find(_.heroId == projectile.ownerHeroId)
    BattleProjectileTerminalState(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      ownerPlayerId = owner.map(_.playerId).getOrElse(PlayerId(projectile.ownerHeroId.value)),
      ownerHeroId = projectile.ownerHeroId,
      reason = reason,
      start = projectile.position,
      end = segmentEnd,
      terminalPosition = terminalPosition,
      ttlBefore = projectile.ttlMs,
      ttlAfter = DurationMillis(math.max(0L, ttlAfterValue)),
      elapsedMs = state.elapsedMs,
      targetPlayerId = damageReport.map(_.targetAfter.playerId),
      targetHeroId = damageReport.map(_.targetAfter.heroId),
      hpBefore = damageReport.map(_.targetBefore.hp),
      hpAfter = damageReport.map(_.targetAfter.hp),
      damage = damageReport.map(_ => projectile.damage)
    )
  }

  private def prepareProjection(storedBattle: StoredBattle): (StoredBattle, Option[BattleAggregateState]) = {
    val state = storedBattle.state
    if state.phase != BattlePhase.Finished then storedBattle -> None
    else
      storedBattle.finishProjectionStatus match {
        case FinishProjectionStatus.Ready =>
          storedBattle.copy(state = state.copy(artifactStatus = BattleArtifactStatus.Ready)) -> None
        case FinishProjectionStatus.InProgress | FinishProjectionStatus.NotConfigured =>
          storedBattle -> None
        case FinishProjectionStatus.Pending | FinishProjectionStatus.Failed(_) =>
          storedBattle.copy(finishProjectionStatus = FinishProjectionStatus.InProgress) -> Some(state)
      }
  }

  private def completeProjection(battleId: BattleId, candidate: BattleAggregateState): BattleAggregateState = {
    val outcome = finishProjector.project(candidate)
    lock.synchronized {
      battles.get(battleId) match {
        case None =>
          candidate
        case Some(storedBattle) if storedBattle.finishProjectionStatus != FinishProjectionStatus.InProgress =>
          storedBattle.state
        case Some(storedBattle) =>
          val artifactStatus = BattleArtifactStatus.merge(
            storedBattle.state.artifactStatus,
            BattleFinishProjectionOutcome.artifactStatus(outcome)
          )
          val updated = outcome match {
            case BattleFinishProjectionOutcome.Projected =>
              storedBattle.copy(
                state = storedBattle.state.copy(artifactStatus = artifactStatus),
                finishProjectionStatus = FinishProjectionStatus.Ready
              )
            case BattleFinishProjectionOutcome.NotConfigured =>
              storedBattle.copy(
                state = storedBattle.state.copy(artifactStatus = artifactStatus),
                finishProjectionStatus = FinishProjectionStatus.NotConfigured
              )
            case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) =>
              storedBattle.copy(
                state = storedBattle.state.copy(artifactStatus = artifactStatus),
                finishProjectionStatus =
                  if artifactStatus == BattleArtifactStatus.Ready then FinishProjectionStatus.Ready
                  else FinishProjectionStatus.Failed(message)
              )
            case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) =>
              storedBattle.copy(
                state = storedBattle.state.copy(artifactStatus = artifactStatus),
                finishProjectionStatus =
                  if artifactStatus == BattleArtifactStatus.Ready then FinishProjectionStatus.Ready
                  else FinishProjectionStatus.Failed(message)
              )
            case BattleFinishProjectionOutcome.Failed(message) =>
              storedBattle.copy(
                state = storedBattle.state.copy(artifactStatus = artifactStatus),
                finishProjectionStatus = FinishProjectionStatus.Failed(message)
              )
          }
          battles = battles.updated(battleId, updated)
          updated.state
      }
    }
  }

  private def spawnPointFor(index: SpawnPointIndex): BattleVector2 =
    InMemoryBattleStateCatalog.SpawnPoints.lift(index.value).getOrElse {
      val fallbackX = 240.0 + (index.value % 3) * 320.0
      val fallbackY = 240.0 + (index.value / 3) * 260.0
      BattleVector2(fallbackX, fallbackY)
    }

  private def initialPickups: Vector[BattlePickupState] =
    Vector(
      BattlePickupState(
        pickupId = PickupId("pickup-medkit-1"),
        pickupKind = PickupKind.Medkit,
        weaponKind = None,
        position = BattleVector2(960.0, 608.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-medkit-2"),
        pickupKind = PickupKind.Medkit,
        weaponKind = None,
        position = BattleVector2(1600.0, 992.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-rocket-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.RocketLauncher),
        position = BattleVector2(1280.0, 256.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-gatling-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Gatling),
        position = BattleVector2(704.0, 800.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-shotgun-1"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Shotgun),
        position = BattleVector2(1856.0, 800.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-rocket-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.RocketLauncher),
        position = BattleVector2(1280.0, 1344.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-gatling-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Gatling),
        position = BattleVector2(448.0, 800.0),
        available = true,
        respawnMs = DurationMillis(0L)
      ),
      BattlePickupState(
        pickupId = PickupId("pickup-shotgun-2"),
        pickupKind = PickupKind.Weapon,
        weaponKind = Some(WeaponKind.Shotgun),
        position = BattleVector2(2112.0, 800.0),
        available = true,
        respawnMs = DurationMillis(0L)
      )
    )

  private def normalizeAim(previous: BattleVector2, next: BattleVector2): BattleVector2 = {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then previous else BattleVector2(next.x / length, next.y / length)
  }

  private def normalizeMovement(next: BattleVector2): BattleVector2 = {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then InMemoryBattleStateCatalog.ZeroVector
    else BattleVector2(next.x / length, next.y / length)
  }

  private def replacePlayer(state: BattleAggregateState, player: BattlePlayerState): BattleAggregateState =
    state.copy(players = state.players.map(existing => if existing.playerId == player.playerId then player else existing))

  private def updateSkill(
    skills: Vector[BattlePlayerSkillState],
    skillKind: SkillKind,
    cooldownMs: Int,
    activeMs: Long
  ): Vector[BattlePlayerSkillState] =
    skills.map { skill =>
      if skill.skillKind == skillKind then
        skill.copy(cooldownMs = CooldownMillis(cooldownMs), activeMs = DurationMillis(activeMs))
      else skill
    }

  private def isBlinkTargetAllowed(from: BattleVector2, target: BattleVector2): Boolean = {
    val distance = distanceBetween(from, target)
    distance <= InMemoryBattleStateCatalog.BlinkRange
  }

  private def blinkDestination(target: BattleVector2): BattleVector2 =
    target

  private def findProjectilePlayerHit(
    players: Vector[BattlePlayerState],
    projectile: BattleProjectileState,
    destination: BattleVector2
  ): Option[ProjectilePlayerHit] = {
    val path = BattleVector2(destination.x - projectile.position.x, destination.y - projectile.position.y)
    val pathLength = vectorLength(path)
    if pathLength <= 0.0001 then None
    else {
      players
        .filter(player => player.alive && player.heroId != projectile.ownerHeroId)
        .flatMap { player =>
          val hitRadius =
            projectile.radius.value +
              InMemoryBattleStateCatalog.PlayerCollisionRadius +
              InMemoryBattleStateCatalog.ProjectileShooterAdvantageRadius
          segmentCircleHitT(projectile.position, destination, player.position, hitRadius).map { hitT =>
            ProjectilePlayerHit(player, pointAtSegmentT(projectile.position, destination, hitT), hitT * pathLength)
          }
        }
        .sortBy(_.distance)
        .headOption
    }
  }

  private def decrementInt(value: Int, deltaMs: Long): Int =
    math.max(0, value - deltaMs.toInt)

  private def decrementLong(value: Long, deltaMs: Long): Long =
    math.max(0L, value - deltaMs)

}
object InMemoryBattleStateService {
  val DefaultBattleDuration: DurationMillis = InMemoryBattleStateCatalog.DefaultBattleDuration

  def apply(sessionLookup: BattleSessionLookup): InMemoryBattleStateService =
    apply(sessionLookup, DefaultBattleDuration)

  def apply(sessionLookup: BattleSessionLookup, battleDuration: DurationMillis): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, NoopBattleFinishProjector)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    finishProjector: BattleFinishProjector
  ): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, finishProjector, NoopBattleRoomLifecycleSink)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): InMemoryBattleStateService =
    new InMemoryBattleStateService(
      sessionLookup = sessionLookup,
      currentTimeMillis = () => System.currentTimeMillis(),
      battleDuration = battleDuration,
      finishProjector = finishProjector,
      roomLifecycleSink = roomLifecycleSink
    )
}
