package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.{BattleQueueJoinAuthorizationService, BattleQueueJoinCommand, BattleQueueService}
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleQueueJoinAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case BadJson(message: String)
  case BadCommand(message: String)
  case InvalidHandle
  case MissingSession
  case Join(command: BattleQueueJoinCommand)
}

private[routes] final class BattleQueueJoinAPIMessagePlanner(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService
) extends BackendAPIMessagePlanner[BattleQueueJoinAPIMessage] {
  override def plan(message: BattleQueueJoinAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleQueueJoinAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleQueueJoinAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedPost
        case BattleQueueJoinAPIMessage.BadJson(message) =>
          BattleAPIResponseSupport.badJsonObject(message)
        case BattleQueueJoinAPIMessage.BadCommand(message) =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.joinCommandParse(message))
        case BattleQueueJoinAPIMessage.InvalidHandle =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.joinCommandParse(BattleQueueJoinCommandParseError.InvalidHandle))
        case BattleQueueJoinAPIMessage.MissingSession =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.joinCommandParse(BattleQueueJoinCommandParseError.MissingSession))
        case BattleQueueJoinAPIMessage.Join(command) =>
          joinAuthorizationService.authorize(command) match {
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.joinAuthorization(error))
            case Right(()) =>
              BackendAPIResponse.json(200, BattleQueueRoomJsonRenderer.renderQueueSnapshot(queueService.join(command)))
          }
      }
    }
}

private[routes] object BattleQueueJoinAPIMessagePlanner {
  val MessageKey: String = "battlequeuejoinapi"

  def endpoint(
    queueService: BattleQueueService,
    joinAuthorizationService: BattleQueueJoinAuthorizationService
  ): BackendAPIEndpoint =
    BackendAPIEndpoint(
      MessageKey,
      decode,
      new BattleQueueJoinAPIMessagePlanner(queueService, joinAuthorizationService)
    )

  private def decode(request: BackendAPIRequest): BattleQueueJoinAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleQueueJoinAPIMessage.Options
      case "POST" =>
        BattleJsonObjectParser.parse(request.body) match {
          case Left(_) =>
            BattleQueueJoinAPIMessage.BadJson("Request body must be a JSON object with supported primitive or object fields.")
          case Right(fields) =>
            BattleJoinCommandParser.parse(fields) match {
              case Left(message) =>
                BattleQueueJoinAPIMessage.BadCommand(message)
              case Right(Left(BattleQueueJoinCommandParseError.InvalidHandle)) =>
                BattleQueueJoinAPIMessage.InvalidHandle
              case Right(Left(BattleQueueJoinCommandParseError.MissingSession)) =>
                BattleQueueJoinAPIMessage.MissingSession
              case Right(Right(command)) =>
                BattleQueueJoinAPIMessage.Join(command)
            }
        }
      case _ =>
        BattleQueueJoinAPIMessage.MethodNotAllowed
    }
}
