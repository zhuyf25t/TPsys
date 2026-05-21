package slaydemo.backend.social.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.apiTypes.MailItemResponse
import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestSubmissionResult
}

object SocialRequestTarget {
  private val FriendRequestPaths: Set[String] =
    Set("/social/friend-requests", "/api/social/friend-requests")
  private val FriendRequestRespondPaths: Set[String] =
    Set("/social/friend-requests/respond", "/api/social/friend-requests/respond")

  def isFriendRequestPath(path: String): Boolean =
    FriendRequestPaths.contains(path)

  def isFriendRequestRespondPath(path: String): Boolean =
    FriendRequestRespondPaths.contains(path)
}

enum SocialApiRequestDecodeError {
  case InvalidJsonObject
}

enum SocialApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case InvalidHandles
  case RequestNotFound
  case Forbidden
  case InvalidDecision
  case MissingFields
  case InvalidActor
}

object SocialApiErrorCode {
  def fromOwnerError(error: SocialRouteHandleError): SocialApiErrorCode =
    error match {
      case SocialRouteHandleError.Missing             => SocialApiErrorCode.MissingOwner
      case SocialRouteHandleError.VisitorNotAllowed   => SocialApiErrorCode.VisitorNotAllowed
      case SocialRouteHandleError.Invalid             => SocialApiErrorCode.InvalidOwner
    }

  def fromCreateRouteError(error: SocialRouteCreateError): SocialApiErrorCode =
    error match {
      case SocialRouteCreateError.InvalidHandles      => SocialApiErrorCode.InvalidHandles
      case SocialRouteCreateError.VisitorNotAllowed   => SocialApiErrorCode.VisitorNotAllowed
    }

  def fromCreateServiceError(error: FriendRequestCreateError): SocialApiErrorCode =
    error match {
      case FriendRequestCreateError.InvalidHandles => SocialApiErrorCode.InvalidHandles
    }

  def fromRespondRouteError(error: SocialRouteRespondError): SocialApiErrorCode =
    error match {
      case SocialRouteRespondError.InvalidDecision     => SocialApiErrorCode.InvalidDecision
      case SocialRouteRespondError.MissingFields       => SocialApiErrorCode.MissingFields
      case SocialRouteRespondError.InvalidActorHandle  => SocialApiErrorCode.InvalidActor
      case SocialRouteRespondError.VisitorNotAllowed   => SocialApiErrorCode.VisitorNotAllowed
    }

  def fromRespondServiceError(error: FriendRequestRespondError): SocialApiErrorCode =
    error match {
      case FriendRequestRespondError.RequestNotFound => SocialApiErrorCode.RequestNotFound
      case FriendRequestRespondError.Forbidden       => SocialApiErrorCode.Forbidden
    }

  def wireValue(code: SocialApiErrorCode): String =
    code match {
      case SocialApiErrorCode.MethodNotAllowed   => "method_not_allowed"
      case SocialApiErrorCode.InvalidJsonObject  => "bad_request"
      case SocialApiErrorCode.MissingOwner       => "missing_owner"
      case SocialApiErrorCode.VisitorNotAllowed  => "visitor_not_allowed"
      case SocialApiErrorCode.InvalidOwner       => "invalid_owner"
      case SocialApiErrorCode.InvalidHandles     => "invalid_handles"
      case SocialApiErrorCode.RequestNotFound    => "request_not_found"
      case SocialApiErrorCode.Forbidden          => "forbidden"
      case SocialApiErrorCode.InvalidDecision    => "invalid_decision"
      case SocialApiErrorCode.MissingFields      => "missing_fields"
      case SocialApiErrorCode.InvalidActor       => "invalid_actor"
    }

  def message(code: SocialApiErrorCode): String =
    code match {
      case SocialApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case SocialApiErrorCode.InvalidJsonObject => "Request body must be a JSON object with string fields."
      case _                                    => wireValue(code)
    }

  def statusCode(code: SocialApiErrorCode): Int =
    code match {
      case SocialApiErrorCode.MethodNotAllowed   => 405
      case SocialApiErrorCode.VisitorNotAllowed  => 403
      case SocialApiErrorCode.RequestNotFound    => 404
      case SocialApiErrorCode.Forbidden          => 403
      case _                                     => 400
    }
}

final case class FriendRequestCreateApiRequest(
  sourceHandle: Option[String],
  targetHandle: Option[String]
) {
  def toCreateHandles: Either[SocialRouteCreateError, SocialCreateHandles] =
    SocialCommandParsers.parseCreateHandles(this)
}

object FriendRequestCreateApiRequest {
  given Decoder[FriendRequestCreateApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        sourceHandle <- optionalString(cursor, "sourceHandle")
        targetHandle <- optionalString(cursor, "targetHandle")
      yield FriendRequestCreateApiRequest(sourceHandle = sourceHandle, targetHandle = targetHandle)
    }
}

final case class FriendRequestRespondApiRequest(
  requestId: Option[String],
  actorHandle: Option[String],
  decision: Option[String]
) {
  def toRespondCommand: Either[SocialRouteRespondError, SocialRespondCommand] =
    SocialCommandParsers.parseRespondCommand(this)
}

object FriendRequestOwnerQuery {
  def parseFromQuery(query: Map[String, String]): Either[SocialRouteHandleError, PlayerHandle] =
    parse(query.get("ownerHandle"))

  def parse(ownerHandle: Option[String]): Either[SocialRouteHandleError, PlayerHandle] =
    SocialCommandParsers.parseOwner(ownerHandle)
}

object FriendRequestRespondApiRequest {
  given Decoder[FriendRequestRespondApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        requestId <- optionalString(cursor, "requestId")
        actorHandle <- optionalString(cursor, "actorHandle")
        decision <- optionalString(cursor, "decision")
      yield FriendRequestRespondApiRequest(requestId = requestId, actorHandle = actorHandle, decision = decision)
    }
}

final case class FriendRequestResponse(
  id: String,
  sourceHandle: String,
  targetHandle: String,
  createdAt: Long,
  status: String,
  respondedAt: Option[Long]
)

object FriendRequestResponse {
  given Encoder[FriendRequestResponse] = deriveEncoder

  def fromRecord(record: FriendRequestRecord): FriendRequestResponse =
    FriendRequestResponse(
      id = record.id.value,
      sourceHandle = record.sourceHandle.value,
      targetHandle = record.targetHandle.value,
      createdAt = record.createdAt.value,
      status = FriendRequestStatus.wireValue(record.status),
      respondedAt = record.respondedAt.map(_.value)
    )
}

final case class FriendRequestListResponse(requests: Vector[FriendRequestResponse])

object FriendRequestListResponse {
  given Encoder[FriendRequestListResponse] = deriveEncoder

  def fromRecords(records: Vector[FriendRequestRecord]): FriendRequestListResponse =
    FriendRequestListResponse(records.map(FriendRequestResponse.fromRecord))
}

final case class FriendRequestCreateResponse(
  created: Boolean,
  alreadySent: Boolean,
  request: FriendRequestResponse,
  mail: Option[MailItemResponse]
)

object FriendRequestCreateResponse {
  given Encoder[FriendRequestCreateResponse] = deriveEncoder

  def fromResult(result: FriendRequestSubmissionResult): FriendRequestCreateResponse =
    FriendRequestCreateResponse(
      created = result match {
        case FriendRequestSubmissionResult.Created(_, _) => true
        case FriendRequestSubmissionResult.AlreadySent(_) => false
      },
      alreadySent = result match {
        case FriendRequestSubmissionResult.Created(_, _) => false
        case FriendRequestSubmissionResult.AlreadySent(_) => true
      },
      request = FriendRequestResponse.fromRecord(result.friendRequest),
      mail = result.notificationMail.map(MailItemResponse.fromRecord)
    )
}

final case class FriendRequestRespondResponse(
  request: FriendRequestResponse,
  mail: Option[MailItemResponse]
)

object FriendRequestRespondResponse {
  given Encoder[FriendRequestRespondResponse] = deriveEncoder

  def fromResult(result: FriendRequestResponseResult): FriendRequestRespondResponse =
    FriendRequestRespondResponse(
      request = FriendRequestResponse.fromRecord(result.friendRequest),
      mail = result.notificationMail.map(MailItemResponse.fromRecord)
    )
}

private def requireObject(cursor: HCursor): Decoder.Result[Unit] =
  cursor.value.asObject match {
    case Some(_) => Right(())
    case None    => Left(DecodingFailure("social request must be a JSON object.", cursor.history))
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
