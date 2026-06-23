package route.replay

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response, Status}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import services.replay.objects.ReplayId
import services.replay.api.{
  ReplayAPIMessageSupport,
  ReplayApiCodec,
  ReplayApiErrorCode,
  ReplayReadAPIParser,
  ReplayCatalogAPIMessage,
  ReplayCatalogResponse,
  ReplayCatalogTarget,
  ReplayCommentCreateAPIMessage,
  ReplayCommentWrapperResponse,
  ReplayCommentsAPIMessage,
  ReplayCommentsResponse,
  ReplayDetailAPIMessage,
  ReplayRecordAPIMessage,
  ReplayDetailResponse
}
import services.replay.services.ReplayService
import system.api.{APIMessageError, APIMessageRouter, RegisteredAPIMessage}
import system.api.APIMessageRouter.{APIMessageAlias, APIMessageRequestAlias}
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object ReplayHttp4sRoutes {
  def catalogRoutes(service: ReplayService): HttpRoutes[IO] =
    postAliasRoutes(service) <+> getAliasRoutes(service) <+> compatibilityRoutes

  private def postAliasRoutes(service: ReplayService): HttpRoutes[IO] =
    APIMessageRouter.dynamicAliasRoutes(
      apiMessages = replayPostApiMessages(service),
      aliasForRequest = replayPostAlias(service),
      errorHandler = replayAPIMessageErrorResponse
    )

  private def replayPostApiMessages(service: ReplayService): List[RegisteredAPIMessage] =
    List(
      apiWithContext[
        ReplayService,
        ReplayRecordAPIMessage,
        ReplayDetailResponse
      ](service, ReplayAPIMessageSupport.invalidJsonObject),
      apiWithContext[
        ReplayService,
        ReplayCommentCreateAPIMessage,
        ReplayCommentWrapperResponse
      ](service, ReplayAPIMessageSupport.invalidJsonObject)
    )

  private def getAliasRoutes(service: ReplayService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = replayGetApiMessages(service),
      aliasForRequest = replayGetAlias(service),
      errorHandler = replayAPIMessageErrorResponse
    )

  private def replayGetApiMessages(service: ReplayService): List[RegisteredAPIMessage] =
    List(
      apiWithContext[
        ReplayService,
        ReplayCatalogAPIMessage,
        ReplayCatalogResponse
      ](service, ReplayAPIMessageSupport.invalidJsonObject),
      apiWithContext[
        ReplayService,
        ReplayDetailAPIMessage,
        ReplayDetailResponse
      ](service, ReplayAPIMessageSupport.invalidJsonObject),
      apiWithContext[
        ReplayService,
        ReplayCommentsAPIMessage,
        ReplayCommentsResponse
      ](service, ReplayAPIMessageSupport.invalidJsonObject)
    )

  private def replayPostAlias(service: ReplayService)(request: Request[IO]): Option[APIMessageAlias] =
    catalogTarget(request).flatMap {
      case ReplayCatalogTarget.Collection =>
        Some(
          APIMessageAlias.fromContextMessage[ReplayService, ReplayRecordAPIMessage, ReplayDetailResponse](
            context = service,
            responseTransform = createdResponse
          )
        )
      case ReplayCatalogTarget.Comments(replayId) =>
        Some(
          APIMessageAlias.fromContextMessage[ReplayService, ReplayCommentCreateAPIMessage, ReplayCommentWrapperResponse](
            context = service,
            transformMessage = _.withReplayId(replayId),
            responseTransform = createdResponse
          )
        )
      case ReplayCatalogTarget.Detail(_) | ReplayCatalogTarget.InvalidReplayId =>
        None
    }

  private def replayGetAlias(service: ReplayService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    if request.method != Method.GET then None
    else
      catalogTarget(request).flatMap {
        case ReplayCatalogTarget.Collection =>
          Some(
            APIMessageRequestAlias.fromContextMessage[ReplayService, ReplayCatalogAPIMessage, ReplayCatalogResponse](
              context = service,
              message = ReplayReadAPIParser.catalogMessageFromQuery(request.params),
              responseTransform = withCors
            )
          )
        case ReplayCatalogTarget.Detail(replayId) =>
          Some(
            APIMessageRequestAlias.fromContextMessage[ReplayService, ReplayDetailAPIMessage, ReplayDetailResponse](
              context = service,
              message = ReplayReadAPIParser.detailMessageFromPathAndQuery(replayId, request.params),
              responseTransform = withCors
            )
          )
        case ReplayCatalogTarget.Comments(replayId) =>
          Some(
            APIMessageRequestAlias.fromContextMessage[ReplayService, ReplayCommentsAPIMessage, ReplayCommentsResponse](
              context = service,
              message = ReplayReadAPIParser.commentsMessageFromPathAndQuery(replayId, request.params),
              responseTransform = withCors
            )
          )
        case ReplayCatalogTarget.InvalidReplayId =>
          None
      }

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request @ CatalogRequest(target) =>
        target match {
          case ReplayCatalogTarget.Collection =>
            handleCollection(request)
          case ReplayCatalogTarget.Detail(_) =>
            handleDetail(request)
          case ReplayCatalogTarget.Comments(_) =>
            handleComments(request)
          case ReplayCatalogTarget.InvalidReplayId =>
            errorResponse(replayApiError(ReplayApiErrorCode.InvalidReplayId))
        }
    }

  private def handleCollection(request: Request[IO]): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def handleDetail(request: Request[IO]): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def handleComments(request: Request[IO]): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def replayAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(replayApiError(ReplayApiErrorCode.BadJsonObject))
    case error: APIMessageError =>
      errorResponse(replayApiError(replayApiErrorCode(error)))
  }

  private def createdResponse(response: Response[IO]): Response[IO] =
    withCors(response.withStatus(Status.Created))

  private def catalogTarget(request: Request[IO]): Option[ReplayCatalogTarget] =
    ReplayApiCodec.catalogTarget(requestPath(request))

  private def replayApiError(code: ReplayApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = ReplayApiErrorCode.statusCode(code),
      code = ReplayApiErrorCode.wireValue(code),
      message = ReplayApiErrorCode.message(code)
    )

  private def replayApiErrorCode(error: APIMessageError): ReplayApiErrorCode =
    ReplayApiErrorCode.values
      .find(code =>
        error.getMessage == ReplayApiErrorCode.message(code) ||
          error.getMessage == ReplayApiErrorCode.wireValue(code)
      )
      .getOrElse(ReplayApiErrorCode.BadJsonObject)

  private object CatalogRequest {
    def unapply(request: Request[IO]): Option[ReplayCatalogTarget] =
      catalogTarget(request)
  }
}
