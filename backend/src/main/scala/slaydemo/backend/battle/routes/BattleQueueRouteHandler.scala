package slaydemo.backend.battle.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.battle.objects.TicketId
import slaydemo.backend.battle.services.{BattleQueueJoinAuthorizationService, BattleQueueLeaveOutcome, BattleQueueService}
import slaydemo.backend.battle.routes.BattleRouteHttpSupport.{handleGet, handlePost, jsonError, readJsonObject}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] final class BattleQueueRouteHandler(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService
) {
  def join(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, BattleRouteErrorMapper.badJsonObject(message))
        case Right(fields) =>
          BattleJoinCommandParser.parse(fields) match {
            case Left(message) =>
              jsonError(exchange, BattleRouteErrorMapper.joinCommandParse(message))
            case Right(Left(BattleQueueJoinCommandParseError.InvalidHandle)) =>
              jsonError(exchange, BattleRouteErrorMapper.joinCommandParse(BattleQueueJoinCommandParseError.InvalidHandle))
            case Right(Left(BattleQueueJoinCommandParseError.MissingSession)) =>
              jsonError(exchange, BattleRouteErrorMapper.joinCommandParse(BattleQueueJoinCommandParseError.MissingSession))
            case Right(Right(command)) =>
              joinAuthorizationService.authorize(command) match {
                case Left(error) =>
                  jsonError(exchange, BattleRouteErrorMapper.joinAuthorization(error))
                case Right(()) =>
                  val snapshot = queueService.join(command)
                  HttpRouteSupport.sendJson(exchange, 200, BattleQueueRoomJsonRenderer.renderQueueSnapshot(snapshot))
              }
          }
      }
    }

  def status(exchange: HttpExchange): Unit =
    handleGet(exchange) {
      BattleRouteRequestParsers.statusTicketId(exchange.getRequestURI.getRawQuery) match {
        case None =>
          jsonError(exchange, BattleRouteErrorMapper.missingTicketId)
        case Some(ticketId) =>
          queueService.status(ticketId) match {
            case Right(snapshot) =>
              HttpRouteSupport.sendJson(exchange, 200, BattleQueueRoomJsonRenderer.renderQueueSnapshot(snapshot))
            case Left(error) =>
              jsonError(exchange, BattleRouteErrorMapper.queueStatus(error))
          }
      }
    }

  def leave(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readJsonObject(exchange) match {
        case Left(message) =>
          jsonError(exchange, BattleRouteErrorMapper.badJsonObject(message))
        case Right(fields) =>
          BattleRouteRequestParsers.parseLeaveRequest(fields) match {
            case Left(message) =>
              jsonError(exchange, BattleRouteErrorMapper.queueLeaveParse(message))
            case Right(request) =>
              val left = queueService.leave(TicketId(request.ticketId)) == BattleQueueLeaveOutcome.LeftQueue
              HttpRouteSupport.sendJson(exchange, 200, s"""{"left":$left}""")
          }
      }
    }
}

private[routes] object BattleQueueRouteHandler {
  def apply(
    queueService: BattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService
  ): BattleQueueRouteHandler =
    new BattleQueueRouteHandler(queueService, joinAuthorizationService)
}
