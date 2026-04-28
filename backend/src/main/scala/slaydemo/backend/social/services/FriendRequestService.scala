package slaydemo.backend.social.services

import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.social.objects.FriendRequestRecord

final case class FriendRequestSubmissionResult(
  created: Boolean,
  alreadySent: Boolean,
  request: FriendRequestRecord,
  mail: Option[MailRecord]
)

final case class FriendRequestResponseResult(
  request: FriendRequestRecord,
  mail: Option[MailRecord]
)

trait FriendRequestService {
  def create(sourceHandle: String, targetHandle: String): Either[String, FriendRequestSubmissionResult]
  def respond(requestId: String, actorHandle: String, decision: String): Either[String, FriendRequestResponseResult]
  def list(ownerHandle: String): Seq[FriendRequestRecord]
  def find(requestId: String): Option[FriendRequestRecord]
}
