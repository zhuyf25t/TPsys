package slaydemo.backend.battle.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.objects.RealtimeRoomSnapshot
import slaydemo.backend.battle.services.{BattleQueueService, BattleRoomError}
import slaydemo.backend.battle.routes.BattleRouteHttpSupport.{jsonError, readJsonObject}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] final class BattleRoomRouteHandler(queueService: BattleQueueService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          handleRoomSnapshot(exchange)
        case "POST" =>
          handleRoomHeartbeat(exchange)
        case _ =>
          jsonError(exchange, BattleRouteErrorMapper.unsupportedRooms)
      }
    } finally {
      exchange.close()
    }
  }

  private def handleRoomSnapshot(exchange: HttpExchange): Unit = {
    BattleRoomRouteParsers.snapshotTarget(exchange.getRequestURI.getPath, exchange.getRequestURI.getRawQuery) match {
      case target @ (BattleRoomSnapshotTarget.MissingRoomId | BattleRoomSnapshotTarget.RouteNotFound) =>
        BattleRouteErrorMapper.roomSnapshotTarget(target).foreach(error => jsonError(exchange, error))
      case BattleRoomSnapshotTarget.Room(roomId) =>
        sendRoomSnapshotResult(exchange, queueService.roomSnapshot(roomId))
    }
  }

  private def handleRoomHeartbeat(exchange: HttpExchange): Unit = {
    BattleRoomRouteParsers.heartbeatRoute(exchange.getRequestURI.getPath) match {
      case None =>
        jsonError(exchange, BattleRouteErrorMapper.roomRouteNotFound)
      case Some(pathRoomId) =>
        readJsonObject(exchange) match {
          case Left(message) =>
            jsonError(exchange, BattleRouteErrorMapper.badJsonObject(message))
          case Right(fields) =>
            val command = BattleRoomRouteParsers.heartbeatCommand(pathRoomId, exchange.getRequestURI.getRawQuery, fields)
            sendRoomSnapshotResult(exchange, queueService.heartbeat(command))
        }
    }
  }

  private def sendRoomSnapshotResult(
    exchange: HttpExchange,
    result: Either[BattleRoomError, RealtimeRoomSnapshot]
  ): Unit =
    result match {
      case Right(snapshot) =>
        HttpRouteSupport.sendJson(exchange, 200, BattleQueueRoomJsonRenderer.renderRoomSnapshot(snapshot))
      case Left(error) =>
        jsonError(exchange, BattleRouteErrorMapper.room(error))
    }
}

private[routes] object BattleRoomRouteHandler {
  def apply(queueService: BattleQueueService): BattleRoomRouteHandler =
    new BattleRoomRouteHandler(queueService)
}
