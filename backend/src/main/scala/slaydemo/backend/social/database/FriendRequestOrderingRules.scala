package slaydemo.backend.social.database

import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}

private[database] object FriendRequestOrderingRules {
  def recentFirst(left: FriendRequestRecord, right: FriendRequestRecord): Boolean =
    if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
    else left.id.value < right.id.value

  def pairCandidates(left: FriendRequestRecord, right: FriendRequestRecord): Boolean = {
    val leftStatusOrder = statusOrder(left.status)
    val rightStatusOrder = statusOrder(right.status)
    if leftStatusOrder != rightStatusOrder then leftStatusOrder < rightStatusOrder
    else {
      val leftRecency = left.respondedAt.getOrElse(left.createdAt).value
      val rightRecency = right.respondedAt.getOrElse(right.createdAt).value
      if leftRecency != rightRecency then leftRecency > rightRecency
      else if left.createdAt.value != right.createdAt.value then left.createdAt.value > right.createdAt.value
      else left.id.value < right.id.value
    }
  }

  private def statusOrder(status: FriendRequestStatus): Int =
    status match {
      case FriendRequestStatus.Pending  => 0
      case FriendRequestStatus.Accepted => 1
      case FriendRequestStatus.Rejected => 2
    }
}
