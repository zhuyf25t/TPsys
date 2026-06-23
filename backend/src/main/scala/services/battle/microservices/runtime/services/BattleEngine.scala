package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.actors.services.BattleInputRules
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.combat.objects.projectile.BattleProjectileState
import services.battle.microservices.combat.objects.weapon.{BattleWeaponState, WeaponKind}
import services.battle.microservices.combat.services.BattleWeaponRules
import services.battle.microservices.projections.objects.replay.BattleReplayFrameState
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.microservices.world.services.{BattleArenaCatalog, BattleInitialLayout}
import services.battle.microservices.abilities.services.BattleSkillCommandRules.CommandApplication
import services.battle.microservices.runtime.services.BattleReplayFrameRecorder
import services.battle.microservices.runtime.objects.command.BattleCommandRequest
import services.battle.objects.{
  BattleAggregateState,
  BattleMapId,
  BattleVector2,
  ClientCommandSeq,
  DurationMillis,
  ElapsedMillis,
  EpochMillis,
  PlayerId,
  SpawnPointIndex
}

object BattleEngine {
  def DefaultBattleDuration(battleRules: BattleDynamicRuleBook): IO[DurationMillis] =
    battleRules.runtime.map(_.defaultBattleDuration)

  def TickStep(battleRules: BattleDynamicRuleBook): IO[DurationMillis] =
    battleRules.runtime.map(_.tickStep)

  def worldSize(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[BattleVector2] =
    BattleArenaCatalog.contextFor(mapId, battleRules).map(_.worldSize)

  val ZeroVector: BattleVector2 =
    BattleArenaContext.ZeroVector

  def initialPickups(mapId: BattleMapId, battleRules: BattleDynamicRuleBook): IO[Vector[BattlePickupState]] =
    BattleInitialLayout.initialPickups(mapId, battleRules)

  def spawnPointFor(mapId: BattleMapId, index: SpawnPointIndex, battleRules: BattleDynamicRuleBook): IO[BattleVector2] =
    BattleInitialLayout.spawnPointFor(mapId, index, battleRules)

  def createWeaponState(weaponKind: WeaponKind, battleRules: BattleDynamicRuleBook): IO[BattleWeaponState] =
    BattleWeaponRules.createWeaponState(weaponKind, battleRules)

  def captureReplayFrame(
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): IO[BattleReplayFrameState] =
    BattleReplayFrameRecorder.captureFrame(elapsedMs, players, projectiles, pickups)

  def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    BattleRuntimeStepRules.advanceStateStep(state, requestedDeltaMs, now, battleRules)

  def finishedAtForRoom(state: BattleAggregateState, battleRules: BattleDynamicRuleBook): IO[EpochMillis] =
    BattleRuntimeFinishRules.finishedAtForRoom(state, battleRules)

  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): IO[ClientCommandSeq] =
    BattleInputRules.lastClientCommandSeq(state, playerId)

  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest,
    battleRules: BattleDynamicRuleBook
  ): IO[CommandApplication] =
    BattleCommandApplicationRules.applyCommand(state, player, request, battleRules)
}
