package route.battle

import cats.effect.{IO, Resource}
import io.circe.Json
import org.http4s.HttpRoutes
import services.identity.objects.SessionToken
import services.identity.services.{IdentityCurrentSessionError, IdentityService}
import services.battle.routes.{BattleAPIRuntimeContext, BattleRoutes}
import system.api.{APIMessageError, APIMessageRouter}

import java.sql.Connection

object BattleHttp4sRoutes {
  def routes(
    context: BattleAPIRuntimeContext,
    identityService: IdentityService,
    connectionResource: Resource[IO, Connection]
  ): HttpRoutes[IO] =
    APIMessageRouter.routes(
      apiMessages = BattleRoutes.apiMessages(context),
      resolveUserToken = resolveUserToken(identityService),
      connectionResource = connectionResource
    )

  private def resolveUserToken(identityService: IdentityService)(
    userToken: String,
    connection: Connection
  ): IO[Json] =
    IO.blocking(identityService.current(SessionToken.fromString(userToken))).flatMap {
      case Right(account) =>
        IO.pure(Json.fromString(account.userId.value))
      case Left(IdentityCurrentSessionError.MissingSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Login is required."))
      case Left(IdentityCurrentSessionError.InvalidSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Session token is not valid."))
    }
}
