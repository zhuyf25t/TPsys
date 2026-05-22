package services.forum.objects

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle

final case class ForumTopicId(value: String) extends AnyVal
final case class ForumReplyId(value: String) extends AnyVal
final case class ForumTitle(value: String) extends AnyVal
final case class ForumBody(value: String) extends AnyVal
final case class ForumTag(value: String) extends AnyVal
final case class ForumScore(value: Int) extends AnyVal
final case class ForumReplyCount(value: Int) extends AnyVal

final case class ForumVoterKey(value: String) extends AnyVal

object ForumVoterKey {
  def fromHandle(handle: PlayerHandle): ForumVoterKey =
    ForumVoterKey(handle.key)
}

enum ForumVoteChoice {
  case Up
  case Down
}

object ForumVoteChoice {
  def fromWire(value: String): Option[ForumVoteChoice] =
    Option(value).map(_.trim.toLowerCase).flatMap {
      case "up"   => Some(ForumVoteChoice.Up)
      case "down" => Some(ForumVoteChoice.Down)
      case _      => None
    }

  def wireValue(value: ForumVoteChoice): String =
    value match {
      case ForumVoteChoice.Up   => "up"
      case ForumVoteChoice.Down => "down"
    }

  def scoreDelta(value: ForumVoteChoice): Int =
    value match {
      case ForumVoteChoice.Up   => 1
      case ForumVoteChoice.Down => -1
    }
}

final case class ForumVotes(valuesByVoter: Map[ForumVoterKey, ForumVoteChoice]) {
  def setFor(voter: PlayerHandle, vote: Option[ForumVoteChoice]): ForumVotes = {
    val key = ForumVoterKey.fromHandle(voter)
    vote match {
      case Some(choice) => copy(valuesByVoter = valuesByVoter.updated(key, choice))
      case None         => copy(valuesByVoter = valuesByVoter.removed(key))
    }
  }

  def viewerVote(viewer: Option[PlayerHandle]): Option[ForumVoteChoice] =
    viewer.flatMap(handle => valuesByVoter.get(ForumVoterKey.fromHandle(handle)))

  def score: ForumScore =
    ForumScore(valuesByVoter.values.map(ForumVoteChoice.scoreDelta).sum)
}

object ForumVotes {
  val empty: ForumVotes = ForumVotes(Map.empty)
}

final case class ForumReplyRecord(
  id: ForumReplyId,
  authorHandle: PlayerHandle,
  body: ForumBody,
  createdAt: EpochMillis,
  votes: ForumVotes
)

object ForumReplyRecord {
  def create(
    id: ForumReplyId,
    authorHandle: PlayerHandle,
    body: ForumBody,
    createdAt: EpochMillis
  ): ForumReplyRecord =
    ForumReplyRecord(
      id = id,
      authorHandle = authorHandle,
      body = body,
      createdAt = createdAt,
      votes = ForumVotes.empty
    )
}

final case class ForumTopicRecord(
  id: ForumTopicId,
  title: ForumTitle,
  body: ForumBody,
  tag: ForumTag,
  authorHandle: PlayerHandle,
  createdAt: EpochMillis,
  updatedAt: EpochMillis,
  replies: Vector[ForumReplyRecord],
  votes: ForumVotes
)

object ForumTopicRecord {
  def create(
    id: ForumTopicId,
    title: ForumTitle,
    body: ForumBody,
    tag: ForumTag,
    authorHandle: PlayerHandle,
    createdAt: EpochMillis
  ): ForumTopicRecord =
    ForumTopicRecord(
      id = id,
      title = title,
      body = body,
      tag = tag,
      authorHandle = authorHandle,
      createdAt = createdAt,
      updatedAt = createdAt,
      replies = Vector.empty,
      votes = ForumVotes.empty
    )

  def addReply(topic: ForumTopicRecord, reply: ForumReplyRecord, updatedAt: EpochMillis): ForumTopicRecord =
    topic.copy(replies = topic.replies :+ reply, updatedAt = updatedAt)

  def setVote(
    topic: ForumTopicRecord,
    voter: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): ForumTopicRecord =
    topic.copy(votes = topic.votes.setFor(voter, vote), updatedAt = updatedAt)

  def setReplyVote(
    topic: ForumTopicRecord,
    replyId: ForumReplyId,
    voter: PlayerHandle,
    vote: Option[ForumVoteChoice],
    updatedAt: EpochMillis
  ): Either[ForumReplyVoteUpdateError, ForumTopicRecord] = {
    val replyIndex = topic.replies.indexWhere(_.id == replyId)
    if replyIndex < 0 then Left(ForumReplyVoteUpdateError.ReplyNotFound)
    else {
      val reply = topic.replies(replyIndex)
      val updatedReply = reply.copy(votes = reply.votes.setFor(voter, vote))
      Right(topic.copy(replies = topic.replies.updated(replyIndex, updatedReply), updatedAt = updatedAt))
    }
  }

  def toView(topic: ForumTopicRecord, viewer: Option[PlayerHandle]): ForumTopicView =
    ForumTopicView(
      id = topic.id,
      title = topic.title,
      author = topic.authorHandle,
      excerpt = buildExcerpt(topic.body),
      tag = topic.tag,
      replies = ForumReplyCount(topic.replies.size),
      updatedAt = topic.updatedAt,
      createdAt = topic.createdAt,
      body = topic.body,
      replyItems = topic.replies.sortBy(_.createdAt.value).map(ForumReplyView.fromRecord(_, viewer)),
      viewerVote = topic.votes.viewerVote(viewer),
      score = topic.votes.score
    )

  private def buildExcerpt(body: ForumBody): String = {
    val trimmed = body.value.trim
    if trimmed.length <= 90 then trimmed else s"${trimmed.take(90)}..."
  }
}

enum ForumReplyVoteUpdateError {
  case ReplyNotFound
}

final case class ForumReplyView(
  id: ForumReplyId,
  author: PlayerHandle,
  body: ForumBody,
  publishedAt: EpochMillis,
  viewerVote: Option[ForumVoteChoice],
  score: ForumScore
)

object ForumReplyView {
  def fromRecord(record: ForumReplyRecord, viewer: Option[PlayerHandle]): ForumReplyView =
    ForumReplyView(
      id = record.id,
      author = record.authorHandle,
      body = record.body,
      publishedAt = record.createdAt,
      viewerVote = record.votes.viewerVote(viewer),
      score = record.votes.score
    )
}

final case class ForumTopicView(
  id: ForumTopicId,
  title: ForumTitle,
  author: PlayerHandle,
  excerpt: String,
  tag: ForumTag,
  replies: ForumReplyCount,
  updatedAt: EpochMillis,
  createdAt: EpochMillis,
  body: ForumBody,
  replyItems: Vector[ForumReplyView],
  viewerVote: Option[ForumVoteChoice],
  score: ForumScore
)
