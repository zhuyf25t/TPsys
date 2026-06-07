package services.battle.microservices.session.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.runtime.services.BattleEngine
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.extraction.services.BattleExtractionInitialState
import services.battle.objects.{BattleArtifactStatus, BattleMode, BattlePhase}
import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.objects.core.{
  BattleAggregateState,
  BattleMapId,
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
  HitPoints,
  KillCount,
  Score
}
import services.battle.microservices.combat.objects.weapon.{BattleWeaponState, WeaponKind}
import services.battle.microservices.queue.objects.queue.{BattleSessionBootstrapSeat, BattleSessionDescriptor}
import services.identity.objects.DisplayName

private[battle] object BattleSessionStateFactory {
  private val WinterMapId = BattleMapId("winter-hunt-v1")
  private val BossZombieHeroIds =
    Set("bot-1", "bot-2", "bot-3").map(HeroId.apply)
  private val BossZombieHpMultiplier = 3
  private val CombatBotHeroPrefix = "combat-bot-"
  private val CombatBotWeaponRotation: Vector[WeaponKind] =
    Vector(WeaponKind.Pistol, WeaponKind.Gatling, WeaponKind.Shotgun, WeaponKind.RocketLauncher)

  /** 中文名：创建initial状态（createInitialState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def createInitialState(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    for
      mapId <- battleMapId(seed.descriptor)
      startedAt <- startedAtFor(seed.descriptor, now)
      seats <- bootstrapSeats(seed.descriptor)
      players <- seats.traverse(seat => toPlayerState(mapId, seat, battleRules))
      pickups <- BattleEngine.initialPickups(mapId, battleRules)
      worldSize <- BattleEngine.worldSize(mapId, battleRules)
      gasZone <- BattleExtractionInitialState.gasZone(mapId, battleRules)
      extraction <- BattleExtractionInitialState.extraction(mapId, battleRules)
      lootCaches <- BattleExtractionInitialState.lootCaches(mapId, battleRules)
      replayFrame <- BattleEngine.captureReplayFrame(ElapsedMillis(0L), players, Vector.empty, pickups)
    yield
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
        worldSize = worldSize,
        tick = BattleTick(0L),
        artifactStatus = BattleArtifactStatus.Pending,
        players = players,
        projectiles = Vector.empty,
        projectileTerminals = Vector.empty,
        slowFields = Vector.empty,
        pickups = pickups,
        gasZone = gasZone,
        extraction = extraction,
        lootCaches = lootCaches,
        replayFrames = Vector(replayFrame),
        events = Vector.empty,
        winnerPlayerId = None,
        winnerHeroId = None
      )
  }

  private def battleMapId(descriptor: BattleSessionDescriptor): IO[BattleMapId] =
    IO.pure(BattleMode.mapId(descriptor.battleMode))

  private def startedAtFor(descriptor: BattleSessionDescriptor, now: EpochMillis): IO[EpochMillis] =
    IO.pure(if descriptor.startedAt.value > 0L then descriptor.startedAt else now)

  private def bootstrapSeats(descriptor: BattleSessionDescriptor): IO[Vector[BattleSessionBootstrapSeat]] =
    IO.pure(descriptor.bootstrap.map(_.seats).getOrElse {
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
    }.sortBy(_.seat.value))

  private def toPlayerState(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat,
    battleRules: BattleDynamicRuleBook
  ): IO[BattlePlayerState] = {
    for
      playerRules <- battleRules.sessionPlayer
      weapons <- initialWeaponLoadout(mapId, seat, playerRules.defaultWeaponKind, battleRules)
      position <- BattleEngine.spawnPointFor(mapId, seat.spawnPointIndex, battleRules)
      maxHp = maxHpForSeat(mapId, seat, playerRules.maxHp)
      hp = if maxHp.value > playerRules.initialHp.value then maxHp else playerRules.initialHp
    yield BattlePlayerState(
      playerId = seat.playerId,
      heroId = seat.heroId,
      handle = seat.handle,
      displayName = seat.displayName,
      seat = seat.seat,
      participantKind = seat.participantKind,
      position = position,
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = BattleEngine.ZeroVector,
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lastClientCommandSeq = ClientCommandSeq(0L),
      currentWeaponIndex = 0,
      weapons = weapons,
      currentWeaponKind = weapons.headOption.map(_.weaponKind).getOrElse(playerRules.defaultWeaponKind),
      hp = hp,
      maxHp = maxHp,
      stamina = playerRules.initialStamina,
      maxStamina = playerRules.maxStamina,
      score = Score(0),
      kills = KillCount(0),
      skills = Vector(
        BattlePlayerSkillState(SkillKind.Blink, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Dash, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Freeze, CooldownMillis(0), DurationMillis(0L)),
        BattlePlayerSkillState(SkillKind.Critical, CooldownMillis(0), DurationMillis(0L))
      ),
      lifeState = BattlePlayerLifeState.Alive
    )
  }

  private def maxHpForSeat(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat,
    baseMaxHp: HitPoints
  ): HitPoints =
    if isWinterBossZombie(mapId, seat) then
      HitPoints(baseMaxHp.value * BossZombieHpMultiplier)
    else baseMaxHp

  private def initialWeaponLoadout(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat,
    defaultWeaponKind: WeaponKind,
    battleRules: BattleDynamicRuleBook
  ): IO[Vector[BattleWeaponState]] =
    if isPlainWinterZombie(mapId, seat) then IO.pure(Vector.empty)
    else {
      val initialWeaponKind =
        if isCombatBot(mapId, seat) then combatBotWeaponKind(seat)
        else defaultWeaponKind

      BattleEngine.createWeaponState(initialWeaponKind, battleRules).map(Vector(_))
    }

  private def isWinterBossZombie(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat
  ): Boolean =
    mapId == WinterMapId &&
      seat.participantKind == BattleParticipantKind.Bot &&
      BossZombieHeroIds.contains(seat.heroId)

  private def isPlainWinterZombie(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat
  ): Boolean =
    mapId == WinterMapId &&
      seat.participantKind == BattleParticipantKind.Bot &&
      !BossZombieHeroIds.contains(seat.heroId)

  private def isCombatBot(
    mapId: BattleMapId,
    seat: BattleSessionBootstrapSeat
  ): Boolean =
    mapId != WinterMapId &&
      seat.participantKind == BattleParticipantKind.Bot &&
      seat.heroId.value.startsWith(CombatBotHeroPrefix)

  private def combatBotWeaponKind(seat: BattleSessionBootstrapSeat): WeaponKind =
    CombatBotWeaponRotation(math.floorMod(seat.seat.value - 1, CombatBotWeaponRotation.length))
}
