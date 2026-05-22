package services.battle.engine

import services.battle.objects.*
import BattleSkillCommandRules.CommandApplication

object BattleEngine {
  val DefaultBattleDuration: DurationMillis =
    BattleRuntimeCatalog.DefaultBattleDuration

  val TickStep: DurationMillis =
    BattleRuntimeCatalog.TickStep

  val WorldSize: BattleVector2 =
    BattleArenaCatalog.WorldSize

  val ZeroVector: BattleVector2 =
    BattleArenaCatalog.ZeroVector

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
    BattleRuntimeStepRules.advanceStateStep(state, requestedDeltaMs, now)

  def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    BattleRuntimeFinishRules.finishedAtForRoom(state)

  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): ClientCommandSeq =
    BattleInputRules.lastClientCommandSeq(state, playerId)

  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication =
    BattleCommandApplicationRules.applyCommand(state, player, request)
}
