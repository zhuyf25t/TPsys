package slaydemo.backend.forum.services

import java.util.UUID

import slaydemo.backend.forum.database.ForumRepository
import slaydemo.backend.forum.objects.*
import slaydemo.backend.shared.objects.ThreadId

final class DefaultForumService(repository: ForumRepository) extends ForumService {
  override def listTopics(viewerHandle: Option[String]): Vector[ForumTopicView] = {
    repository.listTopics().toVector.map(topic => toTopicView(topic, viewerHandle))
  }

  override def loadTopic(threadId: ThreadId, viewerHandle: Option[String]): Option[ForumTopicView] = {
    repository.findTopic(threadId).map(topic => toTopicView(topic, viewerHandle))
  }

  override def createTopic(title: String, body: String, tag: String, authorHandle: String): Either[String, ForumTopicView] = {
    val normalizedTitle = title.trim
    val normalizedBody = body.trim
    val normalizedTag = tag.trim
    val normalizedAuthor = authorHandle.trim

    if (normalizedTitle.isEmpty) {
      Left("invalid_title")
    } else if (normalizedBody.isEmpty) {
      Left("invalid_body")
    } else if (normalizedTag.isEmpty) {
      Left("invalid_tag")
    } else if (normalizedAuthor.isEmpty) {
      Left("invalid_author")
    } else {
      val now = System.currentTimeMillis()
      val topic = ForumTopicRecord(
        threadId = ThreadId(s"topic-${UUID.randomUUID().toString}"),
        title = normalizedTitle,
        body = normalizedBody,
        tag = normalizedTag,
        authorHandle = normalizedAuthor,
        createdAt = now,
        updatedAt = now
      )

      repository.saveTopic(topic)
      Right(toTopicView(topic, Some(normalizedAuthor)))
    }
  }

  override def addReply(threadId: ThreadId, body: String, authorHandle: String): Either[String, ForumTopicView] = {
    val normalizedBody = body.trim
    val normalizedAuthor = authorHandle.trim

    if (normalizedBody.isEmpty) {
      Left("invalid_body")
    } else if (normalizedAuthor.isEmpty) {
      Left("invalid_author")
    } else {
      repository.findTopic(threadId) match {
        case Some(topic) =>
          val now = System.currentTimeMillis()
          val reply = ForumReplyRecord(
            replyId = s"reply-${UUID.randomUUID().toString}",
            threadId = threadId,
            authorHandle = normalizedAuthor,
            body = normalizedBody,
            createdAt = now
          )

          repository.saveReply(reply)
          repository.saveTopic(topic.copy(updatedAt = now))
          Right(toTopicView(topic.copy(updatedAt = now), Some(normalizedAuthor)))
        case None =>
          Left("topic_not_found")
      }
    }
  }

  override def setVote(threadId: ThreadId, authorHandle: String, vote: Option[ForumVoteChoice]): Either[String, ForumTopicView] = {
    val normalizedAuthor = authorHandle.trim
    if (normalizedAuthor.isEmpty) {
      Left("invalid_author")
    } else {
      repository.findTopic(threadId) match {
        case Some(topic) =>
          val now = System.currentTimeMillis()
          val changed = repository.upsertVote(threadId, normalizedAuthor, vote, now)
          if (changed) {
            repository.saveTopic(topic.copy(updatedAt = now))
            Right(toTopicView(topic.copy(updatedAt = now), Some(normalizedAuthor)))
          } else {
            Right(toTopicView(topic, Some(normalizedAuthor)))
          }
        case None =>
          Left("topic_not_found")
      }
    }
  }

  override def setReplyVote(
    threadId: ThreadId,
    replyId: String,
    authorHandle: String,
    vote: Option[ForumVoteChoice]
  ): Either[String, ForumTopicView] = {
    val normalizedReplyId = replyId.trim
    val normalizedAuthor = authorHandle.trim

    if (normalizedReplyId.isEmpty) {
      Left("reply_not_found")
    } else if (normalizedAuthor.isEmpty) {
      Left("invalid_author")
    } else {
      repository.findTopic(threadId) match {
        case Some(topic) =>
          val replyExists = repository.listReplies(threadId).exists(_.replyId == normalizedReplyId)
          if (!replyExists) {
            Left("reply_not_found")
          } else {
            val now = System.currentTimeMillis()
            val changed = repository.upsertReplyVote(normalizedReplyId, normalizedAuthor, vote, now)
            if (changed) {
              val updatedTopic = topic.copy(updatedAt = now)
              repository.saveTopic(updatedTopic)
              Right(toTopicView(updatedTopic, Some(normalizedAuthor)))
            } else {
              Right(toTopicView(topic, Some(normalizedAuthor)))
            }
          }
        case None =>
          Left("topic_not_found")
      }
    }
  }

  private def toTopicView(topic: ForumTopicRecord, viewerHandle: Option[String]): ForumTopicView = {
    val replies = repository.listReplies(topic.threadId).toVector
    val votes = repository.listVotes(topic.threadId).toVector
    val viewerVote = findViewerVote(
      votes.map(vote => vote.authorHandle -> vote.vote),
      viewerHandle
    )

    ForumTopicView(
      id = topic.threadId.value,
      title = topic.title,
      author = topic.authorHandle,
      excerpt = buildExcerpt(topic.body),
      tag = topic.tag,
      replies = replies.size,
      updatedAt = topic.updatedAt,
      createdAt = topic.createdAt,
      body = topic.body,
      replyItems = replies.sortBy(_.createdAt).map { reply =>
        val replyVotes = repository.listReplyVotes(reply.replyId).toVector
        ForumReplyView(
          id = reply.replyId,
          author = reply.authorHandle,
          body = reply.body,
          publishedAt = reply.createdAt,
          viewerVote = findViewerVote(
            replyVotes.map(vote => vote.authorHandle -> vote.vote),
            viewerHandle
          ),
          score = scoreVoteChoices(replyVotes.map(_.vote))
        )
      },
      viewerVote = viewerVote,
      score = scoreVoteChoices(votes.map(_.vote))
    )
  }

  private def findViewerVote(
    votes: Seq[(String, ForumVoteChoice)],
    viewerHandle: Option[String]
  ): Option[String] = {
    viewerHandle
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .flatMap { normalizedViewer =>
        votes.find { case (authorHandle, _) => authorHandle.trim.toLowerCase == normalizedViewer }.map(_._2.value)
      }
  }

  private def scoreVoteChoices(votes: Seq[ForumVoteChoice]): Int = {
    votes.foldLeft(0) { (score, vote) =>
      vote match {
        case ForumVoteChoice.Up   => score + 1
        case ForumVoteChoice.Down => score - 1
      }
    }
  }

  private def buildExcerpt(body: String): String = {
    val trimmed = body.trim
    if (trimmed.length <= 90) {
      trimmed
    } else {
      s"${trimmed.take(90)}..."
    }
  }
}
