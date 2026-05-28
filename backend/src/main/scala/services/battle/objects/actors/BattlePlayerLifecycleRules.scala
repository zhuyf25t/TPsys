package services.battle.objects.actors

import services.battle.objects.core.{BattleVector2, CooldownMillis, DurationMillis}
import services.battle.objects.player.{BattlePlayerLifeState, BattlePlayerState}

private[battle] object BattlePlayerLifecycleRules {
  def clearFinishedPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    clearDeadPlayerRuntime(player)

  def clearDeadPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    player.copy(
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
    )

  def winnerFor(players: Vector[BattlePlayerState]): Option[BattlePlayerState] =
    players.filter(player => player.alive && player.hp.value > 0) match {
      case Vector(winner) => Some(winner)
      case _              => None
    }
}