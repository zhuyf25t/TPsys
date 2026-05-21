package slaydemo.backend.social.objects.apiTypes

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.mail.objects.apiTypes.MailItemResponse
import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

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
