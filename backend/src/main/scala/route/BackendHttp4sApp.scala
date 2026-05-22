package route

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger

import services.{BackendConfig, BackendEnvironment, BackendRuntime}
import services.battle.routes.BattleAPIMessageServices
import route.governance.GovernanceHttpServices
import system.database.PostgresSupport

object BackendHttp4sApp extends IOApp.Simple {
  private val logger = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    for
      env <- IO.blocking(BackendEnvironment.load())
      _ <- runtimeResource(env).use { runtime =>
        for
          port <- httpPort(runtime.config)
          httpApp = HttpApiModules
            .routes(httpApiServices(runtime))
            .orNotFound
          _ <- logger.info(s"Starting Slay http4s backend shell on http://0.0.0.0:${port.value}")
          _ <- EmberServerBuilder
            .default[IO]
            .withHost(host"0.0.0.0")
            .withPort(port)
            .withHttpApp(httpApp)
            .build
            .useForever
        yield ()
      }
    yield ()

  private def runtimeResource(env: Map[String, String]): Resource[IO, BackendRuntime] =
    Resource.make(IO.blocking(BackendRuntime.fromEnvironment(env)))(_ => IO.blocking(PostgresSupport.closeAll()))

  private def httpApiServices(runtime: BackendRuntime): HttpApiServices =
    HttpApiServices(
      healthService = runtime.healthService,
      replayService = runtime.replayService,
      battleServices = BattleAPIMessageServices(
        queueService = runtime.battleQueueService,
        joinAuthorizationService = runtime.battleJoinAuthorizationService,
        resultService = runtime.battleResultService,
        stateService = runtime.battleStateService
      ),
      botProfileService = runtime.botProfileService,
      identityService = runtime.identityService,
      mailService = runtime.mailService,
      friendRequestService = runtime.friendRequestService,
      forumService = runtime.forumService,
      governanceServices = GovernanceHttpServices(
        contributionAdjustmentService = runtime.contributionAdjustmentService,
        notificationService = runtime.governanceNotificationService
      )
    )

  private def httpPort(config: BackendConfig): IO[Port] =
    IO.fromOption(Port.fromInt(config.port.value))(
      IllegalArgumentException(s"Invalid backend port: ${config.port.value}")
    )
}
