package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleInitialLayout.*
import slaydemo.backend.battle.services.BattleReplayFrameRecorder.captureFrame
import slaydemo.backend.battle.services.BattleWeaponRules.createWeaponState
import slaydemo.backend.identity.objects.DisplayName

private[services] object BattleSessionStateFactory {
  def createInitialState(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis
  ): BattleAggregateState = {
    val startedAt = if seed.descriptor.startedAt.value > 0L then seed.descriptor.startedAt else now
    val players = bootstrapSeats(seed.descriptor).map(toPlayerState)
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
      worldSize = BattleArenaCatalog.WorldSize,
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

  private def bootstrapSeats(descriptor: BattleSessionDescriptor): Vector[BattleSessionBootstrapSeat] =
    descriptor.bootstrap.map(_.seats).getOrElse {
      descriptor.roster.map { entry =>
        BattleSessionBootstrapSeat(
          seat = entry.seat,
          playerId = entry.playerId,
          heroId = HeroId(s"hero-${entry.playerId.value}"),
          handle = entry.handle,
          displayName = DisplayName(entry.handle.value),
          joinedAt = entry.joinedAt,
          participantKind = BattleParticipantKind.Human,
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
      participantKind = seat.participantKind,
      position = spawnPointFor(seat.spawnPointIndex),
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = BattleArenaCatalog.ZeroVector,
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
      lifeState = BattlePlayerLifeState.Alive
    )
  }
}
