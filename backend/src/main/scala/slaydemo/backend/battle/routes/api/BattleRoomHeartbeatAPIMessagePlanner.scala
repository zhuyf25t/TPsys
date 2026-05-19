package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.{BattleQueueService, RealtimeRoomHeartbeatCommand}
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleRoomHeartbeatAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case BadJson(message: String)
  case Heartbeat(command: RealtimeRoomHeartbeatCommand)
}

private[routes] final class BattleRoomHeartbeatAPIMessagePlanner(
  queueService: BattleQueueService
) extends BackendAPIMessagePlanner[BattleRoomHeartbeatAPIMessage] {
  override def plan(message: BattleRoomHeartbeatAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleRoomHeartbeatAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleRoomHeartbeatAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedPost
        case BattleRoomHeartbeatAPIMessage.BadJson(message) =>
          BattleAPIResponseSupport.badJsonObject(message)
        case BattleRoomHeartbeatAPIMessage.Heartbeat(command) =>
          queueService.heartbeat(command) match {
            case Right(snapshot) =>
              BackendAPIResponse.json(200, BattleQueueRoomJsonRenderer.renderRoomSnapshot(snapshot))
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.room(error))
          }
      }
    }
}

private[routes] object BattleRoomHeartbeatAPIMessagePlanner {
  val MessageKey: String = "battleroomheartbeatapi"

  def endpoint(queueService: BattleQueueService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleRoomHeartbeatAPIMessagePlanner(queueService))

  private def decode(request: BackendAPIRequest): BattleRoomHeartbeatAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleRoomHeartbeatAPIMessage.Options
      case "POST" =>
        BattleJsonObjectParser.parse(request.body) match {
          case Left(_) =>
            BattleRoomHeartbeatAPIMessage.BadJson("Request body must be a JSON object with supported primitive or object fields.")
          case Right(fields) =>
            BattleRoomHeartbeatAPIMessage.Heartbeat(
              BattleRoomRouteParsers.heartbeatCommand(None, request.rawQuery, fields)
            )
        }
      case _ =>
        BattleRoomHeartbeatAPIMessage.MethodNotAllowed
    }
}
