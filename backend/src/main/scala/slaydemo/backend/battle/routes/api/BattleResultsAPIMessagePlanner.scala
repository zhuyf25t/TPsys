package slaydemo.backend.battle.routes

import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError, BattleResultService}
import slaydemo.backend.shared.api.{BackendAPIEndpoint, BackendAPIMessage, BackendAPIMessagePlanner, BackendAPIRequest, BackendAPIResponse, BackendIO}

private[routes] enum BattleResultsAPIMessage extends BackendAPIMessage {
  case Options
  case Head
  case MethodNotAllowed
  case List(request: BattleResultListRequest)
  case EmptyList
  case BadJson
  case BadRecord(error: BattleResultRecordCommandParseError)
  case Record(command: BattleResultRecordCommand)
}

private[routes] final class BattleResultsAPIMessagePlanner(
  service: BattleResultService
) extends BackendAPIMessagePlanner[BattleResultsAPIMessage] {
  override def plan(message: BattleResultsAPIMessage): BackendIO[BackendAPIResponse] =
    BackendIO.delay {
      message match {
        case BattleResultsAPIMessage.Options =>
          BackendAPIResponse.empty(204)
        case BattleResultsAPIMessage.Head =>
          BackendAPIResponse.empty(200)
        case BattleResultsAPIMessage.MethodNotAllowed =>
          BackendAPIResponse.jsonError(405, "method_not_allowed", "Only GET, POST, HEAD, and OPTIONS are supported.")
        case BattleResultsAPIMessage.EmptyList =>
          BackendAPIResponse.json(200, BattleResultRouteJsonRenderer.renderRecords(Vector.empty))
        case BattleResultsAPIMessage.List(request) =>
          BackendAPIResponse.json(
            200,
            BattleResultRouteJsonRenderer.renderRecords(
              service.list(handle = request.handle, battleId = request.battleId, limit = request.limit)
            )
          )
        case BattleResultsAPIMessage.BadJson =>
          BackendAPIResponse.jsonError(400, "bad_request", "Request body must be a JSON object.")
        case BattleResultsAPIMessage.BadRecord(error) =>
          recordParseError(error)
        case BattleResultsAPIMessage.Record(command) =>
          service.record(command) match {
            case Right(record) =>
              BackendAPIResponse.json(201, BattleResultRouteJsonRenderer.renderRecord(record))
            case Left(BattleResultRecordError.InvalidHandle) =>
              BackendAPIResponse.jsonError(400, "invalid_handle", "invalid_handle")
            case Left(BattleResultRecordError.VisitorNotAllowed) =>
              BackendAPIResponse.jsonError(403, "visitor_not_allowed", "visitor_not_allowed")
          }
      }
    }

  private def recordParseError(error: BattleResultRecordCommandParseError): BackendAPIResponse =
    error match {
      case BattleResultRecordCommandParseError.InvalidBattleId =>
        BackendAPIResponse.jsonError(400, "invalid_battle_id", "invalid_battle_id")
      case BattleResultRecordCommandParseError.InvalidHandle =>
        BackendAPIResponse.jsonError(400, "invalid_handle", "invalid_handle")
      case BattleResultRecordCommandParseError.VisitorNotAllowed =>
        BackendAPIResponse.jsonError(403, "visitor_not_allowed", "visitor_not_allowed")
    }
}

private[routes] object BattleResultsAPIMessagePlanner {
  val MessageKey: String = "battleresultsapi"

  def endpoint(service: BattleResultService): BackendAPIEndpoint =
    BackendAPIEndpoint(MessageKey, decode, new BattleResultsAPIMessagePlanner(service))

  private def decode(request: BackendAPIRequest): BattleResultsAPIMessage =
    request.method match {
      case "OPTIONS" =>
        BattleResultsAPIMessage.Options
      case "HEAD" =>
        BattleResultsAPIMessage.Head
      case "GET" =>
        BattleResultCommandParsers.parseListRequest(request.rawQuery) match {
          case BattleResultListRequestParseResult.EmptyResults =>
            BattleResultsAPIMessage.EmptyList
          case BattleResultListRequestParseResult.Query(listRequest) =>
            BattleResultsAPIMessage.List(listRequest)
        }
      case "POST" =>
        ResultJsonObjectParser.parse(request.body) match {
          case Left(_) =>
            BattleResultsAPIMessage.BadJson
          case Right(fields) =>
            BattleResultCommandParsers.parseRecordCommand(fields) match {
              case Right(command) => BattleResultsAPIMessage.Record(command)
              case Left(error)    => BattleResultsAPIMessage.BadRecord(error)
            }
        }
      case _ =>
        BattleResultsAPIMessage.MethodNotAllowed
    }
}
