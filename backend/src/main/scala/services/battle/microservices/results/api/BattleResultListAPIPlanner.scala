package services.battle.microservices.results.api

import cats.effect.IO

import java.sql.Connection

import services.battle.microservices.results.api.results.{BattleResultListResponse, BattleResultResponseMapping}
import services.battle.microservices.results.objects.result.BattleResultListQuery
import services.battle.microservices.results.services.BattleResultService

object BattleResultListAPIPlanner {
  def plan(connection: Connection, message: BattleResultListAPIMessage): IO[BattleResultListResponse] =
    for
      records <- BattleResultService.list(connection, toQuery(message))
      response <- BattleResultResponseMapping.fromRecords(records)
    yield response

  private def toQuery(message: BattleResultListAPIMessage): BattleResultListQuery =
    BattleResultListQuery(
      handle = message.handle,
      battleId = message.battleId,
      limit = message.limit
    )
}
