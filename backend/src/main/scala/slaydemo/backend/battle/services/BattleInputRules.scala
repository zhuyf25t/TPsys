package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.BattleCommandRequest
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleMotionRules.*
import slaydemo.backend.battle.services.BattleWeaponRules.*

private[services] object BattleInputRules {
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

  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): ClientCommandSeq =
    state.players.find(_.playerId == playerId).map(_.lastClientCommandSeq).getOrElse(ClientCommandSeq(0L))

  def normalizeAim(previous: BattleVector2, next: BattleVector2): BattleVector2 = {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then previous else BattleVector2(next.x / length, next.y / length)
  }

  private def maxClientCommandSeq(left: ClientCommandSeq, right: ClientCommandSeq): ClientCommandSeq =
    ClientCommandSeq(math.max(left.value, right.value))
}
