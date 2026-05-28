package services.battle.microservices.runtime.services

import services.battle.objects.actors.BattleInputRules
import services.battle.microservices.combat.services.BattleWeaponRules
import services.battle.database.runtime.BattleRuntimeRuleBook
import services.battle.microservices.world.services.{BattleArenaCatalog, BattleInitialLayout}
import services.battle.objects.abilities.BattleSkillCommandRules.CommandApplication
import services.battle.objects.runtime.BattleReplayFrameRecorder
import services.battle.objects.{
  BattleAggregateState,
  BattleCommandRequest,
  BattleMapId,
  BattlePickupState,
  BattlePlayerState,
  BattleProjectileState,
  BattleReplayFrameState,
  BattleVector2,
  BattleWeaponState,
  ClientCommandSeq,
  DurationMillis,
  ElapsedMillis,
  EpochMillis,
  PlayerId,
  SpawnPointIndex,
  WeaponKind
}

object BattleEngine {
  val DefaultBattleDuration: DurationMillis =
    BattleRuntimeRuleBook.runtime.defaultBattleDuration

  val TickStep: DurationMillis =
    BattleRuntimeRuleBook.runtime.tickStep

  def WorldSize: BattleVector2 =
    BattleArenaCatalog.WorldSize

  val ZeroVector: BattleVector2 =
    BattleArenaCatalog.ZeroVector

  def withMap[A](mapId: BattleMapId)(work: => A): A =
    BattleArenaCatalog.withMap(mapId)(work)

  def initialPickups: Vector[BattlePickupState] =
    BattleInitialLayout.initialPickups

  def spawnPointFor(index: SpawnPointIndex): BattleVector2 =
    BattleInitialLayout.spawnPointFor(index)

  def createWeaponState(weaponKind: WeaponKind): BattleWeaponState =
    BattleWeaponRules.createWeaponState(weaponKind)

  def captureReplayFrame(
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): BattleReplayFrameState =
    BattleReplayFrameRecorder.captureFrame(elapsedMs, players, projectiles, pickups)

  def advanceStateStep(
    state: BattleAggregateState,
    requestedDeltaMs: Long,
    now: EpochMillis
  ): BattleAggregateState =
    withMap(state.mapId) {
      BattleRuntimeStepRules.advanceStateStep(state, requestedDeltaMs, now)
    }

  def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    BattleRuntimeFinishRules.finishedAtForRoom(state)

  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): ClientCommandSeq =
    BattleInputRules.lastClientCommandSeq(state, playerId)

  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication =
    withMap(state.mapId) {
      BattleCommandApplicationRules.applyCommand(state, player, request)
    }
}
