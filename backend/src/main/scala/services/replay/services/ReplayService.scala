package services.replay.services

import cats.effect.IO

import services.battle.microservices.actors.objects.player.BattleSurvivalOutcome
import services.battle.microservices.results.objects.result.BattlePlacement
import services.battle.microservices.actors.objects.player.Score
import services.battle.objects.{BattleId, DurationMillis, EpochMillis}
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.database.{InMemoryReplayRepository, ReplayRepository}
import services.replay.objects.{ReplayCommentRecord, ReplayFrameCount, ReplayId, ReplayPlaybackAvailability, ReplayRecord}
import services.replay.support.ReplayFrameJson
import system.policies.HandlePolicy

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
  placement: Option[BattlePlacement],
  durationMs: DurationMillis,
  survivalOutcome: BattleSurvivalOutcome,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: ReplayFrameCount,
  requestedPlaybackAvailability: ReplayPlaybackAvailability,
  framesJson: String
)

final case class ReplayCommentCommand(
  replayId: ReplayId,
  authorHandle: PlayerHandle,
  body: String
)

trait ReplayService {
  def record(command: ReplayRecordCommand): IO[Either[ReplayRecordError, ReplayRecord]]
  def list(limit: Int): IO[Vector[ReplayRecord]]
  def load(replayId: ReplayId): IO[Option[ReplayRecord]]
  def addComment(command: ReplayCommentCommand): IO[Either[ReplayCommentError, ReplayCommentRecord]]
  def listComments(replayId: ReplayId, limit: Int): IO[Vector[ReplayCommentRecord]]
}

final class DefaultReplayService(repository: ReplayRepository, currentTimeMillis: () => Long) extends ReplayService {
  override def record(command: ReplayRecordCommand): IO[Either[ReplayRecordError, ReplayRecord]] =
    IO.blocking {
      recordSync(command)
    }

  override def list(limit: Int): IO[Vector[ReplayRecord]] =
    IO.blocking {
      val safeLimit = math.max(0, math.min(limit, 100))
      repository
        .listReplays(safeLimit * 3)
        .filter(record => isPlayable(record.handle))
        .take(safeLimit)
    }

  override def load(replayId: ReplayId): IO[Option[ReplayRecord]] =
    IO.blocking(loadRecord(replayId))

  override def addComment(command: ReplayCommentCommand): IO[Either[ReplayCommentError, ReplayCommentRecord]] =
    IO.blocking(appendComment(command.replayId, command.authorHandle, command.body))

  override def listComments(replayId: ReplayId, limit: Int): IO[Vector[ReplayCommentRecord]] =
    IO.blocking {
      val safeLimit = math.max(0, math.min(limit, 100))
      loadRecord(replayId) match {
        case None =>
          Vector.empty
        case Some(_) =>
          repository
            .listComments(replayId, safeLimit * 3)
            .filter(comment => isPlayable(comment.authorHandle))
            .takeRight(safeLimit)
      }
    }

  private def recordSync(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord] =
    if !ReplayIdentifierPolicy.isSafeReplayId(command.replayId) then Left(ReplayRecordError.InvalidReplayId)
    else
      ReplayFrameJson.normalize(command.framesJson) match {
        case Left(ReplayFrameJson.Error.InvalidFramesJson) =>
          Left(ReplayRecordError.InvalidFramesJson)
        case Right(replayFrames) =>
          val record = ReplayRecordFactory.fromCommand(command, replayFrames)
          Right(if isPlayable(record.handle) then repository.saveReplay(record) else record)
      }

  private def loadRecord(replayId: ReplayId): Option[ReplayRecord] =
    Option.when(ReplayIdentifierPolicy.isSafeReplayId(replayId))(replayId)
      .flatMap(repository.findReplayById)
      .filter(record => isPlayable(record.handle))

  private def appendComment(
    replayId: ReplayId,
    author: PlayerHandle,
    body: String
  ): Either[ReplayCommentError, ReplayCommentRecord] = {
    val normalizedBody = Option(body).getOrElse("").trim
    if !ReplayIdentifierPolicy.isSafeReplayId(replayId) then Left(ReplayCommentError.InvalidReplayId)
    else if !isPlayable(author) then Left(ReplayCommentError.InvalidAuthor)
    else if normalizedBody.isEmpty || normalizedBody.length > 1_000 then Left(ReplayCommentError.InvalidBody)
    else if loadRecord(replayId).isEmpty then Left(ReplayCommentError.ReplayNotFound)
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
