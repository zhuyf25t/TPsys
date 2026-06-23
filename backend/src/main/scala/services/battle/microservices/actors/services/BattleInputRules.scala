package services.battle.microservices.actors.services

import cats.effect.IO

import services.battle.microservices.runtime.objects.command.BattleCommandRequest
import services.battle.objects.core.{BattleAggregateState, BattleVector2, ClientCommandSeq, FacingRadians, PlayerId}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

private[battle] object BattleInputRules {
  final case class BattleInputEnvironment(
    normalizeMovement: BattleVector2 => IO[BattleVector2],
    applyWeaponSwitchRequest: (BattlePlayerState, BattleWeaponSwitchDirection, Option[BattleWeaponSwitchIndex]) => IO[BattlePlayerState]
  )

  def applyCommandToPlayer(
    player: BattlePlayerState,
    request: BattleCommandRequest,
    environment: BattleInputEnvironment
  ): IO[BattlePlayerState] =
    for
      aim <- normalizeAim(player.aim, BattleVector2(request.aim.x, request.aim.y))
      movement <- environment.normalizeMovement(BattleVector2(request.movement.x, request.movement.y))
      commandSeq <- maxClientCommandSeq(player.lastClientCommandSeq, request.clientCommandSeq)
      suppressPrimaryHeld = request.skillIntents.nonEmpty
      inputPlayer = player.copy(
        aim = aim,
        facing = FacingRadians(math.atan2(aim.y, aim.x)),
        movement = movement,
        sprint = request.sprint,
        primaryHeld = request.primaryHeld && !suppressPrimaryHeld,
        reloadPressed = request.reloadPressed,
        lastClientCommandSeq = commandSeq
      )
      switched <- environment.applyWeaponSwitchRequest(inputPlayer, request.switchWeaponDirection, request.switchWeaponIndex)
    yield switched

  def lastClientCommandSeq(state: BattleAggregateState, playerId: PlayerId): IO[ClientCommandSeq] =
    IO.pure(state.players.find(_.playerId == playerId).map(_.lastClientCommandSeq).getOrElse(ClientCommandSeq(0L)))

  def normalizeAim(previous: BattleVector2, next: BattleVector2): IO[BattleVector2] = IO.pure {
    val length = math.hypot(next.x, next.y)
    if length <= 0.0001 then previous else BattleVector2(next.x / length, next.y / length)
  }

  private def maxClientCommandSeq(left: ClientCommandSeq, right: ClientCommandSeq): IO[ClientCommandSeq] =
    IO.pure(ClientCommandSeq(math.max(left.value, right.value)))
}
