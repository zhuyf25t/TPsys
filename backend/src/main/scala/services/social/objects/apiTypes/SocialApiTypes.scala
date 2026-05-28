package services.social.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import services.mail.objects.apiTypes.MailItemResponse
import services.social.objects.{FriendRequestRecord, FriendRequestStatus}
import services.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

final case class FriendRequestListApiRequest(ownerHandle: Option[String])

final case class FriendRequestCreateApiRequest(
  sourceHandle: Option[String],
  targetHandle: Option[String]
)

object FriendRequestCreateApiRequest {
  given Decoder[FriendRequestCreateApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        sourceHandle <- optionalString(cursor, "sourceHandle")
        targetHandle <- optionalString(cursor, "targetHandle")
      yield FriendRequestCreateApiRequest(sourceHandle = sourceHandle, targetHandle = targetHandle)
    }
}

object FriendRequestListApiRequest {
  given Decoder[FriendRequestListApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      optionalString(cursor, "ownerHandle").map(FriendRequestListApiRequest.apply)
    }
}

final case class FriendRequestRespondApiRequest(
  requestId: Option[String],
  actorHandle: Option[String],
  decision: Option[String]
)

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
  status: FriendRequestStatus,
  respondedAt: Option[Long]
)

object FriendRequestResponse {
  given Encoder[FriendRequestResponse] =
    Encoder.forProduct6("id", "sourceHandle", "targetHandle", "createdAt", "status", "respondedAt")(response =>
      (
        response.id,
        response.sourceHandle,
        response.targetHandle,
        response.createdAt,
        FriendRequestStatus.wireValue(response.status),
        response.respondedAt
      )
    )

  def fromRecord(record: FriendRequestRecord): FriendRequestResponse =
    FriendRequestResponse(
      id = record.id.value,
      sourceHandle = record.sourceHandle.value,
      targetHandle = record.targetHandle.value,
      createdAt = record.createdAt.value,
      status = record.status,
      respondedAt = record.respondedAt.map(_.value)
    )
}

final case class FriendRequestListResponse(requests: Vector[FriendRequestResponse])

object FriendRequestListResponse {
  given Encoder[FriendRequestListResponse] = deriveEncoder

  def fromRecords(records: Vector[FriendRequestRecord]): FriendRequestListResponse =
    FriendRequestListResponse(records.map(FriendRequestResponse.fromRecord))
}

enum FriendRequestCreateApiOutcome {
  case Created
  case AlreadySent
}

object FriendRequestCreateApiOutcome {
  def fromResult(result: FriendRequestSubmissionResult): FriendRequestCreateApiOutcome =
    result match {
      case FriendRequestSubmissionResult.Created(_, _) =>
        FriendRequestCreateApiOutcome.Created
      case FriendRequestSubmissionResult.AlreadySent(_) =>
        FriendRequestCreateApiOutcome.AlreadySent
    }

  def createdFlag(outcome: FriendRequestCreateApiOutcome): Boolean =
    outcome match {
      case FriendRequestCreateApiOutcome.Created     => true
      case FriendRequestCreateApiOutcome.AlreadySent => false
    }

  def alreadySentFlag(outcome: FriendRequestCreateApiOutcome): Boolean =
    outcome match {
      case FriendRequestCreateApiOutcome.Created     => false
      case FriendRequestCreateApiOutcome.AlreadySent => true
    }
}

final case class FriendRequestCreateResponse(
  outcome: FriendRequestCreateApiOutcome,
  request: FriendRequestResponse,
  mail: Option[MailItemResponse]
)

object FriendRequestCreateResponse {
  given Encoder[FriendRequestCreateResponse] =
    Encoder.forProduct4("created", "alreadySent", "request", "mail")(response =>
      (
        FriendRequestCreateApiOutcome.createdFlag(response.outcome),
        FriendRequestCreateApiOutcome.alreadySentFlag(response.outcome),
        response.request,
        response.mail
      )
    )

  def fromResult(result: FriendRequestSubmissionResult): FriendRequestCreateResponse =
    FriendRequestCreateResponse(
      outcome = FriendRequestCreateApiOutcome.fromResult(result),
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
