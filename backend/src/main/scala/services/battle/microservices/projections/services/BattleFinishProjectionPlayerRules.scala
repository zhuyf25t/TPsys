package services.battle.microservices.projections.services

import cats.effect.IO

import services.battle.microservices.actors.objects.player.BattlePlayerState
import system.policies.HandlePolicy

private[battle] object BattleFinishProjectionPlayerRules {
  def playersByPlacement(players: Vector[BattlePlayerState]): IO[Vector[BattlePlayerState]] =
    IO.pure(
      players.sortBy { player =>
        if player.alive then (0, -player.score.value.toLong, -player.hp.value, player.seat.value)
        else (1, -player.eliminatedAtMs.map(_.value).getOrElse(-1L), -player.score.value, player.seat.value)
      }
    )

  def isPlayableHumanPlayer(player: BattlePlayerState): IO[Boolean] =
    safeHandle(player).map(handle => !player.isBot && HandlePolicy.isPlayableIdentityHandle(handle))

  def safeDisplayName(player: BattlePlayerState): IO[String] =
    IO.pure {
      val displayName = player.displayName.value.trim
      if displayName.nonEmpty then displayName else player.handle.value.trim
    }

  private def safeHandle(player: BattlePlayerState): IO[String] =
    IO.pure {
      val handle = player.handle.value.trim
      if handle.nonEmpty then handle else player.playerId.value
    }
}
