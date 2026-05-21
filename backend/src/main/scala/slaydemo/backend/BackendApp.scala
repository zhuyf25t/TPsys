package slaydemo.backend

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer
import slaydemo.backend.battle.routes.{BattleResultRoutes, BattleRoutes}
import slaydemo.backend.bots.routes.BotProfileRoutes
import slaydemo.backend.forum.routes.ForumRoutes
import slaydemo.backend.governance.routes.GovernanceRoutes
import slaydemo.backend.identity.routes.IdentityRoutes
import slaydemo.backend.mail.routes.MailRoutes
import slaydemo.backend.replay.routes.ReplayRoutes
import slaydemo.backend.shared.database.PostgresSupport
import slaydemo.backend.shared.routes.HealthRoutes
import slaydemo.backend.social.routes.SocialRoutes

object BackendApp {
  private[backend] val BaseRouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.BaseRouteContexts

  private[backend] val CompatibilityRouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.CompatibilityRouteContexts

  private[backend] val RouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.RouteContexts

  def main(args: Array[String]): Unit =
    start(BackendEnvironment.load())

  def start(env: Map[String, String] = BackendEnvironment.load()): Unit = {
    val runtime = BackendRuntime.fromEnvironment(env)
    Runtime.getRuntime.addShutdownHook(Thread(() => PostgresSupport.closeAll()))

    val server = HttpServer.create(InetSocketAddress(runtime.config.port.value), 0)
    BackendRouteRegistry.register(
      server,
      legacyRouteHandlers(runtime)
    )
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    println(s"Slay demo backend listening on http://127.0.0.1:${runtime.config.port.value}")
    awaitForever()
  }

  private def awaitForever(): Unit =
    while true do Thread.sleep(60_000L)

  private[backend] def legacyRouteHandlers(runtime: BackendRuntime): Vector[BackendRouteHandler] =
    BackendRouteRegistry.routeHandlers(
      healthRoutes = HealthRoutes(runtime.healthService),
      identityRoutes = IdentityRoutes(runtime.identityService),
      battleRoutes = BattleRoutes(
        runtime.battleQueueService,
        runtime.battleStateService,
        runtime.battleJoinAuthorizationService
      ),
      battleResultRoutes = BattleResultRoutes(runtime.battleResultService),
      replayRoutes = ReplayRoutes(runtime.replayService),
      mailRoutes = MailRoutes(runtime.mailService),
      botProfileRoutes = BotProfileRoutes(runtime.botProfileService),
      socialRoutes = SocialRoutes(runtime.friendRequestService),
      forumRoutes = ForumRoutes(runtime.forumService),
      governanceRoutes = GovernanceRoutes(
        runtime.contributionAdjustmentService,
        runtime.governanceNotificationService
      )
    )
}
