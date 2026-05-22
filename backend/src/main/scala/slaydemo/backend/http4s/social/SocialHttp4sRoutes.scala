package slaydemo.backend.http4s.social

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.Http4sCors.corsNoContent
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, decodeEntityBody, errorResponse, jsonOk, requestPath}
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

  def routes(service: FriendRequestService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if SocialRequestTarget.isFriendRequestRespondPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            respond(request, service)
          case _ =>
            errorResponse(socialApiError(SocialApiErrorCode.MethodNotAllowed))
        }
      case request if SocialRequestTarget.isFriendRequestPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            list(request, service)
          case Method.POST =>
            create(request, service)
          case _ =>
            errorResponse(socialApiError(SocialApiErrorCode.MethodNotAllowed))
        }
    }

  private def list(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    FriendRequestOwnerQuery.parseFromQuery(request.params) match {
      case Left(error) =>
        errorResponse(ownerApiError(error))
      case Right(ownerHandle) =>
        blocking(service.list(ownerHandle)).flatMap(records =>
          jsonOk(FriendRequestListResponse.fromRecords(records).asJson)
        )
    }

  private def create(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readCreateRequest(request).flatMap {
      case Left(SocialApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(socialApiError(SocialApiErrorCode.InvalidJsonObject))
      case Right(createRequest) =>
        createRequest.toCreateHandles match {
          case Left(error) =>
            errorResponse(createApiError(error))
          case Right(command) =>
            blocking(service.create(command.sourceHandle, command.targetHandle)).flatMap {
              case Right(result) =>
                jsonOk(FriendRequestCreateResponse.fromResult(result).asJson)
              case Left(error) =>
                errorResponse(socialApiError(SocialApiErrorCode.fromCreateServiceError(error)))
            }
        }
    }

  private def respond(request: Request[IO], service: FriendRequestService): IO[Response[IO]] =
    readRespondRequest(request).flatMap {
      case Left(SocialApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(socialApiError(SocialApiErrorCode.InvalidJsonObject))
      case Right(respondRequest) =>
        respondRequest.toRespondCommand match {
          case Left(error) =>
            errorResponse(respondParseApiError(error))
          case Right(command) =>
            blocking(service.respond(command.requestId, command.actorHandle, command.decision)).flatMap {
              case Right(result) =>
                jsonOk(FriendRequestRespondResponse.fromResult(result).asJson)
              case Left(error) =>
                errorResponse(socialApiError(SocialApiErrorCode.fromRespondServiceError(error)))
            }
        }
    }

  private def readCreateRequest(request: Request[IO]): IO[Either[SocialApiRequestDecodeError, FriendRequestCreateApiRequest]] =
    decodeEntityBody[SocialApiRequestDecodeError, FriendRequestCreateApiRequest](
      request,
      SocialApiRequestDecodeError.InvalidJsonObject
    )

  private def readRespondRequest(request: Request[IO]): IO[Either[SocialApiRequestDecodeError, FriendRequestRespondApiRequest]] =
    decodeEntityBody[SocialApiRequestDecodeError, FriendRequestRespondApiRequest](
      request,
      SocialApiRequestDecodeError.InvalidJsonObject
    )

  private def ownerApiError(error: SocialRouteHandleError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromOwnerError(error))

  private def createApiError(error: SocialRouteCreateError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromCreateRouteError(error))

  private def respondParseApiError(error: SocialRouteRespondError): HttpApiError =
    socialApiError(SocialApiErrorCode.fromRespondRouteError(error))

  private def socialApiError(code: SocialApiErrorCode): HttpApiError =
    apiError(
      status = socialApiStatus(code),
      code = SocialApiErrorCode.wireValue(code),
      message = SocialApiErrorCode.message(code)
    )

  private def socialApiStatus(code: SocialApiErrorCode): Status =
    code match {
      case SocialApiErrorCode.MethodNotAllowed  => Status.MethodNotAllowed
      case SocialApiErrorCode.VisitorNotAllowed => Status.Forbidden
      case SocialApiErrorCode.Forbidden         => Status.Forbidden
      case SocialApiErrorCode.RequestNotFound   => Status.NotFound
      case SocialApiErrorCode.InvalidJsonObject => Status.BadRequest
      case SocialApiErrorCode.MissingOwner      => Status.BadRequest
      case SocialApiErrorCode.InvalidOwner      => Status.BadRequest
      case SocialApiErrorCode.InvalidHandles    => Status.BadRequest
      case SocialApiErrorCode.InvalidDecision   => Status.BadRequest
      case SocialApiErrorCode.MissingFields     => Status.BadRequest
      case SocialApiErrorCode.InvalidActor      => Status.BadRequest
    }

}
