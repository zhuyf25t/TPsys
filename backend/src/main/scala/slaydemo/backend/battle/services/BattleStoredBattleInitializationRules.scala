package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleStoredBattleInitializationRules {
  def fromSeed(
    seed: BattleSessionSeed,
    battleDuration: DurationMillis,
    now: EpochMillis
  ): StoredBattle = {
    val initialState = BattleSessionStateFactory.createInitialState(seed, battleDuration, now)
    StoredBattle(
      state = initialState,
      commandOwnershipByPlayerId = seed.commandOwnership.map(entry => entry.playerId -> entry.ticketId).toMap,
      finishProjectionStatus = BattleFinishProjectionStatus.Pending,
      lastUpdatedAt = initialState.serverTime,
      pendingStepMs = 0L
    )
  }
}
