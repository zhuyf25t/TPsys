package services.mail.api

import services.identity.objects.PlayerHandle
import services.mail.objects.MailId
import system.policies.HandlePolicy

object MailOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[MailRouteOwnerError, PlayerHandle] =
    parse(query.get("ownerHandle").flatMap(MailAPIMessageDecoding.playerHandleFromWire))

  def parse(ownerHandle: Option[PlayerHandle]): Either[MailRouteOwnerError, PlayerHandle] =
    MailCommandParsers.parseOwner(ownerHandle)
}

object MailCommandParsers {
  def parseOwner(ownerHandle: Option[PlayerHandle]): Either[MailRouteOwnerError, PlayerHandle] =
    parseOwnerHandle(ownerHandle)

  def parseReadCommand(message: MailReadAPIMessage): Either[MailRouteReadError, MailReadCommand] =
    parseReadCommandFields(ownerHandle = message.ownerHandle, mailId = message.mailId)

  private def parseReadCommandFields(
    ownerHandle: Option[PlayerHandle],
    mailId: Option[MailId]
  ): Either[MailRouteReadError, MailReadCommand] =
    parseOwnerHandle(ownerHandle) match {
      case Left(MailRouteOwnerError.MissingOwner) =>
        Left(MailRouteReadError.MissingOwner)
      case Left(MailRouteOwnerError.VisitorNotAllowed) =>
        Left(MailRouteReadError.VisitorNotAllowed)
      case Left(MailRouteOwnerError.InvalidOwner) =>
        Left(MailRouteReadError.InvalidOwner)
      case Right(owner) =>
        mailId.map(mailId => MailReadCommand(owner, mailId)).toRight(MailRouteReadError.MissingMailId)
    }

  private def parseOwnerHandle(value: Option[PlayerHandle]): Either[MailRouteOwnerError, PlayerHandle] = {
    val trimmed = value.map(handle => HandlePolicy.trim(handle.value)).getOrElse("")
    if trimmed.isEmpty then Left(MailRouteOwnerError.MissingOwner)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(MailRouteOwnerError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(MailRouteOwnerError.InvalidOwner)
  }
}

final case class MailReadCommand(
  ownerHandle: PlayerHandle,
  mailId: MailId
)

enum MailRouteOwnerError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
}

enum MailRouteReadError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case MissingMailId
}
