package slaydemo.backend.identity.planners

import slaydemo.backend.identity.api.{IssueSessionRequest, IssueSessionResponse}

trait IdentitySessionPlanner {
  def plan(request: IssueSessionRequest): IssueSessionResponse
}
