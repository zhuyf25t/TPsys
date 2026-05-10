package slaydemo.backend.social.services

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.social.objects.FriendRequestRecord

private[services] object FriendRequestVisibilityRules {
  def canCreate(source: PlayerHandle, target: PlayerHandle): Boolean =
    source.key != target.key && isPlayable(source) && isPlayable(target)

  def isVisible(request: FriendRequestRecord): Boolean =
    isPlayable(request.sourceHandle) && isPlayable(request.targetHandle)

  def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)
}
