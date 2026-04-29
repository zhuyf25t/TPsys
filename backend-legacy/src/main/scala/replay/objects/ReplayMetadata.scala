package slaydemo.backend.replay.objects

import slaydemo.backend.shared.objects.{BattleId, ReplayId}

final case class ReplayMetadata(
  replayId: ReplayId,
  battleId: BattleId,
  title: String,
  recordedAtEpochMs: Long
)
