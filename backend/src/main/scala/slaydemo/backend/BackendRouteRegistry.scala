package slaydemo.backend

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import slaydemo.backend.battle.routes.{BattleResultRoutes, BattleRoutes}
import slaydemo.backend.bots.routes.BotProfileRoutes
import slaydemo.backend.forum.routes.ForumRoutes
import slaydemo.backend.governance.routes.GovernanceRoutes
import slaydemo.backend.identity.routes.IdentityRoutes
import slaydemo.backend.mail.routes.MailRoutes
import slaydemo.backend.replay.routes.ReplayRoutes
import slaydemo.backend.shared.routes.HealthRoutes
import slaydemo.backend.social.routes.SocialRoutes

private[backend] final case class BackendRouteHandler(path: String, handle: HttpExchange => Unit)

private[backend] object BackendRouteRegistry {
  def routeHandlers(
    healthRoutes: HealthRoutes,
    identityRoutes: IdentityRoutes,
    battleRoutes: BattleRoutes,
    battleResultRoutes: BattleResultRoutes,
    replayRoutes: ReplayRoutes,
    mailRoutes: MailRoutes,
    botProfileRoutes: BotProfileRoutes,
    socialRoutes: SocialRoutes,
    forumRoutes: ForumRoutes,
    governanceRoutes: GovernanceRoutes
  ): Vector[BackendRouteHandler] = {
    val baseHandlers = Vector(
      BackendRouteHandler("/health", healthRoutes.handle),
      BackendRouteHandler("/identity/register", identityRoutes.register),
      BackendRouteHandler("/identity/session", identityRoutes.issueSession),
      BackendRouteHandler("/identity/me", identityRoutes.current),
      BackendRouteHandler("/identity/accounts", identityRoutes.accounts),
      BackendRouteHandler("/battle/queue/join", battleRoutes.join),
      BackendRouteHandler("/battle/queue/status", battleRoutes.status),
      BackendRouteHandler("/battle/queue/leave", battleRoutes.leave),
      BackendRouteHandler("/battle/rooms", battleRoutes.rooms),
      BackendRouteHandler("/battle/state", battleRoutes.state),
      BackendRouteHandler("/battle/commands", battleRoutes.commands),
      BackendRouteHandler("/battle/results", battleResultRoutes.handle),
      BackendRouteHandler("/replay/catalog", replayRoutes.handle),
      BackendRouteHandler("/mails", mailRoutes.mails),
      BackendRouteHandler("/mails/read", mailRoutes.read),
      BackendRouteHandler("/bots/profiles", botProfileRoutes.handle),
      BackendRouteHandler("/bot/profiles", botProfileRoutes.handle),
      BackendRouteHandler("/social/friend-requests/respond", socialRoutes.friendRequests),
      BackendRouteHandler("/social/friend-requests", socialRoutes.friendRequests),
      BackendRouteHandler("/forum/topics", forumRoutes.handle),
      BackendRouteHandler("/governance/contribution-adjustments", governanceRoutes.contributionAdjustments),
      BackendRouteHandler("/governance/admin-notifications", governanceRoutes.adminNotifications)
    )

    baseHandlers ++ baseHandlers.map(handler => handler.copy(path = s"/api${handler.path}"))
  }

  def register(server: HttpServer, handlers: Vector[BackendRouteHandler]): Unit = {
    val paths = handlers.map(_.path)
    if paths != BackendRouteCatalog.RouteContexts.map(_.path) then
      throw IllegalStateException("Backend route handler table and route context metadata diverged.")
    handlers.foreach(handler => server.createContext(handler.path, exchange => handler.handle(exchange)))
  }
}
