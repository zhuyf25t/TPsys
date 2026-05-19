package slaydemo.backend.battle.services.results

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.BattlePlayerState
import slaydemo.backend.shared.policies.HandlePolicy

private[services] object BattleFinishProjectionPlayerRules {
  /** 中文名：playersbyplacement（playersByPlacement）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def playersByPlacement(players: Vector[BattlePlayerState]): Vector[BattlePlayerState] =
    players.sortBy { player =>
      if player.alive then (0, -player.score.value.toLong, -player.hp.value, player.seat.value)
      else (1, -player.eliminatedAtMs.map(_.value).getOrElse(-1L), -player.score.value, player.seat.value)
    }

  /** 中文名：判断是否playablehuman玩家（isPlayableHumanPlayer）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def isPlayableHumanPlayer(player: BattlePlayerState): Boolean =
    !player.isBot && HandlePolicy.isPlayableIdentityHandle(safeHandle(player))

  /** 中文名：safe展示name（safeDisplayName）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def safeDisplayName(player: BattlePlayerState): String = {
    val displayName = player.displayName.value.trim
    if displayName.nonEmpty then displayName else player.handle.value.trim
  }

  private def safeHandle(player: BattlePlayerState): String = {
    val handle = player.handle.value.trim
    if handle.nonEmpty then handle else player.playerId.value
  }
}
