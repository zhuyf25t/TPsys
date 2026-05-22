package services.battle.engine

import services.battle.objects.*
import services.battle.services.abilities.BattleSkillCommandRules.CommandApplication
import services.battle.services.runtime.BattleRuntimeCatalog
import services.battle.services.session.{
  BattleCommandApplicationRules,
  BattleRoomFinishedNotification,
  BattleSessionSeed,
  BattleStoredBattleAdvanceResult,
  BattleStoredBattleAdvanceRules,
  BattleStoredBattleInitializationRules,
  StoredBattle
}

object BattleEngine {
  val DefaultBattleDuration: DurationMillis =
    BattleRuntimeCatalog.DefaultBattleDuration

  def initialize(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis
  ): StoredBattle =
    BattleStoredBattleInitializationRules.fromSeed(seed, battleDuration, now)

  def advance(
    storedBattle: StoredBattle,
    now: EpochMillis
  ): BattleStoredBattleAdvanceResult =
    BattleStoredBattleAdvanceRules.advance(storedBattle, now)

  def applyCommand(
    state: BattleAggregateState,
    player: BattlePlayerState,
    request: BattleCommandRequest
  ): CommandApplication =
    BattleCommandApplicationRules.applyCommand(state, player, request)
}
