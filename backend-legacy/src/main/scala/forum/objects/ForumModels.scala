package slaydemo.backend.forum.objects

import slaydemo.backend.shared.objects.ThreadId

final case class ForumTopicRecord(
  threadId: ThreadId,
  title: String,
  body: String,
  tag: String,
  authorHandle: String,
  createdAt: Long,
  updatedAt: Long
)

final case class ForumReplyRecord(
  replyId: String,
  threadId: ThreadId,
  authorHandle: String,
  body: String,
  createdAt: Long
)

sealed trait ForumVoteChoice {
  def value: String
}

object ForumVoteChoice {
  case object Up extends ForumVoteChoice {
    override val value: String = "up"
  }

  case object Down extends ForumVoteChoice {
    override val value: String = "down"
  }

  def fromString(value: String): Option[ForumVoteChoice] = value.trim.toLowerCase match {
    case "up"   => Some(Up)
    case "down" => Some(Down)
    case _      => None
  }
}

final case class ForumVoteRecord(
  threadId: ThreadId,
  authorHandle: String,
  vote: ForumVoteChoice,
  updatedAt: Long
)

final case class ForumReplyVoteRecord(
  replyId: String,
  authorHandle: String,
  vote: ForumVoteChoice,
  updatedAt: Long
)

final case class ForumReplyView(
  id: String,
  author: String,
  body: String,
  publishedAt: Long,
  viewerVote: Option[String],
  score: Int
)

final case class ForumTopicView(
  id: String,
  title: String,
  author: String,
  excerpt: String,
  tag: String,
  replies: Int,
  updatedAt: Long,
  createdAt: Long,
  body: String,
  replyItems: Vector[ForumReplyView],
  viewerVote: Option[String],
  score: Int
)
