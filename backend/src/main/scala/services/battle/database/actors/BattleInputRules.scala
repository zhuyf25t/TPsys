package services.battle.database.actors

import services.battle.database.world.BattleMotionRules.*
import services.battle.database.combat.BattleWeaponRules.*
import services.battle.objects.command.BattleCommandRequest
import services.battle.objects.core.{BattleAggregateState, BattleVector2, ClientCommandSeq, FacingRadians, PlayerId}
import services.battle.objects.player.BattlePlayerState

private[services] object BattleInputRules {
  /** 中文名：应用命令转为玩家（applyCommandToPlayer）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动�?*/
  def applyCommandToPlayer(player: BattlePlayerState, request: BattleCommandRequest): BattlePlayerState = {
    val aim = normalizeAim(player.aim, BattleVector2(request.aim.x, request.aim.y))
    val movement = normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
    val suppressPrimaryHeld = request.skillIntents.nonEmpty
    val inputPlayer = player.copy(
      aim = aim,
      facing = FacingRadians(math.atan2(aim.y, aim.x)),
      movement = movement,
      sprint = request.sprint,
      primaryHeld = request.primaryHeld && !suppressPrimaryHeld,
      reloadPressed = request.reloadPressed,
      lastClientCommandSeq = maxClientCommandSeq(player.lastClientCommandSeq, request.clientCommandSeq)
    )
    applyWeaponSwitchRequest(inputPlayer, request.switchWeaponDirection, request.switchWeaponIndex)
  }

  /** 中文名：last客户端命令seq（lastClientCommandSeq）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动�?*/
  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): ClientCommandSeq =
    state.players.find(_.playerId == playerId).map(_.lastClientCommandSeq).getOrElse(ClientCommandSeq(0L))

  /** 中文名：规范化瞄准（normalizeAim）。游戏职责：在后端角色域中管理玩家、bot、输入和生命周期，决定战场实体如何行动�?*/
  def normalizeAim(previous: BattleVector2, next: BattleVector2): BattleVector2 = {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then previous else BattleVector2(next.x / length, next.y / length)
  }

  private def maxClientCommandSeq(left: ClientCommandSeq, right: ClientCommandSeq): ClientCommandSeq =
    ClientCommandSeq(math.max(left.value, right.value))
}
