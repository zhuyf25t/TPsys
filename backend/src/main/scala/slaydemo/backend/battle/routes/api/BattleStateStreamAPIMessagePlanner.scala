package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.BattleId
import slaydemo.backend.battle.services.BattleStateService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleStateStreamAPIMessage extends BackendAPIMessage {
  case Options
  case MethodNotAllowed
  case MissingBattleId
  case Stream(battleId: BattleId)
}

private[routes] final class BattleStateStreamAPIMessagePlanner(
  battleStateService: BattleStateService
) extends BackendAPIMessagePlanner[BattleStateStreamAPIMessage] {
  override def plan(message: BattleStateStreamAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleStateStreamAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleStateStreamAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedState
        case BattleStateStreamAPIMessage.MissingBattleId =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.invalidBattleId)
        case BattleStateStreamAPIMessage.Stream(battleId) =>
          battleStateService.currentState(battleId) match {
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.stateRead(error))
            case Right(state) =>
              BackendAPIResponse.stream { exchange =>
                val headers = exchange.getResponseHeaders
                headers.set("Content-Type", "text/event-stream; charset=utf-8")
                headers.set("Cache-Control", "no-cache")
                headers.set("Connection", "keep-alive")
                exchange.sendResponseHeaders(200, 0)
                BattleStateStreamWriter.writeStateFrames(
                  output = exchange.getResponseBody,
                  battleId = battleId,
                  initialState = state,
                  nextState = battleId => battleStateService.currentState(battleId).toOption
                )
              }
          }
      }
    }
}

private[routes] object BattleStateStreamAPIMessagePlanner {
  val MessageKey: String = "battlestatestreamapi"

  def endpoint(battleStateService: BattleStateService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleStateStreamAPIMessagePlanner(battleStateService))

  private def decode(request: BackendAPIRequest): BattleStateStreamAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleStateStreamAPIMessage.Options
      case "GET" =>
        request.query.get("battleId").map(_.trim).filter(_.nonEmpty) match {
          case Some(battleId) => BattleStateStreamAPIMessage.Stream(BattleId(battleId))
          case None           => BattleStateStreamAPIMessage.MissingBattleId
        }
      case _ =>
        BattleStateStreamAPIMessage.MethodNotAllowed
    }
}
