package services.battle.api.results

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.results.{BattleResultRepository, BattleResultStorage, BattleResultTable}
import services.battle.objects.{BattleResultList, BattleResultListQuery, BattleResultRecord}
import services.battle.objects.BattleResultListLimit
import services.battle.objects.apiTypes.results.BattleResultListRequest.given
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage, APIWithTokenMessage}
import system.objects.UserId
import system.policies.HandlePolicy

final case class BattleResultListAPIMessage(
  userId: UserId,
  query: BattleResultListQuery
) extends APIWithTokenMessage[BattleResultList],
      APIWithTokenContextMessage[BattleResultStorage, BattleResultList] {
  override def plan(connection: Connection): IO[BattleResultList] =
    for
      records <- IO.blocking(BattleResultListAPIMessage.listFromTable(connection, query))
    yield BattleResultList(records)

  override def plan(storage: BattleResultStorage, connection: Connection): IO[BattleResultList] =
    for
      records <- listRecords(storage, connection, query)
    yield BattleResultList(records)

  private def listRecords(
    storage: BattleResultStorage,
    connection: Connection,
    query: BattleResultListQuery
  ): IO[Vector[BattleResultRecord]] =
    storage match {
      case BattleResultStorage.ConnectionTable =>
        IO.blocking(BattleResultListAPIMessage.listFromTable(connection, query))
      case BattleResultStorage.Repository(resultRepository) =>
        BattleResultListAPIMessage.listFromRepository(resultRepository, query)
    }

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

  private def listFromRepository(
    resultRepository: BattleResultRepository,
    query: BattleResultListQuery
  ): IO[Vector[BattleResultRecord]] =
    IO.blocking {
      listRecords(query) { limit =>
        resultRepository.list(query.handle, query.battleId, limit)
      }
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

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case _ =>
        APIMessageError.BadRequest("Invalid battle result list request.")
    }

}
