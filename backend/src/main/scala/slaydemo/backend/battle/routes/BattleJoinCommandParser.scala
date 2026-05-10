package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.{QueueRequestId, Rating}
import slaydemo.backend.battle.services.BattleQueueJoinCommand
import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

private[routes] enum BattleQueueJoinCommandParseError {
  case InvalidHandle
  case MissingSession
}

private[routes] object BattleJoinCommandParser {
  def parse(
    fields: Map[String, BattleJsonValue]
  ): Either[String, Either[BattleQueueJoinCommandParseError, BattleQueueJoinCommand]] =
    readOptionalInt(fields, "rating").map { rating =>
      for {
        handle <- PlayerHandle.forLookup(readString(fields, "handle").getOrElse(""))
          .toRight(BattleQueueJoinCommandParseError.InvalidHandle)
        sessionToken <- SessionToken.fromString(readString(fields, "sessionToken").getOrElse(""))
          .toRight(BattleQueueJoinCommandParseError.MissingSession)
      } yield BattleQueueJoinCommand(
        handle = handle,
        sessionToken = sessionToken,
        queueRequestId = readString(fields, "queueRequestId").flatMap(nonEmptyText).map(QueueRequestId.apply),
        rating = rating.map(Rating.apply),
        avatar = readString(fields, "avatar").flatMap(nonEmptyText),
        skin = readString(fields, "skin").flatMap(nonEmptyText)
      )
    }

  private def readString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case Some(BattleJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(BattleJsonValue.NumberValue(value)) => Some(value.toString)
      case _                                        => None
    }

  private def readOptionalInt(fields: Map[String, BattleJsonValue], key: String): Either[String, Option[Int]] =
    fields.get(key) match {
      case None | Some(BattleJsonValue.NullValue) =>
        Right(None)
      case Some(BattleJsonValue.StringValue(value)) =>
        nonEmptyText(value) match {
          case None =>
            Right(None)
          case Some(trimmed) =>
            trimmed.toIntOption.map(value => Right(Some(value))).getOrElse(Left(s"$key must be an integer."))
        }
      case Some(BattleJsonValue.NumberValue(value)) if isValidInt(value) =>
        Right(Some(value.toInt))
      case Some(BattleJsonValue.NumberValue(_)) =>
        Left(s"$key must fit in a 32-bit integer.")
      case _ =>
        Left(s"$key must be an integer.")
    }

  private def isValidInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
