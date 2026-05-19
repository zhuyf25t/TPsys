package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.BattleId
import slaydemo.backend.battle.services.BattleStateService
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleStateReadAPIMessage extends BackendAPIMessage {
  case Options
  case Head
  case MethodNotAllowed
  case MissingBattleId
  case Read(battleId: BattleId)
}

private[routes] final class BattleStateReadAPIMessagePlanner(
  battleStateService: BattleStateService
) extends BackendAPIMessagePlanner[BattleStateReadAPIMessage] {
  override def plan(message: BattleStateReadAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleStateReadAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleStateReadAPIMessage.Head =>
          BackendAPIResponse.empty(200)
        case BattleStateReadAPIMessage.MethodNotAllowed =>
          BattleAPIResponseSupport.unsupportedState
        case BattleStateReadAPIMessage.MissingBattleId =>
          BattleAPIResponseSupport.error(BattleRouteErrorMapper.invalidBattleId)
        case BattleStateReadAPIMessage.Read(battleId) =>
          battleStateService.currentState(battleId) match {
            case Right(state) =>
              BackendAPIResponse.json(200, BattleStateJson.renderState(state))
            case Left(error) =>
              BattleAPIResponseSupport.error(BattleRouteErrorMapper.stateRead(error))
          }
      }
    }
}

private[routes] object BattleStateReadAPIMessagePlanner {
  val MessageKey: String = "battlestatereadapi"

  def endpoint(battleStateService: BattleStateService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleStateReadAPIMessagePlanner(battleStateService))

  private def decode(request: BackendAPIRequest): BattleStateReadAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleStateReadAPIMessage.Options
      case "HEAD" =>
        BattleStateReadAPIMessage.Head
      case "GET" =>
        request.query.get("battleId").map(_.trim).filter(_.nonEmpty) match {
          case Some(battleId) => BattleStateReadAPIMessage.Read(BattleId(battleId))
          case None           => BattleStateReadAPIMessage.MissingBattleId
        }
      case _ =>
        BattleStateReadAPIMessage.MethodNotAllowed
    }
}
