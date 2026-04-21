package slaydemo.backend.social.services

import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.social.objects.FriendRequestRecord

final case class FriendRequestSubmissionResult(
  created: Boolean,
  alreadySent: Boolean,
  request: FriendRequestRecord,
  mail: Option[MailRecord]
)

trait FriendRequestService {
  def create(sourceHandle: String, targetHandle: String): Either[String, FriendRequestSubmissionResult]
}
