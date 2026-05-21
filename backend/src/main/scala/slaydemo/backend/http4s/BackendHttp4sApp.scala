package slaydemo.backend.http4s

import cats.effect.{IO, IOApp, Resource}
import com.comcast.ip4s.{Port, host}
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.log4cats.slf4j.Slf4jLogger

import slaydemo.backend.{BackendConfig, BackendEnvironment, BackendRuntime}
import slaydemo.backend.shared.database.PostgresSupport

object BackendHttp4sApp extends IOApp.Simple {
  private val logger = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    for
      env <- IO.blocking(BackendEnvironment.load())
      _ <- runtimeResource(env).use { runtime =>
        for
          port <- httpPort(runtime.config)
          httpApp = BackendHttp4sRoutes
            .backendRoutes(
              runtime.healthService,
              runtime.replayService,
              runtime.battleQueueService,
              runtime.battleJoinAuthorizationService,
              runtime.battleResultService,
              runtime.battleStateService,
              runtime.botProfileService,
              runtime.identityService,
              runtime.mailService,
              runtime.friendRequestService,
              runtime.forumService,
              runtime.contributionAdjustmentService,
              runtime.governanceNotificationService
            )
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

  private def httpPort(config: BackendConfig): IO[Port] =
    IO.fromOption(Port.fromInt(config.port.value))(
      IllegalArgumentException(s"Invalid backend port: ${config.port.value}")
    )
}
