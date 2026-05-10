package slaydemo.backend.battle.services

import slaydemo.backend.battle.database.{BattleResultRepository, InMemoryBattleResultRepository}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.shared.policies.HandlePolicy

enum BattleResultRecordError {
  case InvalidHandle
  case VisitorNotAllowed
}

trait BattleResultService {
  def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord]
  def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord]
}

final case class BattleResultRecordCommand(
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
  durationMs: DurationMillis,
  score: Score,
  placement: Option[BattlePlacement],
  survivalOutcome: BattleSurvivalOutcome,
  ratingBefore: Rating,
  ratingDelta: RatingDelta,
  ratingAfter: Rating,
  resultLabel: String,
  modeLabel: String,
  mapLabel: String,
  highlightLine: String,
  playersLine: String,
  timelineHint: String,
  currentLoadout: Option[String]
)

final class DefaultBattleResultService(repository: BattleResultRepository) extends BattleResultService {
  override def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord] =
    validateRecordHandle(command.handle).map { handle =>
      val record = buildRecord(command, handle)
      repository.save(record)
    }

  private def buildRecord(command: BattleResultRecordCommand, handle: PlayerHandle): BattleResultRecord =
    val record = BattleResultRecord(
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
      resultLabel = BattleResultLabel.fromWire(command.resultLabel),
      modeLabel = BattleModeLabel.fromWire(command.modeLabel),
      mapLabel = BattleMapLabel.fromWire(command.mapLabel),
      highlightLine = BattleHighlightLine.fromWire(command.highlightLine),
      playersLine = BattlePlayersLine.fromWire(command.playersLine),
      timelineHint = BattleTimelineHint.fromWire(command.timelineHint),
      currentLoadout = command.currentLoadout.flatMap(nonEmpty)
    )
    record

  override def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord] = {
    val safeLimit = math.max(0, math.min(limit, 100))
    handle match {
      case Some(owner) if !isPlayable(owner) =>
        Vector.empty
      case _ =>
        repository
          .list(handle, battleId, safeLimit * 3)
          .filter(record => isPlayable(record.handle))
          .take(safeLimit)
    }
  }

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def validateRecordHandle(handle: PlayerHandle): Either[BattleResultRecordError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(handle.value)
    if trimmed.isEmpty then Left(BattleResultRecordError.InvalidHandle)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(BattleResultRecordError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(BattleResultRecordError.InvalidHandle)
  }

  private def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)
}

object DefaultBattleResultService {
  def apply(repository: BattleResultRepository): DefaultBattleResultService =
    new DefaultBattleResultService(repository)
}

object InMemoryBattleResultService {
  def apply(): DefaultBattleResultService =
    DefaultBattleResultService(InMemoryBattleResultRepository())
}
