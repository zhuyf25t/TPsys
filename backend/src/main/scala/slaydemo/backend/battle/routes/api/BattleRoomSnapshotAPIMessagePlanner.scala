package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.RoomId
import slaydemo.backend.battle.services.BattleQueueService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleRoomSnapshotAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case MissingRoomId
  case Snapshot(roomId: RoomId)
}

private[routes] final class BattleRoomSnapshotAPIMessagePlanner(
  queueService: BattleQueueService
) extends BackendAPIMessagePlanner[BattleRoomSnapshotAPIMessage] {
  override def plan(message: BattleRoomSnapshotAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleRoomSnapshotAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleRoomSnapshotAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedGet
        case BattleRoomSnapshotAPIMessage.MissingRoomId =>
          BattleAPIResponseSupport.error(
            BattleRouteErrorMapper
              .roomSnapshotTarget(BattleRoomSnapshotTarget.MissingRoomId)
              .getOrElse(BattleRouteErrorMapper.roomRouteNotFound)
          )
        case BattleRoomSnapshotAPIMessage.Snapshot(roomId) =>
          queueService.roomSnapshot(roomId) match {
            case Right(snapshot) =>
              BackendAPIResponse.json(200, BattleQueueRoomJsonRenderer.renderRoomSnapshot(snapshot))
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.room(error))
          }
      }
    }
}

private[routes] object BattleRoomSnapshotAPIMessagePlanner {
  val MessageKey: String = "battleroomsnapshotapi"

  def endpoint(queueService: BattleQueueService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleRoomSnapshotAPIMessagePlanner(queueService))

  private def decode(request: BackendAPIRequest): BattleRoomSnapshotAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleRoomSnapshotAPIMessage.Options
      case "GET" =>
        request.query.get("roomId").map(_.trim).filter(_.nonEmpty) match {
          case Some(roomId) => BattleRoomSnapshotAPIMessage.Snapshot(RoomId(roomId))
          case None         => BattleRoomSnapshotAPIMessage.MissingRoomId
        }
      case _ =>
        BattleRoomSnapshotAPIMessage.MethodNotAllowed
    }
}
