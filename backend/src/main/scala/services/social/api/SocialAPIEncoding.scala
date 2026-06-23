package services.social.api

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import services.mail.api.MailItemResponse
import services.social.objects.{FriendRequestRecord, FriendRequestStatus}
import services.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

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
