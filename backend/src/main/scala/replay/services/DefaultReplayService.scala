package slaydemo.backend.replay.services

import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

import slaydemo.backend.battle.rules.BattleRules
import slaydemo.backend.replay.api.{ReplayCatalogView, ReplayCommentSubmissionRequest, ReplayCommentView, ReplayDetailView, ReplaySubmissionRequest}
import slaydemo.backend.replay.database.ReplayRepository
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord}
import slaydemo.backend.replay.support.ReplayJsonSupport
import slaydemo.backend.shared.objects.{BattleId, ReplayId, UserId}

final class DefaultReplayService(repository: ReplayRepository) extends ReplayService {
  override def record(request: ReplaySubmissionRequest): Either[String, ReplayRecord] = {
    val replayId = normalizeIdentifier(request.replayId.value)
    val battleId = normalizeIdentifier(request.battleId.value)
    val handle = normalizeIdentifier(request.handle.value)

    if (replayId.isEmpty) {
      Left("invalid_replay_id")
    } else if (battleId.isEmpty) {
      Left("invalid_battle_id")
    } else if (handle.isEmpty) {
      Left("invalid_handle")
    } else if (isVisitorHandle(handle)) {
      Left("visitor_not_allowed")
    } else {
      ReplayJsonSupport.validateArrayString(request.framesJson) match {
        case Left(error) =>
          Left(error)
        case Right(_) =>
          val framesJson = normalizeFramesJson(request.framesJson)
          val storedFrameCount = countFramesJson(framesJson)
          val playableFrames = hasPlayableFramesJson(framesJson)
          val record = ReplayRecord(
            replayId = ReplayId(replayId),
            battleId = BattleId(battleId),
            handle = UserId(handle),
            displayName = request.displayName.trim,
            finishedAt = request.finishedAt,
            finishedAtLabel = request.finishedAtLabel.trim,
            title = request.title.trim,
            modeLabel = request.modeLabel.trim,
            resultLabel = request.resultLabel.trim,
            mapLabel = request.mapLabel.trim,
            highlightLine = request.highlightLine.trim,
            coverLabel = request.coverLabel.trim,
            playersLine = request.playersLine.trim,
            timelineHint = request.timelineHint.trim,
            score = request.score,
            placement = request.placement,
            durationMs = request.durationMs,
            aliveAtEnd = request.aliveAtEnd,
            thumbnailDataUrl = request.thumbnailDataUrl.map(_.trim).filter(_.nonEmpty),
            currentLoadout = request.currentLoadout.map(_.trim).filter(_.nonEmpty),
            frameCount = if (storedFrameCount > 0) storedFrameCount else if (playableFrames) request.frameCount.max(2) else 0,
            playbackAvailable = request.playbackAvailable && playableFrames,
            framesJsonB64 = Base64.getEncoder.encodeToString(framesJson.getBytes(StandardCharsets.UTF_8))
          )

          Right(repository.save(record))
      }
    }
  }

  override def list(limit: Int): Seq[ReplayCatalogView] = {
    val bounded = limit.max(1).min(50)
    repository.list(bounded).flatMap(record => hydrateCatalogView(record).toSeq)
  }

  override def load(replayId: ReplayId): Option[ReplayDetailView] = {
    repository.findById(replayId).flatMap(hydrateDetailView)
  }

  override def listComments(replayId: ReplayId, limit: Int): Seq[ReplayCommentView] = {
    repository.listComments(replayId, limit.max(1).min(100)).map(toCommentView)
  }

  override def addComment(request: ReplayCommentSubmissionRequest): Either[String, ReplayCommentView] = {
    val replayId = normalizeIdentifier(request.replayId.value)
    val authorHandle = normalizeIdentifier(request.authorHandle.value)
    val body = request.body.trim

    if (replayId.isEmpty) {
      Left("invalid_replay_id")
    } else if (repository.findById(ReplayId(replayId)).isEmpty) {
      Left("replay_not_found")
    } else if (authorHandle.isEmpty) {
      Left("invalid_author_handle")
    } else if (isVisitorHandle(authorHandle)) {
      Left("visitor_not_allowed")
    } else if (body.isEmpty) {
      Left("invalid_body")
    } else {
      val normalizedReplayId = ReplayId(replayId)
      val normalizedAuthorHandle = UserId(authorHandle)
      val createdAt = System.currentTimeMillis()
      val comment = ReplayCommentRecord(
        id = s"replay-comment-$createdAt-${java.util.UUID.randomUUID().toString.take(8)}",
        replayId = normalizedReplayId,
        authorHandle = normalizedAuthorHandle,
        body = body,
        createdAt = createdAt
      )
      Right(toCommentView(repository.saveComment(comment)))
    }
  }

  private def hydrateCatalogView(record: ReplayRecord): Option[ReplayCatalogView] = {
    validateStoredRecord(record) match {
      case Right(normalizedRecord) => Some(toCatalogView(normalizedRecord))
      case Left("visitor_not_allowed") =>
        None
      case Left(reason) =>
        purgeBadReplay(record.replayId, reason)
        None
    }
  }

  private def hydrateDetailView(record: ReplayRecord): Option[ReplayDetailView] = {
    validateStoredRecord(record) match {
      case Right(normalizedRecord) => Some(toDetailView(normalizedRecord))
      case Left("visitor_not_allowed") =>
        None
      case Left(reason) =>
        purgeBadReplay(record.replayId, reason)
        None
    }
  }

  private def validateStoredRecord(record: ReplayRecord): Either[String, ReplayRecord] = {
    val replayId = normalizeIdentifier(record.replayId.value)
    val battleId = normalizeIdentifier(record.battleId.value)
    val handle = normalizeIdentifier(record.handle.value)

    if (replayId.isEmpty) {
      Left("invalid_replay_id")
    } else if (battleId.isEmpty) {
      Left("invalid_battle_id")
    } else if (handle.isEmpty) {
      Left("invalid_handle")
    } else if (isVisitorHandle(handle)) {
      Left("visitor_not_allowed")
    } else {
      Right(
        record.copy(
          replayId = ReplayId(replayId),
          battleId = BattleId(battleId),
          handle = UserId(handle)
        )
      )
    }
  }

  private def purgeBadReplay(replayId: ReplayId, reason: String): Unit = {
    try {
      repository.delete(replayId)
      Console.err.println(s"[replay] deleted invalid replay ${replayId.value.trim}: $reason")
    } catch {
      case error: Throwable =>
        Console.err.println(
          s"[replay] failed to delete invalid replay ${replayId.value.trim}: $reason; ${error.getMessage}"
        )
    }
  }

  private def toCatalogView(record: ReplayRecord): ReplayCatalogView = {
    val framesJson = decodeFramesJson(record.framesJsonB64)
    val playableFrames = record.playbackAvailable && hasPlayableFramesJson(framesJson)
    val storedFrameCount = countFramesJson(framesJson)
    ReplayCatalogView(
      replayId = record.replayId,
      battleId = record.battleId,
      handle = record.handle,
      title = safeText(record.title),
      modeLabel = safeText(record.modeLabel),
      resultLabel = safeText(record.resultLabel),
      finishedAt = record.finishedAt,
      finishedAtLabel = safeText(record.finishedAtLabel),
      mapLabel = safeText(record.mapLabel),
      highlightLine = safeText(record.highlightLine),
      coverLabel = safeText(record.coverLabel),
      playersLine = safeText(record.playersLine),
      timelineHint = safeText(record.timelineHint),
      score = record.score,
      placement = record.placement,
      durationMs = record.durationMs,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = safeTextOption(record.thumbnailDataUrl),
      frameCount = if (storedFrameCount > 0) storedFrameCount else if (playableFrames) record.frameCount.max(2) else 0,
      playbackAvailable = playableFrames
    )
  }

  private def toDetailView(record: ReplayRecord): ReplayDetailView = {
    val framesJson = decodeFramesJson(record.framesJsonB64)
    val playableFrames = record.playbackAvailable && hasPlayableFramesJson(framesJson)
    val storedFrameCount = countFramesJson(framesJson)
    ReplayDetailView(
      replayId = record.replayId,
      battleId = record.battleId,
      handle = record.handle,
      displayName = safeText(record.displayName),
      finishedAt = record.finishedAt,
      finishedAtLabel = safeText(record.finishedAtLabel),
      title = safeText(record.title),
      modeLabel = safeText(record.modeLabel),
      resultLabel = safeText(record.resultLabel),
      mapLabel = safeText(record.mapLabel),
      highlightLine = safeText(record.highlightLine),
      coverLabel = safeText(record.coverLabel),
      playersLine = safeText(record.playersLine),
      timelineHint = safeText(record.timelineHint),
      score = record.score,
      placement = record.placement,
      durationMs = record.durationMs,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = safeTextOption(record.thumbnailDataUrl),
      currentLoadout = safeTextOption(record.currentLoadout),
      frameCount = if (storedFrameCount > 0) storedFrameCount else if (playableFrames) record.frameCount.max(2) else 0,
      playbackAvailable = playableFrames,
      framesJson = framesJson
    )
  }

  private def toCommentView(record: ReplayCommentRecord): ReplayCommentView = {
    ReplayCommentView(
      id = record.id,
      replayId = record.replayId,
      authorHandle = record.authorHandle,
      body = record.body,
      createdAt = record.createdAt
    )
  }

  private def normalizeFramesJson(value: String): String = {
    val trimmed = Option(value).map(_.trim).getOrElse("")
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) trimmed else "[]"
  }

  private def decodeFramesJson(value: String): String = {
    Try(new String(Base64.getDecoder.decode(Option(value).getOrElse("")), StandardCharsets.UTF_8)).toOption
      .map(normalizeFramesJson)
      .getOrElse("[]")
  }

  private def hasPlayableFramesJson(value: String): Boolean = {
    """"elapsedMs"""".r.findAllMatchIn(value).take(2).length == 2
  }

  private def countFramesJson(value: String): Int = {
    ReplayJsonSupport.countArrayElements(value).getOrElse(0)
  }

  private def safeText(value: String): String = Option(value).getOrElse("")

  private def safeTextOption(value: Option[String]): Option[String] = value.map(safeText).filter(_.nonEmpty)

  private def normalizeIdentifier(value: String): String = {
    val trimmed = safeText(value).trim
    if (isSafeIdentifier(trimmed)) trimmed else ""
  }

  private def isSafeIdentifier(value: String): Boolean = {
    value.nonEmpty && value.forall(ch => ch.isLetterOrDigit || ch == '-' || ch == '_' || ch == '.' || ch == '~')
  }

  private def isVisitorHandle(value: String): Boolean =
    BattleRules.isVisitorHandle(value)
}
