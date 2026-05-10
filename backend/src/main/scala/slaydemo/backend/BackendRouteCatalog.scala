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
      "/battle/queue/join",
      "/battle/queue/status",
      "/battle/queue/leave",
      "/battle/rooms",
      "/battle/state",
      "/battle/commands",
      "/battle/results",
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

  val RouteContexts: Vector[BackendRouteContext] =
    BaseRouteContexts ++ BaseRouteContexts.map(context => BackendRouteContext(s"/api${context.path}"))
}
