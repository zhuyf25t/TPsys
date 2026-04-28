package slaydemo.backend

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer

import slaydemo.backend.bots.database.{BotProfileRepository, FileBotProfileRepository, PostgresBotProfileRepository}
import slaydemo.backend.bots.routes.BotProfileRoutes
import slaydemo.backend.bots.services.DefaultBotProfileService
import slaydemo.backend.identity.database.{FileIdentityAccountRepository, IdentityAccountRepository, PostgresIdentityAccountRepository}
import slaydemo.backend.identity.routes.IdentityRoutes
import slaydemo.backend.identity.services.DefaultIdentityService
import slaydemo.backend.battle.database.{BattleResultRepository, FileBattleResultRepository, PostgresBattleResultRepository}
import slaydemo.backend.battle.routes.BattleRoutes
import slaydemo.backend.battle.routes.BattleQueueRoutes
import slaydemo.backend.battle.routes.BattleResultRoutes
import slaydemo.backend.battle.runtime.InMemoryAuthoritativeBattleRuntime
import slaydemo.backend.battle.services.AuthoritativeBattleFinishProjector
import slaydemo.backend.battle.services.DefaultBattleResultService
import slaydemo.backend.battle.services.InMemoryAuthoritativeBattleService
import slaydemo.backend.battle.services.InMemoryBattleQueueService
import slaydemo.backend.forum.database.{FileForumRepository, ForumRepository, PostgresForumRepository}
import slaydemo.backend.forum.routes.ForumRoutes
import slaydemo.backend.forum.services.DefaultForumService
import slaydemo.backend.governance.database.{
  ContributionAdjustmentRepository,
  FileContributionAdjustmentRepository,
  FileGovernanceReviewNotificationRepository,
  GovernanceReviewNotificationRepository,
  PostgresContributionAdjustmentRepository,
  PostgresGovernanceReviewNotificationRepository
}
import slaydemo.backend.governance.routes.GovernanceRoutes
import slaydemo.backend.governance.services.{DefaultContributionAdjustmentService, DefaultGovernanceNotificationService}
import slaydemo.backend.mails.database.{FileMailRepository, MailRepository, PostgresMailRepository}
import slaydemo.backend.mails.routes.MailsRoutes
import slaydemo.backend.mails.services.DefaultMailService
import slaydemo.backend.replay.database.{FileReplayRepository, PostgresReplayRepository, ReplayRepository}
import slaydemo.backend.replay.routes.ReplayRoutes
import slaydemo.backend.replay.services.DefaultReplayService
import slaydemo.backend.social.database.{FileFriendRequestRepository, FriendRequestRepository, PostgresFriendRequestRepository}
import slaydemo.backend.social.routes.SocialRoutes
import slaydemo.backend.social.services.DefaultFriendRequestService
import slaydemo.backend.shared.database.PostgresSupport

object BackendApp {
  def main(args: Array[String]): Unit = start()

  def start(): Unit = {
    val port = sys.env.get("SLAY_DEMO_BACKEND_PORT").flatMap(_.toIntOption).getOrElse(8080)
    val storagePath = sys.env
      .get("SLAY_DEMO_IDENTITY_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "identity-accounts.json"))
    val battleResultStoragePath = sys.env
      .get("SLAY_DEMO_BATTLE_RESULTS_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "battle-results.json"))
    val friendRequestStoragePath = sys.env
      .get("SLAY_DEMO_FRIEND_REQUESTS_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "friend-requests.json"))
    val mailStoragePath = sys.env
      .get("SLAY_DEMO_MAILS_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "mails.json"))
    val replayStoragePath = sys.env
      .get("SLAY_DEMO_REPLAY_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "replay-records.json"))
    val governanceContributionAdjustmentStoragePath = sys.env
      .get("SLAY_DEMO_GOVERNANCE_CONTRIBUTION_ADJUSTMENTS_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "governance-contribution-adjustments.json"))
    val governanceReviewNotificationStoragePath = sys.env
      .get("SLAY_DEMO_GOVERNANCE_REVIEW_NOTIFICATIONS_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "governance-review-notifications.json"))
    val forumStoragePath = sys.env
      .get("SLAY_DEMO_FORUM_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "forum.json"))
    val botProfileStoragePath = sys.env
      .get("SLAY_DEMO_BOT_PROFILES_STORE")
      .map(Paths.get(_))
      .getOrElse(Paths.get("data", "bot-profiles.json"))
    val postgresConfig = PostgresSupport.configFromEnvironment()
    val repository: IdentityAccountRepository = postgresConfig match {
      case Some(config) => new PostgresIdentityAccountRepository(config)
      case None         => new FileIdentityAccountRepository(storagePath)
    }
    val service = new DefaultIdentityService(repository)
    val routes = new IdentityRoutes(service)
    val battleResultRepository: BattleResultRepository = postgresConfig match {
      case Some(config) => new PostgresBattleResultRepository(config)
      case None         => new FileBattleResultRepository(battleResultStoragePath)
    }
    val friendRequestRepository: FriendRequestRepository = postgresConfig match {
      case Some(config) => new PostgresFriendRequestRepository(config)
      case None         => new FileFriendRequestRepository(friendRequestStoragePath)
    }
    val mailRepository: MailRepository = postgresConfig match {
      case Some(config) => new PostgresMailRepository(config)
      case None         => new FileMailRepository(mailStoragePath)
    }
    val contributionAdjustmentRepository: ContributionAdjustmentRepository = postgresConfig match {
      case Some(config) => new PostgresContributionAdjustmentRepository(config)
      case None         => new FileContributionAdjustmentRepository(governanceContributionAdjustmentStoragePath)
    }
    val governanceReviewNotificationRepository: GovernanceReviewNotificationRepository = postgresConfig match {
      case Some(config) => new PostgresGovernanceReviewNotificationRepository(config)
      case None         => new FileGovernanceReviewNotificationRepository(governanceReviewNotificationStoragePath)
    }
    val forumRepository: ForumRepository = postgresConfig match {
      case Some(config) => new PostgresForumRepository(config)
      case None         => new FileForumRepository(forumStoragePath)
    }
    val mailService = new DefaultMailService(mailRepository)
    val battleResultService = new DefaultBattleResultService(battleResultRepository, mailService)
    val battleResultRoutes = new BattleResultRoutes(battleResultService)
    val replayRepository: ReplayRepository = postgresConfig match {
      case Some(config) => new PostgresReplayRepository(config)
      case None         => new FileReplayRepository(replayStoragePath)
    }
    val replayService = new DefaultReplayService(replayRepository)
    val replayRoutes = new ReplayRoutes(replayService, Some(battleResultService))
    val authoritativeBattleFinishProjector =
      new AuthoritativeBattleFinishProjector(battleResultService, replayService)
    val authoritativeBattleDurationMs = sys.env
      .get("SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS")
      .flatMap(_.trim.toLongOption)
      .filter(_ > 0L)
      .getOrElse(InMemoryAuthoritativeBattleRuntime.DefaultBattleDurationMs)
    val authoritativeBattleRuntime =
      new InMemoryAuthoritativeBattleRuntime(configuredBattleDurationMs = authoritativeBattleDurationMs)
    val battleService = new InMemoryAuthoritativeBattleService(
      runtime = authoritativeBattleRuntime,
      finishProjector = Some(authoritativeBattleFinishProjector)
    )
    val battleQueueService = new InMemoryBattleQueueService(battleService = battleService)
    val battleQueueRoutes = new BattleQueueRoutes(battleQueueService)
    val battleRoutes = new BattleRoutes(battleService)
    val friendRequestService = new DefaultFriendRequestService(friendRequestRepository, mailService)
    val contributionAdjustmentService = new DefaultContributionAdjustmentService(contributionAdjustmentRepository, mailService)
    val governanceNotificationService =
      new DefaultGovernanceNotificationService(governanceReviewNotificationRepository, mailService)
    val forumService = new DefaultForumService(forumRepository)
    val botProfileRepository: BotProfileRepository = postgresConfig match {
      case Some(config) => new PostgresBotProfileRepository(config)
      case None         => new FileBotProfileRepository(botProfileStoragePath)
    }
    val botProfileService = new DefaultBotProfileService(botProfileRepository)
    val socialRoutes = new SocialRoutes(friendRequestService)
    val mailsRoutes =
      new MailsRoutes(mailService, friendRequestService.find, governanceNotificationService.findReviewNotificationByMailId)
    val governanceRoutes = new GovernanceRoutes(contributionAdjustmentService, governanceNotificationService)
    val forumRoutes = new ForumRoutes(forumService)
    val botProfileRoutes = new BotProfileRoutes(botProfileService)

    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/health", exchange => {
      addCors(exchange)
      try {
        exchange.getRequestMethod.toUpperCase match {
          case "OPTIONS" =>
            exchange.sendResponseHeaders(204, -1)
          case "GET" =>
            sendJson(
              exchange,
              200,
              s"""{"status":"ok","service":"slay-demo-backend","port":$port}"""
            )
          case "HEAD" =>
            exchange.sendResponseHeaders(200, -1)
          case _ =>
            sendJson(exchange, 405, """{"error":"method_not_allowed"}""")
        }
      } finally {
        exchange.close()
      }
    })
    server.createContext("/identity/register", exchange => routes.register(exchange))
    server.createContext("/identity/session", exchange => routes.issueSession(exchange))
    server.createContext("/identity/me", exchange => routes.current(exchange))
    server.createContext("/identity/accounts", exchange => routes.accounts(exchange))
    server.createContext("/battle/queue", exchange => battleQueueRoutes.handle(exchange))
    server.createContext("/battle/rooms", exchange => battleQueueRoutes.handle(exchange))
    server.createContext("/battle/state", exchange => battleRoutes.handle(exchange))
    server.createContext("/battle/commands", exchange => battleRoutes.handle(exchange))
    server.createContext("/battle/results", exchange => battleResultRoutes.handle(exchange))
    server.createContext("/social/friend-requests/respond", exchange => socialRoutes.friendRequests(exchange))
    server.createContext("/social/friend-requests", exchange => socialRoutes.friendRequests(exchange))
    server.createContext("/governance/contribution-adjustments", exchange => governanceRoutes.contributionAdjustments(exchange))
    server.createContext("/governance/admin-notifications", exchange => governanceRoutes.adminNotifications(exchange))
    server.createContext("/forum/topics", exchange => forumRoutes.handle(exchange))
    server.createContext("/bots/profiles", exchange => botProfileRoutes.handle(exchange))
    server.createContext("/bot/profiles", exchange => botProfileRoutes.handle(exchange))
    server.createContext("/mails", exchange => mailsRoutes.mails(exchange))
    server.createContext("/mails/read", exchange => mailsRoutes.read(exchange))
    server.createContext("/replay/catalog", exchange => replayRoutes.handle(exchange))
    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    println(s"Slay demo backend listening on http://127.0.0.1:$port")

    while (true) {
      Thread.sleep(60_000L)
    }
  }

  private def addCors(exchange: com.sun.net.httpserver.HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def sendJson(exchange: com.sun.net.httpserver.HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }
}
