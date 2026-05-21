package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, typedApiError, withCors}
import slaydemo.backend.social.objects.apiTypes.{
  FriendRequestCreateApiRequest,
  FriendRequestCreateResponse,
  FriendRequestListResponse,
  FriendRequestOwnerQuery,
  FriendRequestRespondApiRequest,
  FriendRequestRespondResponse,
  SocialApiErrorCode,
  SocialApiRequestDecodeError,
  SocialRequestTarget,
  SocialRouteCreateError,
  SocialRouteHandleError,
  SocialRouteRespondError
}
import slaydemo.backend.social.services.FriendRequestService

private[http4s] object SocialHttp4sRoutes {
  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(service: FriendRequestService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if SocialRequestTarget.isFriendRequestRespondPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            respond(request, service)
          case _ =>
            IO.pure(apiError(socialApiError(SocialApiErrorCode.MethodNotAllowed)))
        }
      case request if SocialRequestTarget.isFriendRequestPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            list(request, service)
          case Method.POST =>
            create(request, service)
          case _ =>
            IO.pure(apiError(socialApiError(SocialApiErrorCode.MethodNotAllowed)))
        }
    }

  private def list(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    FriendRequestOwnerQuery.parseFromQuery(request.params) match {
      case Left(error) =>
        IO.pure(apiError(ownerApiError(error)))
      case Right(ownerHandle) =>
        blocking(service.list(ownerHandle)).flatMap(records =>
          Ok(FriendRequestListResponse.fromRecords(records).asJson).map(withCors)
        )
    }

  private def create(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readCreateRequest(request).flatMap {
      case Left(SocialApiRequestDecodeError.InvalidJsonObject) =>
        IO.pure(apiError(socialApiError(SocialApiErrorCode.InvalidJsonObject)))
      case Right(createRequest) =>
        createRequest.toCreateHandles match {
          case Left(error) =>
            IO.pure(apiError(createApiError(error)))
          case Right(command) =>
            blocking(service.create(command.sourceHandle, command.targetHandle)).flatMap {
              case Right(result) =>
                Ok(FriendRequestCreateResponse.fromResult(result).asJson).map(withCors)
              case Left(error) =>
                IO.pure(apiError(socialApiError(SocialApiErrorCode.fromCreateServiceError(error))))
            }
        }
    }

  private def respond(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readRespondRequest(request).flatMap {
      case Left(SocialApiRequestDecodeError.InvalidJsonObject) =>
        IO.pure(apiError(socialApiError(SocialApiErrorCode.InvalidJsonObject)))
      case Right(respondRequest) =>
        respondRequest.toRespondCommand match {
          case Left(error) =>
            IO.pure(apiError(respondParseApiError(error)))
          case Right(command) =>
            blocking(service.respond(command.requestId, command.actorHandle, command.decision)).flatMap {
              case Right(result) =>
                Ok(FriendRequestRespondResponse.fromResult(result).asJson).map(withCors)
              case Left(error) =>
                IO.pure(apiError(socialApiError(SocialApiErrorCode.fromRespondServiceError(error))))
            }
        }
    }

  private def readCreateRequest(request: Request[IO]): IO[Either[SocialApiRequestDecodeError, FriendRequestCreateApiRequest]] =
    request
      .as[FriendRequestCreateApiRequest]
      .attempt
      .map(_.left.map(_ => SocialApiRequestDecodeError.InvalidJsonObject))

  private def readRespondRequest(request: Request[IO]): IO[Either[SocialApiRequestDecodeError, FriendRequestRespondApiRequest]] =
    request
      .as[FriendRequestRespondApiRequest]
      .attempt
      .map(_.left.map(_ => SocialApiRequestDecodeError.InvalidJsonObject))

  private def ownerApiError(error: SocialRouteHandleError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromOwnerError(error))

  private def createApiError(error: SocialRouteCreateError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromCreateRouteError(error))

  private def respondParseApiError(error: SocialRouteRespondError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromRespondRouteError(error))

  private def socialApiError(code: SocialApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = SocialApiErrorCode.statusCode(code),
      code = SocialApiErrorCode.wireValue(code),
      message = SocialApiErrorCode.message(code)
    )

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

}
