package slaydemo.backend.battle.services

import slaydemo.backend.battle.database.{BattleResultRepository, InMemoryBattleResultRepository}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.shared.policies.HandlePolicy

trait BattleResultService {
  def record(command: BattleResultRecordCommand): BattleResultRecord
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
  placement: Option[Int],
  aliveAtEnd: Boolean,
  ratingBefore: Rating,
  ratingDelta: Int,
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
  override def record(command: BattleResultRecordCommand): BattleResultRecord = {
    val record = BattleResultRecord(
      battleId = command.battleId,
      handle = command.handle,
      displayName = command.displayName,
      finishedAt = command.finishedAt,
      finishedAtLabel = command.finishedAtLabel,
      durationMs = command.durationMs,
      score = command.score,
      placement = command.placement,
      aliveAtEnd = command.aliveAtEnd,
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
    if isPlayable(record.handle) then repository.save(record) else record
  }

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
