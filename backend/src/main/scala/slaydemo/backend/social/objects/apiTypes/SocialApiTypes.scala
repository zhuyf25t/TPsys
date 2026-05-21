package slaydemo.backend.social.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.apiTypes.MailItemResponse
import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

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
