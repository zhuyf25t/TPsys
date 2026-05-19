package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.TicketId
import slaydemo.backend.battle.services.{BattleQueueLeaveOutcome, BattleQueueService}
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleQueueLeaveAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case BadJson(message: String)
  case BadRequest(message: String)
  case Leave(ticketId: TicketId)
}

private[routes] final class BattleQueueLeaveAPIMessagePlanner(
  queueService: BattleQueueService
) extends BackendAPIMessagePlanner[BattleQueueLeaveAPIMessage] {
  override def plan(message: BattleQueueLeaveAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleQueueLeaveAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleQueueLeaveAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedPost
        case BattleQueueLeaveAPIMessage.BadJson(message) =>
          BattleAPIResponseSupport.badJsonObject(message)
        case BattleQueueLeaveAPIMessage.BadRequest(message) =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.queueLeaveParse(message))
        case BattleQueueLeaveAPIMessage.Leave(ticketId) =>
          val left = queueService.leave(ticketId) == BattleQueueLeaveOutcome.LeftQueue
          BackendAPIResponse.json(200, s"""{"left":$left}""")
      }
    }
}

private[routes] object BattleQueueLeaveAPIMessagePlanner {
  val MessageKey: String = "battlequeueleaveapi"

  def endpoint(queueService: BattleQueueService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleQueueLeaveAPIMessagePlanner(queueService))

  private def decode(request: BackendAPIRequest): BattleQueueLeaveAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleQueueLeaveAPIMessage.Options
      case "POST" =>
        BattleJsonObjectParser.parse(request.body) match {
          case Left(_) =>
            BattleQueueLeaveAPIMessage.BadJson("Request body must be a JSON object with supported primitive or object fields.")
          case Right(fields) =>
            BattleRouteRequestParsers.parseLeaveRequest(fields) match {
              case Left(message)  => BattleQueueLeaveAPIMessage.BadRequest(message)
              case Right(request) => BattleQueueLeaveAPIMessage.Leave(TicketId(request.ticketId))
            }
        }
      case _ =>
        BattleQueueLeaveAPIMessage.MethodNotAllowed
    }
}
