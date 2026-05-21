package slaydemo.backend

object BackendRouteContextContractTest {
  def main(args: Array[String]): Unit = {
    routeContextsHaveNoDuplicates()
    baseRouteContextsAreExplicit()
    compatibilityRouteContextsAreExplicit()
    everyBaseRouteHasApiAlias()
    registeredHandlersMatchRouteCatalog()

    println("Backend route context contract checks passed")
  }

  private def routeContextsHaveNoDuplicates(): Unit = {
    val paths = BackendApp.RouteContexts.map(_.path)

    assertEquals("route context count", paths.distinct.length, paths.length)
  }

  private def baseRouteContextsAreExplicit(): Unit =
    assertEquals(
      "base route contexts",
      BackendApp.BaseRouteContexts.map(_.path),
      Vector(
        "/health",
        "/battle/queue/status",
        "/battle/queue/join",
        "/battle/queue/leave",
        "/battle/rooms/snapshot",
        "/battle/rooms/heartbeat",
        "/battle/state",
        "/battle/state/stream",
        "/battle/command",
        "/battle/results",
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
      )
    )

  private def compatibilityRouteContextsAreExplicit(): Unit =
    assertEquals(
      "compatibility route contexts",
      BackendApp.CompatibilityRouteContexts.map(_.path),
      Vector(
        "/api/healthapi",
        "/api/battlequeuestatusapi",
        "/api/battlequeuejoinapi",
        "/api/battlequeueleaveapi",
        "/api/battleroomsnapshotapi",
        "/api/battleroomheartbeatapi",
        "/api/battlestatereadapi",
        "/api/battlestatestreamapi",
        "/api/battlecommandapi",
        "/api/battleresultsapi",
        "/api/replaycatalogapi"
      )
    )

  private def everyBaseRouteHasApiAlias(): Unit = {
    val paths = BackendApp.RouteContexts.map(_.path).toSet

    BackendApp.BaseRouteContexts.foreach { context =>
      assert(paths.contains(context.path), s"missing base route ${context.path}")
      assert(paths.contains(s"/api${context.path}"), s"missing api alias for ${context.path}")
    }
    assertEquals(
      "route contexts are base plus aliases plus compatibility aliases",
      paths.size,
      BackendApp.BaseRouteContexts.length * 2 + BackendApp.CompatibilityRouteContexts.length
    )
  }

  private def registeredHandlersMatchRouteCatalog(): Unit = {
    val runtime = BackendRuntime.fromEnvironment(Map("SLAY_DEMO_STORAGE_MODE" -> "memory"))
    val handlers = BackendApp.legacyRouteHandlers(runtime)

    assertEquals(
      "registered handler paths",
      handlers.map(_.path),
      BackendRouteCatalog.RouteContexts.map(_.path)
    )
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
