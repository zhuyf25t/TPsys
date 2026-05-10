package slaydemo.backend.replay.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import slaydemo.backend.shared.database.AtomicFileWrite
import slaydemo.backend.replay.objects.{
  ReplayCommentId,
  ReplayCommentRecord,
  ReplayId,
  ReplayRecord
}

final class FileReplayRepository(storagePath: Path) extends ReplayRepository {
  private val lock = Object()
  private var replaysById: Map[ReplayId, ReplayRecord] = Map.empty
  private var commentsById: Map[ReplayCommentId, ReplayCommentRecord] = Map.empty
  private var nextCommentNumber: Long = 1L

  loadFromDisk()

  override def saveReplay(record: ReplayRecord): ReplayRecord = {
    lock.synchronized {
      replaysById = replaysById.updated(record.replayId, record)
      persist()
    }
    record
  }

  override def listReplays(limit: Int): Vector[ReplayRecord] =
    lock.synchronized {
      replaysById.values.toVector
    }.sortWith(ReplayRepositoryOrderingRules.replaysRecentFirst)
      .take(math.max(0, limit))

  override def findReplayById(replayId: ReplayId): Option[ReplayRecord] =
    lock.synchronized {
      replaysById.get(replayId)
    }

  override def nextCommentId(): ReplayCommentId =
    lock.synchronized {
      val id = ReplayCommentId(f"comment-$nextCommentNumber%06d")
      nextCommentNumber += 1L
      id
    }

  override def saveComment(record: ReplayCommentRecord): ReplayCommentRecord = {
    lock.synchronized {
      commentsById = commentsById.updated(record.id, record)
      advanceNextCommentNumber(record.id)
      persist()
    }
    record
  }

  override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
    lock.synchronized {
      commentsById.values.toVector
    }.filter(_.replayId == replayId)
      .sortWith(ReplayRepositoryOrderingRules.fileCommentsChronological)
      .takeRight(math.max(0, limit))

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          val payload = ReplayFileJsonParser.parse(raw)

          replaysById = payload.records
            .map { replay =>
              val settlements = payload.settlementsByReplay.getOrElse(replay.replayId, Vector.empty)
              replay.replayId -> replay.copy(settlements = settlements.sortWith(ReplayRepositoryOrderingRules.settlements))
            }
            .toMap

          commentsById = payload.comments
            .map(comment => comment.id -> comment)
            .toMap

          commentsById.keys.foreach(advanceNextCommentNumber)
        }
      }
    }

  private def persist(): Unit = {
    val records = replaysById.values.toVector.sortWith(ReplayRepositoryOrderingRules.replaysRecentFirst)
    val comments = commentsById.values.toVector.sortWith(ReplayRepositoryOrderingRules.fileCommentsChronological)
    val settlements = records.flatMap(record => record.settlements.map(record.replayId -> _))
    val payload = ReplayFileJsonRenderer.renderPayload(records, comments, settlements)
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

  private def advanceNextCommentNumber(id: ReplayCommentId): Unit =
    parseNumericCommentId(id).foreach { number =>
      nextCommentNumber = math.max(nextCommentNumber, number + 1L)
    }

  private def parseNumericCommentId(id: ReplayCommentId): Option[Long] = {
    val prefix = "comment-"
    val value = id.value.trim
    Option
      .when(value.startsWith(prefix) && value.drop(prefix.length).forall(_.isDigit))(value.drop(prefix.length).toLong)
  }

}

object FileReplayRepository {
  def apply(storagePath: Path): FileReplayRepository =
    new FileReplayRepository(storagePath)
}
