package services.forum.database

import services.forum.objects.ForumTopicRecord

private[database] object ForumTopicOrderingRules {
  def recentFirst(left: ForumTopicRecord, right: ForumTopicRecord): Boolean =
    if left.updatedAt.value != right.updatedAt.value then left.updatedAt.value > right.updatedAt.value
    else left.createdAt.value > right.createdAt.value

  def sortRecentFirst(topics: Vector[ForumTopicRecord]): Vector[ForumTopicRecord] =
    topics.sortWith(recentFirst)
}
