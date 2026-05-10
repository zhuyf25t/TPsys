package slaydemo.backend

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer

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
  private[backend] val BaseRouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.BaseRouteContexts

  private[backend] val RouteContexts: Vector[BackendRouteContext] =
    BackendRouteCatalog.RouteContexts

  def main(args: Array[String]): Unit =
    start(BackendEnvironment.load())

  def start(env: Map[String, String] = BackendEnvironment.load()): Unit = {
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
    BackendRouteRegistry.register(
      server,
      BackendRouteRegistry.routeHandlers(
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

  private def battleDurationFor(env: Map[String, String]): DurationMillis =
    env
      .get("SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS")
      .flatMap(value => value.trim.toLongOption)
      .filter(_ > 0L)
      .map(DurationMillis.apply)
      .getOrElse(InMemoryBattleStateService.DefaultBattleDuration)
}
