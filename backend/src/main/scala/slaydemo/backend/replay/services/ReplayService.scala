package slaydemo.backend.replay.services

import slaydemo.backend.battle.objects.{BattleId, DurationMillis, EpochMillis, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.database.{InMemoryReplayRepository, ReplayRepository}
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayId, ReplayRecord}
import slaydemo.backend.replay.support.ReplayFrameJson
import slaydemo.backend.shared.policies.HandlePolicy

enum ReplayRecordError {
  case InvalidReplayId
  case InvalidFramesJson
}

enum ReplayCommentError {
  case InvalidReplayId
  case ReplayNotFound
  case InvalidAuthor
  case InvalidBody
}

final case class ReplayRecordCommand(
  replayId: ReplayId,
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Score,
  placement: Option[Int],
  durationMs: DurationMillis,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean,
  framesJson: String
)

final case class ReplayCommentCommand(
  replayId: ReplayId,
  authorHandle: PlayerHandle,
  body: String
)

trait ReplayService {
  def record(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord]
  def list(limit: Int): Vector[ReplayRecord]
  def load(replayId: ReplayId): Option[ReplayRecord]
  def addComment(command: ReplayCommentCommand): Either[ReplayCommentError, ReplayCommentRecord]
  def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord]
}

final class DefaultReplayService(repository: ReplayRepository, currentTimeMillis: () => Long) extends ReplayService {
  override def record(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord] = {
    if !ReplayIdentifierPolicy.isSafeReplayId(command.replayId) then Left(ReplayRecordError.InvalidReplayId)
    else
      ReplayFrameJson.normalize(command.framesJson) match {
        case Left(ReplayFrameJson.Error.InvalidFramesJson) =>
          Left(ReplayRecordError.InvalidFramesJson)
        case Right(replayFrames) =>
          val record = ReplayRecord(
            replayId = command.replayId,
            battleId = command.battleId,
            handle = command.handle,
            displayName = command.displayName,
            finishedAt = command.finishedAt,
            finishedAtLabel = command.finishedAtLabel,
            title = command.title,
            modeLabel = command.modeLabel,
            resultLabel = command.resultLabel,
            mapLabel = command.mapLabel,
            highlightLine = command.highlightLine,
            coverLabel = command.coverLabel,
            playersLine = command.playersLine,
            timelineHint = command.timelineHint,
            score = command.score,
            placement = command.placement,
            ratingBefore = None,
            ratingDelta = None,
            ratingAfter = None,
            durationMs = command.durationMs,
            aliveAtEnd = command.aliveAtEnd,
            thumbnailDataUrl = command.thumbnailDataUrl.flatMap(nonEmpty),
            currentLoadout = command.currentLoadout.flatMap(nonEmpty),
            frameCount = replayFrames.frameCount,
            playbackAvailable = command.playbackAvailable && replayFrames.playbackAvailable,
            framesJson = replayFrames.framesJson
          )
          Right(if isPlayable(record.handle) then repository.saveReplay(record) else record)
      }
  }

  override def list(limit: Int): Vector[ReplayRecord] = {
    val safeLimit = math.max(0, math.min(limit, 100))
    repository
      .listReplays(safeLimit * 3)
      .filter(record => isPlayable(record.handle))
      .take(safeLimit)
  }

  override def load(replayId: ReplayId): Option[ReplayRecord] =
    Option.when(ReplayIdentifierPolicy.isSafeReplayId(replayId))(replayId)
      .flatMap(repository.findReplayById)
      .filter(record => isPlayable(record.handle))

  override def addComment(command: ReplayCommentCommand): Either[ReplayCommentError, ReplayCommentRecord] =
    appendComment(command.replayId, command.authorHandle, command.body)

  override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] = {
    val safeLimit = math.max(0, math.min(limit, 100))
    load(replayId) match {
      case None =>
        Vector.empty
      case Some(_) =>
        repository
          .listComments(replayId, safeLimit * 3)
          .filter(comment => isPlayable(comment.authorHandle))
          .takeRight(safeLimit)
    }
  }

  private def appendComment(
    replayId: ReplayId,
    author: PlayerHandle,
    body: String
  ): Either[ReplayCommentError, ReplayCommentRecord] = {
    val normalizedBody = Option(body).getOrElse("").trim
    if !ReplayIdentifierPolicy.isSafeReplayId(replayId) then Left(ReplayCommentError.InvalidReplayId)
    else if !isPlayable(author) then Left(ReplayCommentError.InvalidAuthor)
    else if normalizedBody.isEmpty || normalizedBody.length > 1_000 then Left(ReplayCommentError.InvalidBody)
    else if load(replayId).isEmpty then Left(ReplayCommentError.ReplayNotFound)
    else {
      val comment = ReplayCommentRecord(
        id = repository.nextCommentId(),
        replayId = replayId,
        authorHandle = author,
        body = normalizedBody,
        createdAt = EpochMillis(currentTimeMillis())
      )
      Right(repository.saveComment(comment))
    }
  }

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)
}

object DefaultReplayService {
  def apply(repository: ReplayRepository, currentTimeMillis: () => Long): DefaultReplayService =
    new DefaultReplayService(repository, currentTimeMillis)
}

object InMemoryReplayService {
  def apply(): DefaultReplayService =
    DefaultReplayService(InMemoryReplayRepository(), () => System.currentTimeMillis())
}

object ReplayIdentifierPolicy {
  private val MaxReplayIdLength: Int = 200

  def isSafeReplayId(replayId: ReplayId): Boolean =
    isSafeIdentifier(replayId.value)

  def isSafeIdentifier(value: String): Boolean = {
    val trimmed = Option(value).getOrElse("").trim
    trimmed.nonEmpty &&
      trimmed.length <= MaxReplayIdLength &&
      trimmed.forall(char => char.isLetterOrDigit || char == '-' || char == '_' || char == '.' || char == '~')
  }
}
