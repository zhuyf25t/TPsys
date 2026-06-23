package services.battle.microservices.runtime.api

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import services.battle.microservices.abilities.api.BattleSkillIntentAPIEncoding
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}
import services.battle.microservices.actors.api.BattlePlayerInputAPIEncoding
import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.runtime.objects.command.{
  BattleCommandInputState,
  BattleCommandRequest,
  BattleCommandVector
}
import services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, PlayerId}
import system.objects.UserId

private[api] object BattleCommandAPIMessageDecoding {
  private final case class BattleCommandWire(
    userId: Option[UserId],
    battleId: Option[BattleId],
    playerId: Option[PlayerId],
    ticketId: Option[TicketId],
    clientTick: Option[BattleTick],
    clientCommandSeq: Option[ClientCommandSeq],
    movement: Option[BattleCommandVector],
    aim: Option[BattleCommandVector],
    primaryHeld: Option[Boolean],
    sprint: Option[Boolean],
    reloadPressed: Option[Boolean],
    castDash: Option[Boolean],
    castBlink: Option[Boolean],
    castFreeze: Option[Boolean],
    castCritical: Option[Boolean],
    pointerWorld: Option[BattleCommandVector],
    switchWeaponDirection: Option[BattleWeaponSwitchDirection],
    switchWeaponIndex: Option[BattleWeaponSwitchIndex]
  ) {
    def toAPIMessage: Either[String, BattleCommandAPIMessage] =
      for
        userId <- userId.toRight("Login is required.")
        request <- toCommandRequest
      yield BattleCommandAPIMessage.fromCommandRequest(userId, request)

    def toCommandRequest: Either[String, BattleCommandRequest] =
      for
        battleId <- required(battleId, BattleCommandRequestField.BattleId)
        playerId <- required(playerId, BattleCommandRequestField.PlayerId)
        ticketId <- ticketId.toRight(BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.MissingTicket))
        clientTick <- required(clientTick, BattleCommandRequestField.ClientTick)
        movement <- required(movement, BattleCommandRequestField.Movement)
        aim <- required(aim, BattleCommandRequestField.Aim)
        primaryHeld <- required(primaryHeld, BattleCommandRequestField.PrimaryHeld)
        reloadPressed <- required(reloadPressed, BattleCommandRequestField.ReloadPressed)
        switchWeaponDirection <- required(switchWeaponDirection, BattleCommandRequestField.SwitchWeaponDirection)
      yield BattleCommandRequest(
        battleId = battleId,
        playerId = playerId,
        ticketId = ticketId,
        clientTick = clientTick,
        clientCommandSeq = clientCommandSeq.getOrElse(ClientCommandSeq(clientTick.value)),
        movement = movement,
        aim = aim,
        inputState = BattleCommandInputState.fromWire(
          primaryHeld = primaryHeld,
          sprint = sprint.getOrElse(false),
          reloadPressed = reloadPressed
        ),
        skillIntents = BattleSkillIntentAPIEncoding.fromLegacyFlags(
          castDash = castDash,
          castBlink = castBlink,
          castFreeze = castFreeze,
          castCritical = castCritical
        ),
        pointerWorld = pointerWorld,
        switchWeaponDirection = switchWeaponDirection,
        switchWeaponIndex = switchWeaponIndex
      )
  }

  given userIdOptionDecoder: Decoder[Option[UserId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(UserId.apply))

  given battleIdOptionDecoder: Decoder[Option[BattleId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(BattleId.apply))

  given playerIdOptionDecoder: Decoder[Option[PlayerId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(PlayerId.apply))

  given ticketIdOptionDecoder: Decoder[Option[TicketId]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(nonEmpty).map(TicketId.apply))

  given battleTickOptionDecoder: Decoder[Option[BattleTick]] =
    Decoder.decodeOption(Decoder.decodeLong).map(_.map(BattleTick.apply))

  given clientCommandSeqOptionDecoder: Decoder[Option[ClientCommandSeq]] =
    Decoder.decodeOption(Decoder.decodeLong).map(_.map(ClientCommandSeq.apply))

  import BattlePlayerInputAPIEncoding.given

  given weaponSwitchDirectionOptionDecoder: Decoder[Option[BattleWeaponSwitchDirection]] =
    Decoder.decodeOption(Decoder.decodeInt).map(_.map(BattleWeaponSwitchDirection.fromWire))

  given weaponSwitchIndexOptionDecoder: Decoder[Option[BattleWeaponSwitchIndex]] =
    Decoder.decodeOption(Decoder.decodeInt).map(_.flatMap(BattleWeaponSwitchIndex.fromWire))

  private given battleCommandWireDecoder: Decoder[BattleCommandWire] =
    deriveDecoder[BattleCommandWire]

  given apiMessageDecoder: Decoder[BattleCommandAPIMessage] =
    Decoder[BattleCommandWire].emap(_.toAPIMessage)

  given commandRequestDecoder: Decoder[BattleCommandRequest] =
    Decoder[BattleCommandWire].emap(_.toCommandRequest)

  def fromCommandRequest(userId: UserId, command: BattleCommandRequest): BattleCommandAPIMessage =
    BattleCommandAPIMessage(
      userId = userId,
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

  def toCommandRequest(message: BattleCommandAPIMessage): BattleCommandRequest =
    BattleCommandRequest(
      battleId = message.battleId,
      playerId = message.playerId,
      ticketId = message.ticketId,
      clientTick = message.clientTick,
      clientCommandSeq = message.clientCommandSeq,
      movement = message.movement,
      aim = message.aim,
      inputState = message.inputState,
      skillIntents = message.skillIntents,
      pointerWorld = message.pointerWorld,
      switchWeaponDirection = message.switchWeaponDirection,
      switchWeaponIndex = message.switchWeaponIndex
    )

  private def required[A](value: Option[A], field: BattleCommandRequestField): Either[String, A] =
    value.toRight(BattleCommandRequestDecodeError.message(BattleCommandRequestDecodeError.InvalidField(field)))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
