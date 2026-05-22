package services.replay.database

import services.replay.objects.{ReplayCommentRecord, ReplayRecord, ReplaySettlementRecord}

private[database] object ReplayRepositoryOrderingRules {
  def replaysRecentFirst(left: ReplayRecord, right: ReplayRecord): Boolean =
    if left.finishedAt.value != right.finishedAt.value then left.finishedAt.value > right.finishedAt.value
    else left.replayId.value < right.replayId.value

  def fileCommentsChronological(left: ReplayCommentRecord, right: ReplayCommentRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value < right.createdAt.value
    else left.id.value < right.id.value

  def inMemoryCommentsChronological(left: ReplayCommentRecord, right: ReplayCommentRecord): Boolean =
    left.createdAt.value < right.createdAt.value

  def settlements(left: ReplaySettlementRecord, right: ReplaySettlementRecord): Boolean = {
    val leftPlacement = left.placement.map(_.value).getOrElse(Int.MaxValue)
    val rightPlacement = right.placement.map(_.value).getOrElse(Int.MaxValue)
    if leftPlacement != rightPlacement then leftPlacement < rightPlacement
    else left.handle.key < right.handle.key
  }
}
