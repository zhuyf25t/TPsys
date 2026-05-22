package services.forum.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import services.battle.objects.EpochMillis
import services.forum.objects.{
  ForumReplyId,
  ForumReplyRecord,
  ForumReplyVoteUpdateError,
  ForumTopicId,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoterKey,
  ForumVotes
}
import services.identity.objects.PlayerHandle
import system.database.AtomicFileWrite

final class FileForumRepository(storagePath: Path) extends ForumRepository {
  private val lock = Object()
  private var topicsById: Map[ForumTopicId, ForumTopicRecord] = Map.empty
  private var idCounters: ForumFileIdCounters = ForumFileIdCounters.initial

  loadFromDisk()

  override def nextTopicId(): ForumTopicId =
    lock.synchronized {
      val (id, nextCounters) = idCounters.allocateTopicId
      idCounters = nextCounters
      id
    }

  override def nextReplyId(): ForumReplyId =
    lock.synchronized {
      val (id, nextCounters) = idCounters.allocateReplyId
      idCounters = nextCounters
      id
    }

  override def listTopics(): Vector[ForumTopicRecord] =
    ForumTopicOrderingRules.sortRecentFirst(
      lock.synchronized {
        topicsById.values.toVector
      }
    )

  override def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId)
    }

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = {
    lock.synchronized {
      topicsById = topicsById.updated(topic.id, topic)
      idCounters = idCounters.afterTopicId(topic.id)
      topic.replies.foreach(reply => idCounters = idCounters.afterReplyId(reply.id))
      persist()
    }
    topic
  }

  override def setTopicVote(
    topicId: ForumTopicId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId) match {
        case None =>
          Left(ForumVoteMutationError.TopicNotFound)
        case Some(topic) =>
          val updated = ForumTopicRecord.setVote(topic, authorHandle, vote, updatedAt)
          topicsById = topicsById.updated(topicId, updated)
          persist()
          Right(updated)
      }
    }

  override def setReplyVote(
    topicId: ForumTopicId,
    replyId: ForumReplyId,
    authorHandle: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumVoteMutationError, ForumTopicRecord] =
    lock.synchronized {
      topicsById.get(topicId) match {
        case None =>
          Left(ForumVoteMutationError.TopicNotFound)
        case Some(topic) =>
          ForumTopicRecord.setReplyVote(topic, replyId, authorHandle, vote, updatedAt) match {
            case Left(ForumReplyVoteUpdateError.ReplyNotFound) =>
              Left(ForumVoteMutationError.ReplyNotFound)
            case Right(updated) =>
              topicsById = topicsById.updated(topicId, updated)
              persist()
              Right(updated)
          }
      }
    }

  private def loadFromDisk(): Unit =
    lock.synchronized {
      if Files.exists(storagePath) then {
        val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
        if raw.nonEmpty then {
          val repliesByTopic = ForumFileJsonParser.parseReplies(raw)
            .groupMap(_._1)(_._2)
            .view
            .mapValues(_.sortBy(reply => (reply.createdAt.value, reply.id.value)))
            .toMap

          val topicVotesByTopic = ForumFileJsonParser.parseTopicVotes(raw)
            .groupMap(_._1) { case (_, voter, choice) => voter -> choice }
            .view
            .mapValues(values => ForumVotes(values.toMap))
            .toMap

          val replyVotesByReply = ForumFileJsonParser.parseReplyVotes(raw)
            .groupMap(_._1) { case (_, voter, choice) => voter -> choice }
            .view
            .mapValues(values => ForumVotes(values.toMap))
            .toMap

          topicsById = ForumFileJsonParser.parseTopics(raw)
            .map { topic =>
              val replies = repliesByTopic
                .getOrElse(topic.id, Vector.empty)
                .map(reply => reply.copy(votes = replyVotesByReply.getOrElse(reply.id, ForumVotes.empty)))
              val votes = topicVotesByTopic.getOrElse(topic.id, ForumVotes.empty)
              val aggregate = topic.copy(replies = replies, votes = votes)
              idCounters = idCounters.afterTopicId(aggregate.id)
              aggregate.replies.foreach(reply => idCounters = idCounters.afterReplyId(reply.id))
              aggregate.id -> aggregate
            }
            .toMap
        }
      }
    }

  private def persist(): Unit = {
    val topics = ForumTopicOrderingRules.sortRecentFirst(topicsById.values.toVector)
    val payload = ForumFileJsonRenderer.renderPayload(topics)
    AtomicFileWrite.writeUtf8(storagePath, payload)
  }

}

object FileForumRepository {
  def apply(storagePath: Path): FileForumRepository =
    new FileForumRepository(storagePath)
}
