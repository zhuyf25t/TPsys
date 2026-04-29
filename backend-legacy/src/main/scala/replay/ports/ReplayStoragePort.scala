package slaydemo.backend.replay.ports

import slaydemo.backend.shared.objects.ReplayId

trait ReplayStoragePort {
  def readReplayArtifact(replayId: ReplayId): Option[String]
}
