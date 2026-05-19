package slaydemo.backend.shared.routes

import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}
import slaydemo.backend.shared.services.HealthService

private[routes] enum HealthAPIMessage extends BackendAPIMessage {
  case Options
  case Head
  case Read
  case MethodNotAllowed
}

private[routes] final class HealthAPIMessagePlanner(service: HealthService) extends BackendAPIMessagePlanner[HealthAPIMessage] {
  override def plan(message: HealthAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case HealthAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case HealthAPIMessage.Head =>
          BackendAPIResponse.empty(200)
        case HealthAPIMessage.Read =>
          BackendAPIResponse.json(200, HealthRouteJsonRenderer.render(service.current))
        case HealthAPIMessage.MethodNotAllowed =>
          BackendAPIResponse.json(405, """{"error":"method_not_allowed"}""")
      }
    }
}

private[routes] object HealthAPIMessagePlanner {
  val MessageKey: String = "healthapi"

  def endpoint(service: HealthService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new HealthAPIMessagePlanner(service))

  private def decode(request: BackendAPIRequest): HealthAPIMessage =
    request.method match {
      case "OPTIONS" => HealthAPIMessage.Options
      case "HEAD"    => HealthAPIMessage.Head
      case "GET"     => HealthAPIMessage.Read
      case _         => HealthAPIMessage.MethodNotAllowed
    }
}
