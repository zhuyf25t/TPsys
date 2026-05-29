package services.battle.microservices.session.services

import services.battle.microservices.runtime.services.BattleEngine
import services.battle.microservices.runtime.database.BattleRuntimeRuleBook
import services.battle.objects.{BattleArtifactStatus, BattleMode, BattlePhase}
import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.objects.core.{
  BattleAggregateState,
  BattleTick,
  BattleVector2,
  ClientCommandSeq,
  CooldownMillis,
  DurationMillis,
  ElapsedMillis,
  EpochMillis,
  FacingRadians,
  HeroId,
  SpawnPointIndex,
}
import services.battle.microservices.actors.objects.player.{
  BattleParticipantKind,
  BattlePlayerLifeState,
  BattlePlayerSkillState,
  BattlePlayerState,
  KillCount,
  Score
}
import services.battle.microservices.queue.objects.queue.{BattleSessionBootstrapSeat, BattleSessionDescriptor}
import services.identity.objects.DisplayName

private[battle] object BattleSessionStateFactory {
  /** 中文名：创建initial状态（createInitialState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def createInitialState(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis
  ): BattleAggregateState = {
    val mapId = BattleMode.mapId(seed.descriptor.battleMode)
    BattleEngine.withMap(mapId) {
      val startedAt = if seed.descriptor.startedAt.value > 0L then seed.descriptor.startedAt else now
      val players = bootstrapSeats(seed.descriptor).map(toPlayerState)
      val pickups = BattleEngine.initialPickups
      BattleAggregateState(
        battleId = seed.descriptor.battleId,
        roomId = seed.roomId,
        mapId = mapId,
        phase = BattlePhase.Active,
        serverTime = startedAt,
        startedAt = startedAt,
        durationMs = battleDuration,
        elapsedMs = ElapsedMillis(0L),
        endsAt = EpochMillis(startedAt.value + battleDuration.value),
        worldSize = BattleEngine.WorldSize,
        tick = BattleTick(0L),
        artifactStatus = BattleArtifactStatus.Pending,
        players = players,
        projectiles = Vector.empty,
        projectileTerminals = Vector.empty,
        slowFields = Vector.empty,
        pickups = pickups,
        replayFrames = Vector(BattleEngine.captureReplayFrame(ElapsedMillis(0L), players, Vector.empty, pickups)),
        events = Vector.empty,
        winnerPlayerId = None,
        winnerHeroId = None
      )
    }
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
    val playerRules = BattleRuntimeRuleBook.sessionPlayer
    val weapon = BattleEngine.createWeaponState(playerRules.defaultWeaponKind)

    BattlePlayerState(
      playerId = seat.playerId,
      heroId = seat.heroId,
      handle = seat.handle,
      displayName = seat.displayName,
      seat = seat.seat,
      participantKind = seat.participantKind,
      position = BattleEngine.spawnPointFor(seat.spawnPointIndex),
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = BattleEngine.ZeroVector,
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lastClientCommandSeq = ClientCommandSeq(0L),
      currentWeaponIndex = 0,
      weapons = Vector(weapon),
      currentWeaponKind = playerRules.defaultWeaponKind,
      hp = playerRules.initialHp,
      maxHp = playerRules.maxHp,
      stamina = playerRules.initialStamina,
      maxStamina = playerRules.maxStamina,
      score = Score(0),
      kills = KillCount(0),
      skills = Vector(
        BattlePlayerSkillState(SkillKind.Blink, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Dash, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Freeze, CooldownMillis(0), DurationMillis(0L))
      ),
      lifeState = BattlePlayerLifeState.Alive
    )
  }
}
