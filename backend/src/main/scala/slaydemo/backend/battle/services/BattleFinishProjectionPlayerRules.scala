package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.BattlePlayerState
import slaydemo.backend.shared.policies.HandlePolicy

private[services] object BattleFinishProjectionPlayerRules {
  def playersByPlacement(players: Vector[BattlePlayerState]): Vector[BattlePlayerState] =
    players.sortBy { player =>
      if player.alive then (0, -player.score.value.toLong, -player.hp.value, player.seat.value)
      else (1, -player.eliminatedAtMs.map(_.value).getOrElse(-1L), -player.score.value, player.seat.value)
    }

  def isPlayableHumanPlayer(player: BattlePlayerState): Boolean =
    !player.isBot && HandlePolicy.isPlayableIdentityHandle(safeHandle(player))

  def safeDisplayName(player: BattlePlayerState): String = {
    val displayName = player.displayName.value.trim
    if displayName.nonEmpty then displayName else player.handle.value.trim
  }

  private def safeHandle(player: BattlePlayerState): String = {
    val handle = player.handle.value.trim
    if handle.nonEmpty then handle else player.playerId.value
  }
}
