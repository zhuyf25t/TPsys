package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandSkillOutcome}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleInputRules.lastClientCommandSeq

private[services] object BattleCommandAcceptanceFactory {
  def ignored(
    state: BattleAggregateState,
    player: BattlePlayerState,
    serverTime: EpochMillis
  ): BattleCommandAccepted =
    BattleCommandAccepted(
      battleId = state.battleId,
      acceptedTick = state.tick,
      acceptedCommandSeq = player.lastClientCommandSeq,
      serverTime = serverTime,
      commandStatus = BattleCommandStatus.Ignored,
      commandReason = Some(ignoredReason(state, player)),
      outcomes = Vector.empty
    )

  def applied(
    state: BattleAggregateState,
    playerId: PlayerId,
    serverTime: EpochMillis,
    outcomes: Vector[BattleCommandSkillOutcome]
  ): BattleCommandAccepted =
    BattleCommandAccepted(
      battleId = state.battleId,
      acceptedTick = state.tick,
      acceptedCommandSeq = lastClientCommandSeq(state, playerId),
      serverTime = serverTime,
      commandStatus = BattleCommandStatus.Applied,
      commandReason = None,
      outcomes = outcomes
    )

  private def ignoredReason(state: BattleAggregateState, player: BattlePlayerState): BattleCommandReason =
    if !player.alive then BattleCommandReason.PlayerDead
    else if state.phase == BattlePhase.Finished then BattleCommandReason.BattleFinished
    else BattleCommandReason.BattleInactive
}
