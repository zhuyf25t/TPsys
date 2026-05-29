package services.battle.microservices.results.services

import cats.effect.IO

import java.sql.Connection

import services.battle.microservices.results.database.BattleResultTable
import services.battle.microservices.results.objects.result.{
  BattleResultListQuery,
  BattleResultRecord,
  BattleResultRecordCommand,
  BattleResultRecordValidationError
}
import services.identity.objects.PlayerHandle
import system.database.PostgresSupport
import system.policies.HandlePolicy

object BattleResultService {
  def list(connection: Connection, query: BattleResultListQuery): IO[Vector[BattleResultRecord]] =
    IO.blocking {
      listPlayableRecords(query) { limit =>
        BattleResultTable.list(connection, query.handle, query.battleId, limit)
      }
    }

  def record(
    connection: Connection,
    command: BattleResultRecordCommand
  ): IO[Either[BattleResultRecordValidationError, BattleResultRecord]] =
    validateRecordHandle(command.handle) match {
      case Left(error) =>
        IO.pure(Left(error))
      case Right(handle) =>
        val record = buildRecord(command, handle)
        PostgresSupport
          .withTransactionIO(connection)(IO.blocking(BattleResultTable.save(connection, record)))
          .map(Right(_))
    }

  private def listPlayableRecords(
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

  private def validateRecordHandle(handle: PlayerHandle): Either[BattleResultRecordValidationError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(handle.value)
    if trimmed.isEmpty then Left(BattleResultRecordValidationError.InvalidHandle)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(BattleResultRecordValidationError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(BattleResultRecordValidationError.InvalidHandle)
  }

  private def buildRecord(command: BattleResultRecordCommand, handle: PlayerHandle): BattleResultRecord =
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

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
