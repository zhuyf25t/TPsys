package services.battle.services.actors

import services.battle.services.*

import services.battle.objects.*

private[services] object BattlePlayerLifecycleRules {
  /** 中文名：clear已结束玩家runtime（clearFinishedPlayerRuntime）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动。 */
  def clearFinishedPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    clearDeadPlayerRuntime(player)

  /** 中文名：cleardead玩家runtime（clearDeadPlayerRuntime）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动。 */
  def clearDeadPlayerRuntime(player: BattlePlayerState): BattlePlayerState =
    player.copy(
      movement = BattleArenaCatalog.ZeroVector,
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

  /** 中文名：winnerfor（winnerFor）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动。 */
  def winnerFor(players: Vector[BattlePlayerState]): Option[BattlePlayerState] =
    players.filter(player => player.alive && player.hp.value > 0) match {
      case Vector(winner) => Some(winner)
      case _              => None
    }
}
