package services.social.services

import services.identity.objects.PlayerHandle
import system.policies.HandlePolicy
import services.social.objects.FriendRequestRecord

private[services] object FriendRequestVisibilityRules {
  def canCreate(source: PlayerHandle, target: PlayerHandle): Boolean =
    source.key != target.key && isPlayable(source) && isPlayable(target)

  def isVisible(request: FriendRequestRecord): Boolean =
    isPlayable(request.sourceHandle) && isPlayable(request.targetHandle)

  def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)
}
