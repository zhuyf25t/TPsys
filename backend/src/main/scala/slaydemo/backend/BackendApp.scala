package slaydemo.backend

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import slaydemo.backend.battle.objects.DurationMillis
import slaydemo.backend.battle.routes.{BattleResultRoutes, BattleRoutes}
import slaydemo.backend.battle.services.{
  DefaultBattleQueueJoinAuthorizationService,
  DefaultBattleFinishProjector,
  DefaultBattleResultService,
  InMemoryBattleQueueService,
  InMemoryBattleStateService
}
import slaydemo.backend.bots.routes.BotProfileRoutes
import slaydemo.backend.bots.services.DefaultBotProfileService
import slaydemo.backend.forum.routes.ForumRoutes
import slaydemo.backend.forum.services.DefaultForumService
import slaydemo.backend.governance.routes.GovernanceRoutes
import slaydemo.backend.governance.services.DefaultGovernanceService
import slaydemo.backend.identity.ports.{Sha256PasswordHasher, UuidIdentityIdGenerator, UuidSessionTokenGenerator}
import slaydemo.backend.identity.routes.IdentityRoutes
import slaydemo.backend.identity.services.DefaultIdentityService
import slaydemo.backend.mail.routes.MailRoutes
import slaydemo.backend.mail.services.DefaultMailService
import slaydemo.backend.replay.routes.ReplayRoutes
import slaydemo.backend.replay.services.DefaultReplayService
import slaydemo.backend.shared.objects.ServiceName
import slaydemo.backend.shared.routes.HealthRoutes
import slaydemo.backend.shared.services.StaticHealthService
import slaydemo.backend.social.routes.SocialRoutes
import slaydemo.backend.social.services.DefaultFriendRequestService

object BackendApp {
  private[backend] final case class BackendRouteContext(path: String)

  private[backend] val BaseRouteContexts: Vector[BackendRouteContext] =
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

  private[backend] val RouteContexts: Vector[BackendRouteContext] =
    BaseRouteContexts ++ BaseRouteContexts.map(context => BackendRouteContext(s"/api${context.path}"))

  def main(args: Array[String]): Unit =
    start()

  def start(env: Map[String, String] = sys.env): Unit = {
    val config = BackendConfig.unsafeFromEnvironment(env)
    val healthService = StaticHealthService(ServiceName.Backend, config.port, config.storage.mode)
    val healthRoutes = HealthRoutes(healthService)
    val repositories = BackendRepositories.fromStorage(config.storage)
    val identityService = DefaultIdentityService(
      repository = repositories.identity,
      identityIdGenerator = UuidIdentityIdGenerator(),
      sessionTokenGenerator = UuidSessionTokenGenerator(),
      passwordHasher = Sha256PasswordHasher()
    )
    val identityRoutes = IdentityRoutes(identityService)
    val battleQueueService = InMemoryBattleQueueService()
    val battleJoinAuthorizationService = DefaultBattleQueueJoinAuthorizationService(identityService)
    val battleResultService = DefaultBattleResultService(repositories.battleResults)
    val replayService = DefaultReplayService(repositories.replay, () => System.currentTimeMillis())
    val mailService = DefaultMailService(repositories.mail, () => System.currentTimeMillis())
    val battleFinishProjector = DefaultBattleFinishProjector(
      battleResultRepository = repositories.battleResults,
      replayRepository = repositories.replay,
      mailRepository = repositories.mail
    )
    val battleStateService = InMemoryBattleStateService(
      battleQueueService,
      battleDurationFor(env),
      battleFinishProjector,
      battleQueueService
    )
    val botProfileService = DefaultBotProfileService(repositories.botProfiles)
    val friendRequestService = DefaultFriendRequestService(
      repositories.friendRequests,
      repositories.mail,
      () => System.currentTimeMillis()
    )
    val forumService = DefaultForumService(repositories.forum, () => System.currentTimeMillis())
    val governanceService = DefaultGovernanceService(repositories.governance, repositories.mail, () => System.currentTimeMillis())
    val battleRoutes = BattleRoutes(battleQueueService, battleStateService, battleJoinAuthorizationService)
    val battleResultRoutes = BattleResultRoutes(battleResultService)
    val replayRoutes = ReplayRoutes(replayService)
    val mailRoutes = MailRoutes(mailService)
    val botProfileRoutes = BotProfileRoutes(botProfileService)
    val socialRoutes = SocialRoutes(friendRequestService)
    val forumRoutes = ForumRoutes(forumService)
    val governanceRoutes = GovernanceRoutes(governanceService, governanceService)

    val server = HttpServer.create(InetSocketAddress(config.port.value), 0)
    registerRouteHandlers(
      server,
      routeHandlers(
        healthRoutes = healthRoutes,
        identityRoutes = identityRoutes,
        battleRoutes = battleRoutes,
        battleResultRoutes = battleResultRoutes,
        replayRoutes = replayRoutes,
        mailRoutes = mailRoutes,
        botProfileRoutes = botProfileRoutes,
        socialRoutes = socialRoutes,
        forumRoutes = forumRoutes,
        governanceRoutes = governanceRoutes
      )
    )
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    println(s"Slay demo backend listening on http://127.0.0.1:${config.port.value}")
    awaitForever()
  }

  private def awaitForever(): Unit =
    while true do Thread.sleep(60_000L)

  private final case class BackendRouteHandler(path: String, handle: HttpExchange => Unit)

  private def routeHandlers(
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

  private def registerRouteHandlers(server: HttpServer, handlers: Vector[BackendRouteHandler]): Unit = {
    val paths = handlers.map(_.path)
    if paths != RouteContexts.map(_.path) then
      throw IllegalStateException("Backend route handler table and route context metadata diverged.")
    handlers.foreach(handler => server.createContext(handler.path, exchange => handler.handle(exchange)))
  }

  private def battleDurationFor(env: Map[String, String]): DurationMillis =
    env
      .get("SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS")
      .flatMap(value => value.trim.toLongOption)
      .filter(_ > 0L)
      .map(DurationMillis.apply)
      .getOrElse(InMemoryBattleStateService.DefaultBattleDuration)
}
