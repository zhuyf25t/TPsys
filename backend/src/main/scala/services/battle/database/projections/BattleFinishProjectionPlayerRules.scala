package services.battle.database.projections

import services.battle.objects.BattlePlayerState
import system.policies.HandlePolicy

private[services] object BattleFinishProjectionPlayerRules {
  /** 中文名：按排名排序玩家（playersByPlacement）。游戏职责：把结束时的玩家状态排序成结算名次。 */
  def playersByPlacement(players: Vector[BattlePlayerState]): Vector[BattlePlayerState] =
    players.sortBy { player =>
      if player.alive then (0, -player.score.value.toLong, -player.hp.value, player.seat.value)
      else (1, -player.eliminatedAtMs.map(_.value).getOrElse(-1L), -player.score.value, player.seat.value)
    }

  /** 中文名：是否可结算真人玩家（isPlayableHumanPlayer）。游戏职责：过滤 bot 和游客身份，只给真实玩家生成战报结算。 */
  def isPlayableHumanPlayer(player: BattlePlayerState): Boolean =
    !player.isBot && HandlePolicy.isPlayableIdentityHandle(safeHandle(player))

  /** 中文名：安全展示名（safeDisplayName）。游戏职责：优先使用展示名，缺失时回退到玩家 handle。 */
  def safeDisplayName(player: BattlePlayerState): String = {
    val displayName = player.displayName.value.trim
    if displayName.nonEmpty then displayName else player.handle.value.trim
  }

  private def safeHandle(player: BattlePlayerState): String = {
    val handle = player.handle.value.trim
    if handle.nonEmpty then handle else player.playerId.value
  }
}
