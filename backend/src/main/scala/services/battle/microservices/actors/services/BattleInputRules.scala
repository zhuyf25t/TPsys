package services.battle.microservices.actors.services

import services.battle.microservices.session.objects.command.BattleCommandRequest
import services.battle.objects.core.{BattleAggregateState, BattleVector2, ClientCommandSeq, FacingRadians, PlayerId}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

private[battle] object BattleInputRules {
  final case class BattleInputEnvironment(
    normalizeMovement: BattleVector2 => BattleVector2,
    applyWeaponSwitchRequest: (BattlePlayerState, BattleWeaponSwitchDirection, Option[BattleWeaponSwitchIndex]) => BattlePlayerState
  )

  def applyCommandToPlayer(
    player: BattlePlayerState,
    request: BattleCommandRequest,
    environment: BattleInputEnvironment
  ): BattlePlayerState = {
    val aim = normalizeAim(player.aim, BattleVector2(request.aim.x, request.aim.y))
    val movement = environment.normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
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
    environment.applyWeaponSwitchRequest(inputPlayer, request.switchWeaponDirection, request.switchWeaponIndex)
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