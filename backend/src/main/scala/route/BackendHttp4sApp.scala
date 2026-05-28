package route

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger

import services.{BackendConfig, BackendEnvironment, BackendRuntime}
import services.battle.routes.BattleAPIRuntimeContext
import route.governance.GovernanceHttpServices
import system.database.PostgresSupport
import system.storage.StorageConfig

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
      battleRuntimeContext = BattleAPIRuntimeContext(
        queueService = runtime.battleQueueService,
        joinAuthorizationService = runtime.battleJoinAuthorizationService,
        stateService = runtime.battleStateService
      ),
      battleConnectionResource = battleConnectionResource(runtime.config),
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

  private def battleConnectionResource(config: BackendConfig) =
    config.storage match {
      case StorageConfig.Postgres(connection) =>
        PostgresSupport.connectionResource(connection)
      case StorageConfig.InMemory | StorageConfig.File(_) =>
        Resource.eval(IO.raiseError[java.sql.Connection](IllegalStateException("Battle APIs require PostgreSQL storage.")))
    }
}
