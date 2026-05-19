package slaydemo.backend

private[backend] final case class BackendRouteContext(path: String)

private[backend] object BackendRouteCatalog {
  val BaseRouteContexts: Vector[BackendRouteContext] =
    Vector(
      "/health",
      "/identity/register",
      "/identity/session",
      "/identity/me",
      "/identity/accounts",
      "/replay/catalog",
      "/mails",
      "/mails/read",
      "/bots/profiles",
      "/bot/profiles",
      "/social/friend-requests/respond",
      "/social/friend-requests",
      "/forum/topics",
      "/governance/contribution-adjustments",
      "/governance/admin-notifications"
    ).map(BackendRouteContext.apply)

  val ApiMessageRouteContexts: Vector[BackendRouteContext] =
    Vector(
      "/api/battlequeuejoinapi",
      "/api/battlequeuestatusapi",
      "/api/battlequeueleaveapi",
      "/api/battleroomsnapshotapi",
      "/api/battleroomheartbeatapi",
      "/api/battlestatereadapi",
      "/api/battlestatestreamapi",
      "/api/battlecommandapi",
      "/api/battleresultsapi"
    ).map(BackendRouteContext.apply)

  val RouteContexts: Vector[BackendRouteContext] =
    BaseRouteContexts ++ BaseRouteContexts.map(context => BackendRouteContext(s"/api${context.path}")) ++ ApiMessageRouteContexts
}
