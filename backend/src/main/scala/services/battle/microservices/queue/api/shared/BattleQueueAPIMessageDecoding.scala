package services.battle.microservices.queue.api.shared

import io.circe.Decoder

import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleSkinKey, Rating}
import services.battle.microservices.queue.objects.queue.{
  BattleRoomChatText,
  QueueRequestId,
  TicketId
}
import services.battle.objects.BattleMode
import services.battle.objects.core.RoomId
import services.identity.objects.{PlayerHandle, SessionToken}
import system.objects.UserId

private[api] object BattleQueueAPIMessageDecoding {
  given userIdDecoder: Decoder[UserId] =
    Decoder.decodeString.emap(value => nonEmptyText(value).map(UserId.apply).toRight("Login is required."))

  given playerHandleOptionDecoder: Decoder[Option[PlayerHandle]] =
    optionalTextDecoder.map(_.flatMap(PlayerHandle.forLookup))

  given sessionTokenOptionDecoder: Decoder[Option[SessionToken]] =
    optionalTextDecoder.map(_.flatMap(SessionToken.fromString))

  given battleModeOptionDecoder: Decoder[Option[BattleMode]] =
    optionalTextDecoder.emap(decodeBattleMode)

  given ratingOptionDecoder: Decoder[Option[Rating]] =
    optionalRatingDecoder

  given ticketIdOptionDecoder: Decoder[Option[TicketId]] =
    optionalTextDecoder.map(_.map(TicketId.apply))

  given roomIdOptionDecoder: Decoder[Option[RoomId]] =
    optionalTextDecoder.map(_.map(RoomId.apply))

  given queueRequestIdOptionDecoder: Decoder[Option[QueueRequestId]] =
    optionalTextDecoder.map(_.map(QueueRequestId.apply))

  given avatarOptionDecoder: Decoder[Option[BattleAvatarKey]] =
    optionalTextDecoder.map(_.flatMap(BattleAvatarKey.fromWire))

  given skinOptionDecoder: Decoder[Option[BattleSkinKey]] =
    optionalTextDecoder.map(_.flatMap(BattleSkinKey.fromWire))

  given chatTextOptionDecoder: Decoder[Option[BattleRoomChatText]] =
    optionalTextDecoder.map(_.flatMap(BattleRoomChatText.fromWire))

  given booleanOptionDecoder: Decoder[Option[Boolean]] =
    Decoder.decodeOption(Decoder.decodeBoolean).or(Decoder.const(None))

  private val optionalTextDecoder: Decoder[Option[String]] =
    Decoder.decodeOption(Decoder.decodeString).or(Decoder.const(None)).map(_.flatMap(nonEmptyText))

  private val optionalRatingDecoder: Decoder[Option[Rating]] =
    Decoder.decodeOption(Decoder.decodeInt).map(_.map(Rating.apply))
      .or(Decoder.decodeOption(Decoder.decodeString).emap(decodeRatingText))
      .or(Decoder.const(None))

  private def decodeBattleMode(value: Option[String]): Either[String, Option[BattleMode]] =
    value match
      case None =>
        Right(None)
      case Some(modeId) =>
        BattleMode.fromWire(modeId)
          .map(Some.apply)
          .toRight(BattleQueueRequestDecodeError.message(BattleQueueRequestDecodeError.InvalidBattleMode))

  private def decodeRatingText(value: Option[String]): Either[String, Option[Rating]] =
    value.map(_.trim) match
      case None | Some("") =>
        Right(None)
      case Some(trimmed) =>
        trimmed.toIntOption
          .map(parsed => Right(Some(Rating(parsed))))
          .getOrElse(Left(BattleQueueRequestDecodeError.message(BattleQueueRequestDecodeError.InvalidRating)))

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
