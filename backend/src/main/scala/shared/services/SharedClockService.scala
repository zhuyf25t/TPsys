package slaydemo.backend.shared.services

import java.time.Instant

trait SharedClockService {
  def now(): Instant
}
