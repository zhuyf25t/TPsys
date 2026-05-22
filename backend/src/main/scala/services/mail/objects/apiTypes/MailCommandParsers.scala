package services.mail.objects.apiTypes

import services.identity.objects.PlayerHandle
import services.mail.objects.MailId
import system.policies.HandlePolicy

object MailCommandParsers {
  def parseOwner(ownerHandle: Option[String]): Either[MailRouteOwnerError, PlayerHandle] =
    parseOwnerHandle(ownerHandle)

  def parseReadCommand(request: MailReadApiRequest): Either[MailRouteReadError, MailReadCommand] =
    parseReadCommandFields(ownerHandle = request.ownerHandle, mailId = request.mailId)

  private def parseReadCommandFields(
    ownerHandle: Option[String],
    mailId: Option[String]
  ): Either[MailRouteReadError, MailReadCommand] =
    parseOwnerHandle(ownerHandle) match {
      case Left(MailRouteOwnerError.MissingOwner) =>
        Left(MailRouteReadError.MissingOwner)
      case Left(MailRouteOwnerError.VisitorNotAllowed) =>
        Left(MailRouteReadError.VisitorNotAllowed)
      case Left(MailRouteOwnerError.InvalidOwner) =>
        Left(MailRouteReadError.InvalidOwner)
      case Right(owner) =>
        parseMailId(mailId).map(mailId => MailReadCommand(owner, mailId))
    }

  private def parseOwnerHandle(value: Option[String]): Either[MailRouteOwnerError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(MailRouteOwnerError.MissingOwner)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(MailRouteOwnerError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(MailRouteOwnerError.InvalidOwner)
  }

  private def parseMailId(value: Option[String]): Either[MailRouteReadError, MailId] =
    value.map(_.trim).filter(_.nonEmpty).map(MailId.apply).toRight(MailRouteReadError.MissingMailId)
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
