package slaydemo.backend.mail.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.MailId
import slaydemo.backend.shared.policies.HandlePolicy

private[routes] object MailCommandParsers {
  def parseOwner(rawQuery: String): Either[MailRouteOwnerError, PlayerHandle] =
    parseOwnerHandle(queryParams(rawQuery).get("ownerHandle"))

  def parseReadCommand(fields: Map[String, String]): Either[MailRouteReadError, MailReadCommand] =
    parseOwnerHandle(fields.get("ownerHandle")) match {
      case Left(MailRouteOwnerError.MissingOwner) =>
        Left(MailRouteReadError.MissingOwner)
      case Left(MailRouteOwnerError.VisitorNotAllowed) =>
        Left(MailRouteReadError.VisitorNotAllowed)
      case Left(MailRouteOwnerError.InvalidOwner) =>
        Left(MailRouteReadError.InvalidOwner)
      case Right(owner) =>
        parseMailId(fields.get("mailId")).map(mailId => MailReadCommand(owner, mailId))
    }

  private def queryParams(rawQuery: String): Map[String, String] =
    Option(rawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def parseOwnerHandle(value: Option[String]): Either[MailRouteOwnerError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(MailRouteOwnerError.MissingOwner)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(MailRouteOwnerError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(MailRouteOwnerError.InvalidOwner)
  }

  private def parseMailId(value: Option[String]): Either[MailRouteReadError, MailId] =
    value.map(_.trim).filter(_.nonEmpty).map(MailId.apply).toRight(MailRouteReadError.MissingMailId)

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

private[routes] final case class MailReadCommand(
  ownerHandle: PlayerHandle,
  mailId: MailId
)

private[routes] enum MailRouteOwnerError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
}

private[routes] enum MailRouteReadError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case MissingMailId
}
