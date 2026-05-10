package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.battle.services.BattleWeaponFireRules.*

private[services] object BattleHeldFireRuntimeRules {
  def resolveHeldPrimaryFire(state: BattleAggregateState): BattleAggregateState =
    state.players.foldLeft(state) { (currentState, snapshotPlayer) =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.primaryHeld =>
          applyPrimaryFire(currentState, player, runtimeFireCommandSeq(currentState, player))
        case _ => currentState
      }
    }
}
