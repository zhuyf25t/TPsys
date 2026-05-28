package services.mail.api

import services.identity.objects.PlayerHandle
import services.mail.objects.MailId
import services.mail.objects.apiTypes.MailReadApiRequest
import system.policies.HandlePolicy

object MailRequestTarget {
  private val MailListPaths: Set[String] =
    Set("/mails", "/api/mails")
  private val MailReadPaths: Set[String] =
    Set("/mails/read", "/api/mails/read")

  def isListPath(path: String): Boolean =
    MailListPaths.contains(path)

  def isReadPath(path: String): Boolean =
    MailReadPaths.contains(path)
}

enum MailReadApiRequestDecodeError {
  case InvalidJsonObject
}

enum MailApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case MissingMailId
  case MailNotFound
}

object MailApiErrorCode {
  def fromOwnerError(error: MailRouteOwnerError): MailApiErrorCode =
    error match {
      case MailRouteOwnerError.MissingOwner      => MailApiErrorCode.MissingOwner
      case MailRouteOwnerError.VisitorNotAllowed => MailApiErrorCode.VisitorNotAllowed
      case MailRouteOwnerError.InvalidOwner      => MailApiErrorCode.InvalidOwner
    }

  def fromReadError(error: MailRouteReadError): MailApiErrorCode =
    error match {
      case MailRouteReadError.MissingOwner      => MailApiErrorCode.MissingOwner
      case MailRouteReadError.VisitorNotAllowed => MailApiErrorCode.VisitorNotAllowed
      case MailRouteReadError.InvalidOwner      => MailApiErrorCode.InvalidOwner
      case MailRouteReadError.MissingMailId     => MailApiErrorCode.MissingMailId
    }

  def wireValue(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case MailApiErrorCode.InvalidJsonObject => "bad_request"
      case MailApiErrorCode.MissingOwner      => "missing_owner"
      case MailApiErrorCode.VisitorNotAllowed => "visitor_not_allowed"
      case MailApiErrorCode.InvalidOwner      => "invalid_owner"
      case MailApiErrorCode.MissingMailId     => "missing_mail_id"
      case MailApiErrorCode.MailNotFound      => "mail_not_found"
    }

  def message(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case MailApiErrorCode.InvalidJsonObject => "Request body must be a JSON object with string fields."
      case _                                  => wireValue(code)
    }

  def statusCode(code: MailApiErrorCode): Int =
    code match {
      case MailApiErrorCode.MethodNotAllowed  => 405
      case MailApiErrorCode.VisitorNotAllowed => 403
      case MailApiErrorCode.MailNotFound      => 404
      case _                                  => 400
    }
}

object MailOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[MailRouteOwnerError, PlayerHandle] =
    parse(query.get("ownerHandle"))

  def parse(ownerHandle: Option[String]): Either[MailRouteOwnerError, PlayerHandle] =
    MailCommandParsers.parseOwner(ownerHandle)
}

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
