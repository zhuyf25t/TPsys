package services.battle.microservices.runtime.api

import cats.effect.IO
import io.circe.{Decoder, Error}

import java.sql.Connection

import services.battle.microservices.abilities.objects.skill.BattleCommandSkillIntents
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}
import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.runtime.objects.command.{
  BattleCommandAccepted,
  BattleCommandInputState,
  BattleCommandRequest,
  BattleCommandVector
}
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, PlayerId}
import system.api.{APIMessageError, APIMessageWithContext}

final case class BattleCommandCompatibilityAPIMessage(
  battleId: BattleId,
  playerId: PlayerId,
  ticketId: TicketId,
  clientTick: BattleTick,
  clientCommandSeq: ClientCommandSeq,
  movement: BattleCommandVector,
  aim: BattleCommandVector,
  inputState: BattleCommandInputState,
  skillIntents: BattleCommandSkillIntents,
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: BattleWeaponSwitchDirection,
  switchWeaponIndex: Option[BattleWeaponSwitchIndex]
) extends APIMessageWithContext[BattleStateService, BattleCommandAccepted] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleCommandAccepted] =
    BattleCommandAPIPlanner.submitCommand(stateService, toCommandRequest)

  def toCommandRequest: BattleCommandRequest =
    BattleCommandRequest(
      battleId = battleId,
      playerId = playerId,
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq,
      movement = movement,
      aim = aim,
      inputState = inputState,
      skillIntents = skillIntents,
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex
    )
}

object BattleCommandCompatibilityAPIMessage {
  import BattleCommandAPIMessage.given

  given Decoder[BattleCommandCompatibilityAPIMessage] =
    Decoder[BattleCommandRequest].map(fromCommandRequest)

  def fromCommandRequest(command: BattleCommandRequest): BattleCommandCompatibilityAPIMessage =
    BattleCommandCompatibilityAPIMessage(
      battleId = command.battleId,
      playerId = command.playerId,
      ticketId = command.ticketId,
      clientTick = command.clientTick,
      clientCommandSeq = command.clientCommandSeq,
      movement = command.movement,
      aim = command.aim,
      inputState = command.inputState,
      skillIntents = command.skillIntents,
      pointerWorld = command.pointerWorld,
      switchWeaponDirection = command.switchWeaponDirection,
      switchWeaponIndex = command.switchWeaponIndex
    )

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleRuntimeAPIMessageErrors.commandCompatibilityDecodeFailure(
      BattleCommandAPIMessage.commandRequestDecodeError(error)
    )
}
