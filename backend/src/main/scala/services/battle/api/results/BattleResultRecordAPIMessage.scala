package services.battle.api.results

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.database.results.BattleResultTable
import services.battle.objects.{BattleAPIRequestError as BattleResultRecordDecodeError, BattleResultRecordCommand}
import services.battle.objects.apiTypes.results.BattleResultRecordRequest.given
import services.battle.objects.apiTypes.results.BattleResultRecordResponse
import services.battle.objects.result.BattleResultRecord
import services.identity.objects.PlayerHandle
import system.api.{APIMessage, APIMessageError, APIWithTokenMessage}
import system.database.PostgresSupport
import system.objects.UserId
import system.policies.HandlePolicy

final case class BattleResultRecordAPIMessage(
  userId: UserId,
  command: BattleResultRecordCommand
) extends APIWithTokenMessage[BattleResultRecordResponse] {
  override def plan(connection: Connection): IO[BattleResultRecordResponse] =
    for
      record <- BattleResultRecordAPIMessage.buildValidatedRecord(command)
      saved <- BattleResultRecordAPIMessage.saveToTable(connection, record)
    yield BattleResultRecordResponse.fromRecord(saved)
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

}
