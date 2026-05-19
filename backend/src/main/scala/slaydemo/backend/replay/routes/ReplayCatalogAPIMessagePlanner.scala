package slaydemo.backend.replay.routes

import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] enum ReplayCatalogAPIMessage extends BackendAPIMessage {
  case Options
  case Head
  case List(rawQuery: String)
  case MethodNotAllowed
}

private[routes] final class ReplayCatalogAPIMessagePlanner(service: ReplayService)
    extends BackendAPIMessagePlanner[ReplayCatalogAPIMessage] {
  override def plan(message: ReplayCatalogAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case ReplayCatalogAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case ReplayCatalogAPIMessage.Head =>
          BackendAPIResponse.empty(200)
        case ReplayCatalogAPIMessage.List(rawQuery) =>
          val limit = ReplayRouteTargetParsers.limit(rawQuery, default = 25)
          BackendAPIResponse.json(
            200,
            ReplayRouteJsonRenderer.renderCatalog(service.list(limit), ReplayRouteTargetParsers.replayHandleFromQuery(rawQuery))
          )
        case ReplayCatalogAPIMessage.MethodNotAllowed =>
          jsonError(ReplayRouteErrorMapper.methodNotAllowed)
      }
    }

  private def jsonError(error: ReplayRouteError): BackendAPIResponse =
    BackendAPIResponse.json(
      error.status,
      s"""{"error":${jsonString(error.message)},"code":${jsonString(error.code)}}"""
    )

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}

private[routes] object ReplayCatalogAPIMessagePlanner {
  val MessageKey: String = "replaycatalogapi"

  def endpoint(service: ReplayService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new ReplayCatalogAPIMessagePlanner(service))

  private def decode(request: BackendAPIRequest): ReplayCatalogAPIMessage =
    request.method match {
      case "OPTIONS" => ReplayCatalogAPIMessage.Options
      case "HEAD"    => ReplayCatalogAPIMessage.Head
      case "GET"     => ReplayCatalogAPIMessage.List(request.rawQuery)
      case _         => ReplayCatalogAPIMessage.MethodNotAllowed
    }
}
