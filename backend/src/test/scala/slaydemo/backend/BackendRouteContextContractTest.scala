package slaydemo.backend

object BackendRouteContextContractTest {
  def main(args: Array[String]): Unit = {
    routeContextsHaveNoDuplicates()
    baseRouteContextsAreExplicit()
    apiMessageRouteContextsAreExplicit()
    everyBaseRouteHasApiAlias()

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

  private def apiMessageRouteContextsAreExplicit(): Unit =
    assertEquals(
      "api message route contexts",
      BackendRouteCatalog.ApiMessageRouteContexts.map(_.path),
      Vector(
        "/api/healthapi",
        "/api/battlequeuejoinapi",
        "/api/battlequeuestatusapi",
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
    BackendRouteCatalog.ApiMessageRouteContexts.foreach { context =>
      assert(paths.contains(context.path), s"missing api message route ${context.path}")
    }
    assertEquals(
      "route contexts are base plus aliases plus api messages",
      paths.size,
      BackendApp.BaseRouteContexts.length * 2 + BackendRouteCatalog.ApiMessageRouteContexts.length
    )
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
