package services.battle.api.results

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Json}

import java.sql.Connection

import services.battle.database.results.BattleResultTable
import services.battle.objects.{BattleResultListQuery, BattleResultRecord}
import services.battle.objects.BattleResultListLimit
import services.battle.objects.apiTypes.results.BattleResultListResponse
import services.battle.objects.apiTypes.results.BattleResultListRequest.given
import system.api.{APIMessage, APIWithTokenMessage}
import system.objects.UserId
import system.policies.HandlePolicy

final case class BattleResultListAPIMessage(
  userId: UserId,
  query: BattleResultListQuery
) extends APIWithTokenMessage[BattleResultListResponse] {
  override def plan(connection: Connection): IO[BattleResultListResponse] =
    for
      records <- IO.blocking(BattleResultListAPIMessage.listFromTable(connection, query))
    yield BattleResultListResponse.fromRecords(records)
}

object BattleResultListAPIMessage {
  given Decoder[BattleResultListAPIMessage] =
    Decoder.instance { cursor =>
      APIMessage
        .injectedUserIdValue(cursor.value)
        .left
        .map(message => DecodingFailure(message, cursor.history))
        .map(userId => BattleResultListAPIMessage(userId, decodeRequest(cursor.value)))
    }

  private def listFromTable(connection: Connection, query: BattleResultListQuery): Vector[BattleResultRecord] =
    listRecords(query) { limit =>
      BattleResultTable.list(connection, query.handle, query.battleId, limit)
    }

  private def listRecords(
    query: BattleResultListQuery
  )(load: Int => Vector[BattleResultRecord]): Vector[BattleResultRecord] = {
    val safeLimit = math.max(0, math.min(query.limit.value, 100))
    query.handle match {
      case Some(owner) if !HandlePolicy.isPlayableIdentityHandle(owner.value) =>
        Vector.empty
      case _ =>
        load(safeLimit * 3)
          .filter(record => HandlePolicy.isPlayableIdentityHandle(record.handle.value))
          .take(safeLimit)
    }
  }

  private def decodeRequest(payload: Json): BattleResultListQuery =
    payload.as[BattleResultListQuery].getOrElse(BattleResultListQuery(None, None, BattleResultListLimit(25)))

}
