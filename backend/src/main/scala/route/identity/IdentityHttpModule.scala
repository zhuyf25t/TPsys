package route.identity

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.identity.api.{
  IdentityAccountsAPIMessage,
  IdentityAPIMessageSupport,
  IdentityCurrentAPIMessage,
  IdentityRegisterAPIMessage,
  IdentitySessionAPIMessage
}
import services.identity.api.{IdentityAccountsResponse, IdentityAuthResponse}
import services.identity.services.IdentityService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object IdentityHttpModule {
  def routes(service: IdentityService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          IdentityService,
          IdentityRegisterAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          IdentityService,
          IdentitySessionAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          IdentityService,
          IdentityCurrentAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          IdentityService,
          IdentityAccountsAPIMessage,
          IdentityAccountsResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject)
      )
    ) <+> IdentityHttp4sRoutes.routes(service)
}
