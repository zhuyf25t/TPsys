package slaydemo.backend.shared.policies

trait ClockPolicy {
  def currentEpochMillis(): Long
}
