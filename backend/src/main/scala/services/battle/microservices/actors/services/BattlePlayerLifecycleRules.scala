package services.battle.microservices.actors.services

import cats.effect.IO

import services.battle.objects.core.{BattleVector2, CooldownMillis, DurationMillis}
import services.battle.microservices.actors.objects.player.{BattlePlayerLifeState, BattlePlayerState}

private[battle] object BattlePlayerLifecycleRules {
  def clearFinishedPlayerRuntime(player: BattlePlayerState): IO[BattlePlayerState] =
    clearDeadPlayerRuntime(player)

  def clearDeadPlayerRuntime(player: BattlePlayerState): IO[BattlePlayerState] =
    clearedDeadPlayerRuntime(player)

  private def clearedDeadPlayerRuntime(player: BattlePlayerState): IO[BattlePlayerState] =
    IO.pure(player.copy(
      movement = BattleVector2(0.0, 0.0),
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lifeState = BattlePlayerLifeState.withRespawnMs(player.lifeState, DurationMillis(0L)),
      skills = player.skills.map(skill => skill.copy(activeMs = DurationMillis(0L))),
      weapons = player.weapons.map(weapon =>
        weapon.copy(
          fireCooldownMs = CooldownMillis(0),
          reloadRemainingMs = CooldownMillis(0)
        )
      )
    ))

  def winnerFor(players: Vector[BattlePlayerState]): IO[Option[BattlePlayerState]] =
    IO.pure {
      players.filter(player => player.alive && player.hp.value > 0) match {
        case Vector(winner) => Some(winner)
        case _              => None
      }
    }
}
