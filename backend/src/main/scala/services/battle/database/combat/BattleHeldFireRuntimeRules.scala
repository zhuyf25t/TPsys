package services.battle.database.combat

import services.battle.database.combat.BattleWeaponFireRules.*
import services.battle.objects.core.BattleAggregateState

private[services] object BattleHeldFireRuntimeRules {
  /** 中文名：解析heldprimary开火（resolveHeldPrimaryFire）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def resolveHeldPrimaryFire(state: BattleAggregateState): BattleAggregateState =
    state.players.foldLeft(state) { (currentState, snapshotPlayer) =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.primaryHeld =>
          applyPrimaryFire(currentState, player, runtimeFireCommandSeq(currentState, player))
        case _ => currentState
      }
    }
}
