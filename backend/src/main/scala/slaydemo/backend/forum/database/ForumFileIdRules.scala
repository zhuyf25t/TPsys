package slaydemo.backend.forum.database

import slaydemo.backend.forum.objects.{ForumReplyId, ForumTopicId}

private[database] final case class ForumFileIdCounters(
  nextTopicNumber: Long,
  nextReplyNumber: Long
) {
  def allocateTopicId: (ForumTopicId, ForumFileIdCounters) =
    (
      ForumTopicId(f"topic-$nextTopicNumber%012d"),
      copy(nextTopicNumber = nextTopicNumber + 1L)
    )

  def allocateReplyId: (ForumReplyId, ForumFileIdCounters) =
    (
      ForumReplyId(f"reply-$nextReplyNumber%012d"),
      copy(nextReplyNumber = nextReplyNumber + 1L)
    )

  def afterTopicId(id: ForumTopicId): ForumFileIdCounters =
    copy(nextTopicNumber = advance(nextTopicNumber, id.value, "topic-"))

  def afterReplyId(id: ForumReplyId): ForumFileIdCounters =
    copy(nextReplyNumber = advance(nextReplyNumber, id.value, "reply-"))

  private def advance(current: Long, value: String, prefix: String): Long =
    parseNumericId(value, prefix).map(number => math.max(current, number + 1L)).getOrElse(current)

  private def parseNumericId(value: String, prefix: String): Option[Long] = {
    val trimmed = value.trim
    Option.when(trimmed.startsWith(prefix) && trimmed.drop(prefix.length).forall(_.isDigit)) {
      trimmed.drop(prefix.length).toLong
    }
  }
}

private[database] object ForumFileIdCounters {
  val initial: ForumFileIdCounters =
    ForumFileIdCounters(
      nextTopicNumber = 1L,
      nextReplyNumber = 1L
    )
}
