package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy

private[services] object BattleQueueParticipantRules {
  def normalizeOptionalText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  def normalizeHandle(handle: PlayerHandle): PlayerHandle =
    PlayerHandle.forLookup(handle.value).getOrElse(handle)

  def sameHandleKey(left: PlayerHandle, rightKey: String): Boolean =
    normalizeHandle(left).key == HandlePolicy.normalizeKey(rightKey)

  def heartbeatMatches(entry: QueueParticipantEntry, request: RealtimeRoomHeartbeatCommand): Boolean = {
    val ticketMatches = request.ticketId.contains(entry.ticketId)
    val handleMatches = request.handle.exists(handle => sameHandleKey(entry.participant.handle, normalizeHandle(handle).key))
    ticketMatches || handleMatches
  }

  def touchHeartbeatParticipant(entry: QueueParticipantEntry, now: EpochMillis): QueueParticipantEntry =
    entry.copy(participant = entry.participant.copy(lastSeen = now))
}
