package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.TicketId
import slaydemo.backend.battle.services.BattleQueueService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleQueueStatusAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case MissingTicketId
  case Status(ticketId: TicketId)
}

private[routes] final class BattleQueueStatusAPIMessagePlanner(
  queueService: BattleQueueService
) extends BackendAPIMessagePlanner[BattleQueueStatusAPIMessage] {
  override def plan(message: BattleQueueStatusAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleQueueStatusAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleQueueStatusAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedGet
        case BattleQueueStatusAPIMessage.MissingTicketId =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.missingTicketId)
        case BattleQueueStatusAPIMessage.Status(ticketId) =>
          queueService.status(ticketId) match {
            case Right(snapshot) =>
              BackendAPIResponse.json(200, BattleQueueRoomJsonRenderer.renderQueueSnapshot(snapshot))
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.queueStatus(error))
          }
      }
    }
}

private[routes] object BattleQueueStatusAPIMessagePlanner {
  val MessageKey: String = "battlequeuestatusapi"

  def endpoint(queueService: BattleQueueService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleQueueStatusAPIMessagePlanner(queueService))

  private def decode(request: BackendAPIRequest): BattleQueueStatusAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleQueueStatusAPIMessage.Options
      case "GET" =>
        request.query.get("ticketId").map(_.trim).filter(_.nonEmpty) match {
          case Some(ticketId) => BattleQueueStatusAPIMessage.Status(TicketId(ticketId))
          case None           => BattleQueueStatusAPIMessage.MissingTicketId
        }
      case _ =>
        BattleQueueStatusAPIMessage.MethodNotAllowed
    }
}
