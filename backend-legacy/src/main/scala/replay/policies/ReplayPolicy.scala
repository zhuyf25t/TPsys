package slaydemo.backend.replay.policies

trait ReplayPolicy {
  def canExposeReplay(publicVisibility: Boolean): Boolean
}
