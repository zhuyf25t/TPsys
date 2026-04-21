package slaydemo.backend.identity.api

import slaydemo.backend.shared.objects.UserId

final case class IssueSessionRequest(handle: String, password: String)
final case class IssueSessionResponse(userId: UserId, sessionToken: String)

trait IdentitySessionApi {
  def issueSession(request: IssueSessionRequest): IssueSessionResponse
}
