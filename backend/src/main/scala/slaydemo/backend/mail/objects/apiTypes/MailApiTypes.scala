package slaydemo.backend.mail.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}

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

final case class MailReadApiRequest(
  ownerHandle: Option[String],
  mailId: Option[String]
) {
  def toCommand: Either[MailRouteReadError, MailReadCommand] =
    MailCommandParsers.parseReadCommand(this)
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
      case MailRouteOwnerError.MissingOwner       => MailApiErrorCode.MissingOwner
      case MailRouteOwnerError.VisitorNotAllowed  => MailApiErrorCode.VisitorNotAllowed
      case MailRouteOwnerError.InvalidOwner       => MailApiErrorCode.InvalidOwner
    }

  def fromReadError(error: MailRouteReadError): MailApiErrorCode =
    error match {
      case MailRouteReadError.MissingOwner       => MailApiErrorCode.MissingOwner
      case MailRouteReadError.VisitorNotAllowed  => MailApiErrorCode.VisitorNotAllowed
      case MailRouteReadError.InvalidOwner       => MailApiErrorCode.InvalidOwner
      case MailRouteReadError.MissingMailId      => MailApiErrorCode.MissingMailId
    }

  def wireValue(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed    => "method_not_allowed"
      case MailApiErrorCode.InvalidJsonObject   => "bad_request"
      case MailApiErrorCode.MissingOwner        => "missing_owner"
      case MailApiErrorCode.VisitorNotAllowed   => "visitor_not_allowed"
      case MailApiErrorCode.InvalidOwner        => "invalid_owner"
      case MailApiErrorCode.MissingMailId       => "missing_mail_id"
      case MailApiErrorCode.MailNotFound        => "mail_not_found"
    }

  def message(code: MailApiErrorCode): String =
    code match {
      case MailApiErrorCode.MethodNotAllowed   => "Method is not allowed."
      case MailApiErrorCode.InvalidJsonObject  => "Request body must be a JSON object with string fields."
      case _                                   => wireValue(code)
    }

  def statusCode(code: MailApiErrorCode): Int =
    code match {
      case MailApiErrorCode.MethodNotAllowed    => 405
      case MailApiErrorCode.VisitorNotAllowed   => 403
      case MailApiErrorCode.MailNotFound        => 404
      case _                                    => 400
    }
}

object MailReadApiRequest {
  given Decoder[MailReadApiRequest] = (cursor: HCursor) =>
    cursor.value.asObject match {
      case None =>
        Left(DecodingFailure("mail read request must be a JSON object.", cursor.history))
      case Some(_) =>
        for
          ownerHandle <- optionalString(cursor, "ownerHandle")
          mailId <- optionalString(cursor, "mailId")
        yield MailReadApiRequest(ownerHandle = ownerHandle, mailId = mailId)
    }

  private def optionalString(cursor: HCursor, field: String): Decoder.Result[Option[String]] =
    cursor.downField(field).focus match {
      case None =>
        Right(None)
      case Some(value) if value.isString =>
        Right(value.asString)
      case Some(_) =>
        Left(DecodingFailure(s"$field must be a string.", cursor.history))
    }
}

object MailOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[MailRouteOwnerError, PlayerHandle] =
    parse(query.get("ownerHandle"))

  def parse(ownerHandle: Option[String]): Either[MailRouteOwnerError, PlayerHandle] =
    MailCommandParsers.parseOwner(ownerHandle)
}

final case class MailItemResponse(
  id: String,
  ownerHandle: String,
  kind: MailKind,
  subject: String,
  excerpt: String,
  senderLabel: String,
  unread: Boolean,
  important: Boolean,
  createdAt: Long,
  sourceBattleId: Option[String],
  sourcePath: Option[String],
  sourceLabel: Option[String],
  friendRequestId: Option[String],
  friendRequestStatus: Option[MailFriendRequestStatus],
  friendRequestSourceHandle: Option[String],
  governanceActorHandle: Option[String],
  governanceTargetPath: Option[String],
  governanceTargetLabel: Option[String]
)

object MailItemResponse {
  given Encoder[MailItemResponse] =
    Encoder.forProduct18(
      "id",
      "ownerHandle",
      "kind",
      "subject",
      "excerpt",
      "senderLabel",
      "unread",
      "important",
      "createdAt",
      "sourceBattleId",
      "sourcePath",
      "sourceLabel",
      "friendRequestId",
      "friendRequestStatus",
      "friendRequestSourceHandle",
      "governanceActorHandle",
      "governanceTargetPath",
      "governanceTargetLabel"
    )((item: MailItemResponse) =>
      (
        item.id,
        item.ownerHandle,
        MailKind.wireValue(item.kind),
        item.subject,
        item.excerpt,
        item.senderLabel,
        item.unread,
        item.important,
        item.createdAt,
        optionalString(item.sourceBattleId),
        optionalString(item.sourcePath),
        optionalString(item.sourceLabel),
        optionalString(item.friendRequestId),
        item.friendRequestStatus.map(MailFriendRequestStatus.wireValue),
        optionalString(item.friendRequestSourceHandle),
        optionalString(item.governanceActorHandle),
        optionalString(item.governanceTargetPath),
        optionalString(item.governanceTargetLabel)
      )
    ).mapJson(_.dropNullValues)

  def fromRecord(record: MailRecord): MailItemResponse =
    MailItemResponse(
      id = record.id.value,
      ownerHandle = record.ownerHandle.value,
      kind = record.kind,
      subject = record.subject,
      excerpt = record.excerpt,
      senderLabel = record.senderLabel,
      unread = record.unread,
      important = record.important,
      createdAt = record.createdAt.value,
      sourceBattleId = record.sourceBattleId.filter(_.trim.nonEmpty),
      sourcePath = record.sourcePath.filter(_.trim.nonEmpty),
      sourceLabel = record.sourceLabel.filter(_.trim.nonEmpty),
      friendRequestId = record.friendRequestMetadata.map(_.requestId.value),
      friendRequestStatus = record.friendRequestMetadata.map(_.status),
      friendRequestSourceHandle = record.friendRequestMetadata.map(_.sourceHandle.value),
      governanceActorHandle = record.governanceMetadata.map(_.actorHandle.value),
      governanceTargetPath = record.governanceMetadata.map(_.targetPath.value),
      governanceTargetLabel = record.governanceMetadata.map(_.targetLabel.value)
    )

  private def optionalString(value: Option[String]): Option[String] =
    value.filter(_.trim.nonEmpty)
}

final case class MailListResponse(mails: Vector[MailItemResponse])

object MailListResponse {
  given Encoder[MailListResponse] =
    Encoder.forProduct1("mails")(_.mails)

  def fromRecords(records: Vector[MailRecord]): MailListResponse =
    MailListResponse(records.map(MailItemResponse.fromRecord))
}

enum MailReadApiOutcome {
  case Read
}

object MailReadApiOutcome {
  def okFlag(outcome: MailReadApiOutcome): Boolean =
    outcome match {
      case MailReadApiOutcome.Read => true
    }
}

final case class MailReadResponse(outcome: MailReadApiOutcome)

object MailReadResponse {
  val Read: MailReadResponse =
    MailReadResponse(MailReadApiOutcome.Read)

  given Encoder[MailReadResponse] =
    Encoder.forProduct1("ok")(response => MailReadApiOutcome.okFlag(response.outcome))
}
