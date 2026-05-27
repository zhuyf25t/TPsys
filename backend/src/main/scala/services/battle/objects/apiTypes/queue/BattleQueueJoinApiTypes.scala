package services.battle.objects.apiTypes.queue

import io.circe.{Decoder, DecodingFailure, HCursor}
import services.battle.objects.{BattleAPIRequestError, BattleMode, BattleQueueJoinCommand}
import services.battle.objects.core.{BattleAvatarKey, BattleSkinKey, QueueRequestId, Rating}
import services.identity.objects.{PlayerHandle, SessionToken}

object BattleQueueJoinRequest {
  given Decoder[BattleQueueJoinCommand] =
    Decoder.instance { cursor =>
      for
        handle <- decodeHandle(cursor)
        sessionToken <- decodeSessionToken(cursor)
        rating <- decodeOptionalRating(cursor, "rating")
        modeId <- decodeOptionalBattleMode(cursor, "modeId")
      yield BattleQueueJoinCommand(
        handle = handle,
        sessionToken = sessionToken,
        battleMode = modeId.getOrElse(BattleMode.default),
        queueRequestId = optionalText(cursor, "queueRequestId").map(QueueRequestId.apply),
        rating = rating,
        avatar = optionalText(cursor, "avatar").flatMap(BattleAvatarKey.fromWire),
        skin = optionalText(cursor, "skin").flatMap(BattleSkinKey.fromWire)
      )
    }

  private def optionalText(cursor: HCursor, field: String): Option[String] =
    cursor.downField(field).focus.flatMap(_.asString).flatMap(nonEmptyText)

  private def decodeHandle(cursor: HCursor): Either[DecodingFailure, PlayerHandle] =
    optionalText(cursor, "handle")
      .flatMap(PlayerHandle.forLookup)
      .toRight(DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.InvalidHandle), cursor.history))

  private def decodeSessionToken(cursor: HCursor): Either[DecodingFailure, SessionToken] =
    optionalText(cursor, "sessionToken")
      .flatMap(SessionToken.fromString)
      .toRight(DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.MissingSession), cursor.history))

  private def decodeOptionalRating(cursor: HCursor, field: String): Either[DecodingFailure, Option[Rating]] =
    cursor.get[Option[Int]](field) match {
      case Right(value) =>
        Right(value.map(Rating.apply))
      case Left(_) =>
        cursor.get[Option[String]](field) match {
          case Right(None) =>
            Right(None)
          case Right(Some(value)) =>
            decodeRatingText(value, cursor)
          case Left(_) =>
            Left(invalidRating(cursor))
        }
    }

  private def decodeRatingText(value: String, cursor: HCursor): Either[DecodingFailure, Option[Rating]] =
    value.trim match {
      case "" =>
        Right(None)
      case trimmed =>
        trimmed.toIntOption
          .map(parsed => Right(Some(Rating(parsed))))
          .getOrElse(Left(invalidRating(cursor)))
    }

  private def decodeOptionalBattleMode(cursor: HCursor, field: String): Either[DecodingFailure, Option[BattleMode]] =
    optionalText(cursor, field) match {
      case None =>
        Right(None)
      case Some(value) =>
        BattleMode.fromWire(value).map(Some.apply).toRight(invalidBattleMode(cursor))
    }

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def invalidRating(cursor: HCursor): DecodingFailure =
    DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.InvalidRating), cursor.history)

  private def invalidBattleMode(cursor: HCursor): DecodingFailure =
    DecodingFailure(BattleAPIRequestError.message(BattleAPIRequestError.InvalidBattleMode), cursor.history)
}
