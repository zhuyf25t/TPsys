package slaydemo.backend.battle.routes

import slaydemo.backend.battle.api.BattleCommandRequest
import slaydemo.backend.battle.services.BattleStateService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleCommandAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case BadJson(message: String)
  case BadRequest(error: BattleCommandRequestParseError)
  case Submit(request: BattleCommandRequest)
}

private[routes] final class BattleCommandAPIMessagePlanner(
  battleStateService: BattleStateService
) extends BackendAPIMessagePlanner[BattleCommandAPIMessage] {
  override def plan(message: BattleCommandAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleCommandAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleCommandAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedPost
        case BattleCommandAPIMessage.BadJson(message) =>
          BattleAPIResponseSupport.badJsonObject(message)
        case BattleCommandAPIMessage.BadRequest(error) =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.commandRequest(error))
        case BattleCommandAPIMessage.Submit(request) =>
          battleStateService.acceptCommand(request) match {
            case Right(accepted) =>
              BackendAPIResponse.json(200, BattleStateJson.renderCommandAccepted(accepted))
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.commandSubmit(error))
          }
      }
    }
}

private[routes] object BattleCommandAPIMessagePlanner {
  val MessageKey: String = "battlecommandapi"

  def endpoint(battleStateService: BattleStateService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleCommandAPIMessagePlanner(battleStateService))

  private def decode(request: BackendAPIRequest): BattleCommandAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleCommandAPIMessage.Options
      case "POST" =>
        BattleJsonObjectParser.parse(request.body) match {
          case Left(_) =>
            BattleCommandAPIMessage.BadJson("Request body must be a JSON object with supported primitive or object fields.")
          case Right(fields) =>
            BattleCommandRequestParser.parse(fields) match {
              case Left(error)           => BattleCommandAPIMessage.BadRequest(error)
              case Right(commandRequest) => BattleCommandAPIMessage.Submit(commandRequest)
            }
        }
      case _ =>
        BattleCommandAPIMessage.MethodNotAllowed
    }
}
