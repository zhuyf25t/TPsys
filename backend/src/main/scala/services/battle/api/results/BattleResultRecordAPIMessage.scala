package services.battle.api.results

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.results.{BattleResultRepository, BattleResultStorage, BattleResultTable}
import services.battle.objects.{BattleAPIRequestError as BattleResultRecordDecodeError, BattleResultRecordCommand}
import services.battle.objects.apiTypes.results.BattleResultRecordRequest.given
import services.battle.objects.result.BattleResultRecord
import services.identity.objects.PlayerHandle
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage, APIWithTokenMessage}
import system.database.PostgresSupport
import system.objects.UserId
import system.policies.HandlePolicy

final case class BattleResultRecordAPIMessage(
  userId: UserId,
  command: BattleResultRecordCommand
) extends APIWithTokenMessage[BattleResultRecord],
      APIWithTokenContextMessage[BattleResultStorage, BattleResultRecord] {
  override def plan(connection: Connection): IO[BattleResultRecord] =
    for
      record <- BattleResultRecordAPIMessage.buildValidatedRecord(command)
      saved <- BattleResultRecordAPIMessage.saveToTable(connection, record)
    yield saved

  override def plan(storage: BattleResultStorage, connection: Connection): IO[BattleResultRecord] =
    for
      record <- BattleResultRecordAPIMessage.buildValidatedRecord(command)
      saved <- saveRecord(storage, connection, record)
    yield saved

  private def saveRecord(
    storage: BattleResultStorage,
    connection: Connection,
    record: BattleResultRecord
  ): IO[BattleResultRecord] =
    storage match {
      case BattleResultStorage.ConnectionTable =>
        BattleResultRecordAPIMessage.saveToTable(connection, record)
      case BattleResultStorage.Repository(resultRepository) =>
        BattleResultRecordAPIMessage.saveToRepository(resultRepository, record)
    }
}

object BattleResultRecordAPIMessage {
  given Decoder[BattleResultRecordAPIMessage] =
    Decoder.instance { cursor =>
      for
        userId <- APIMessage.injectedUserIdValue(cursor.value).left.map(message => DecodingFailure(message, cursor.history))
        command <- decodeRequest(cursor.value)
          .left
          .map(error => DecodingFailure(BattleResultRecordDecodeError.message(error), cursor.history))
      yield BattleResultRecordAPIMessage(userId, command)
    }

  private def buildValidatedRecord(command: BattleResultRecordCommand): IO[BattleResultRecord] =
    for
      handle <- validateRecordHandle(command.handle)
      record <- buildRecord(command, handle)
    yield record

  private def validateRecordHandle(handle: PlayerHandle): IO[PlayerHandle] =
    IO.fromEither {
      val trimmed = HandlePolicy.trim(handle.value)
      if trimmed.isEmpty then Left(APIMessageError.BadRequest("invalid_handle"))
      else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(APIMessageError.Forbidden("visitor_not_allowed"))
      else PlayerHandle.forLookup(trimmed).toRight(APIMessageError.BadRequest("invalid_handle"))
    }

  private def buildRecord(command: BattleResultRecordCommand, handle: PlayerHandle): IO[BattleResultRecord] =
    IO.pure(
      BattleResultRecord(
        battleId = command.battleId,
        handle = handle,
        displayName = command.displayName,
        finishedAt = command.finishedAt,
        finishedAtLabel = command.finishedAtLabel,
        durationMs = command.durationMs,
        score = command.score,
        placement = command.placement,
        survivalOutcome = command.survivalOutcome,
        ratingBefore = command.ratingBefore,
        ratingDelta = command.ratingDelta,
        ratingAfter = command.ratingAfter,
        resultLabel = command.resultLabel,
        modeLabel = command.modeLabel,
        mapLabel = command.mapLabel,
        highlightLine = command.highlightLine,
        playersLine = command.playersLine,
        timelineHint = command.timelineHint,
        currentLoadout = command.currentLoadout.flatMap(nonEmpty)
      )
    )

  private def saveToTable(connection: Connection, record: BattleResultRecord): IO[BattleResultRecord] =
    PostgresSupport.withTransactionIO(connection) {
      IO.blocking(BattleResultTable.save(connection, record))
    }

  private def saveToRepository(
    resultRepository: BattleResultRepository,
    record: BattleResultRecord
  ): IO[BattleResultRecord] =
    IO.blocking(resultRepository.save(record))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def decodeRequest(payload: Json): Either[BattleResultRecordDecodeError, BattleResultRecordCommand] =
    payload.as[BattleResultRecordCommand].left.map(resultRecordDecodeError)

  private def resultRecordDecodeError(error: Error): BattleResultRecordDecodeError =
    error match {
      case failure: DecodingFailure if failure.message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.InvalidBattleId) =>
        BattleResultRecordDecodeError.InvalidBattleId
      case failure: DecodingFailure if failure.message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.InvalidHandle) =>
        BattleResultRecordDecodeError.InvalidHandle
      case failure: DecodingFailure if failure.message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.VisitorNotAllowed) =>
        BattleResultRecordDecodeError.VisitorNotAllowed
      case _ =>
        BattleResultRecordDecodeError.BadJson
    }

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case failure: DecodingFailure =>
        decodeErrorFromMessage(failure.message)
      case _ =>
        APIMessageError.BadRequest("Request body must be a JSON object.")
    }

  private def decodeErrorFromMessage(message: String): APIMessageError =
    if message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.VisitorNotAllowed) then
      APIMessageError.Forbidden("visitor_not_allowed")
    else if message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.InvalidBattleId) then
      APIMessageError.BadRequest("invalid_battle_id")
    else if message == BattleResultRecordDecodeError.message(BattleResultRecordDecodeError.InvalidHandle) then
      APIMessageError.BadRequest("invalid_handle")
    else
      APIMessageError.BadRequest("Request body must be a JSON object.")

}
