package services.battle.microservices.combat.services

import cats.effect.IO

import services.battle.microservices.combat.services.BattleWeaponFireRules.*
import services.battle.microservices.runtime.services.BattleDynamicRuleBook
import services.battle.microservices.world.objects.world.BattleArenaContext
import services.battle.objects.core.BattleAggregateState

private[battle] object BattleHeldFireRuntimeRules {
  /** 中文名：解析heldprimary开火（resolveHeldPrimaryFire）。游戏职责：在后端战斗域中管理武器、投射物、命中、伤害和终止效果，支撑实时交火�?*/
  def resolveHeldPrimaryFire(
    state: BattleAggregateState,
    arena: BattleArenaContext,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    state.players.foldLeft(IO.pure(state)) { (currentStateIO, snapshotPlayer) =>
      currentStateIO.flatMap { currentState =>
      currentState.players.find(_.playerId == snapshotPlayer.playerId) match {
        case Some(player) if player.alive && player.primaryHeld =>
          runtimeFireCommandSeq(currentState, player).flatMap(commandSeq =>
            applyPrimaryFire(currentState, player, commandSeq, arena, battleRules)
          )
        case _ => IO.pure(currentState)
      }
      }
    }
}
