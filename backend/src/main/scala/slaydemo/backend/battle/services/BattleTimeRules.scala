package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{DurationMillis, EpochMillis}

private[services] object BattleTimeRules {
  def elapsedAt(startedAt: EpochMillis, duration: DurationMillis, now: EpochMillis): Long =
    math.max(0L, math.min(duration.value, now.value - startedAt.value))

  def elapsedRateDeltaDouble(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Double =
    ratePerSecond * math.max(0L, nextElapsed - previousElapsed).toDouble / 1000.0

  def elapsedRateDelta(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Int = {
    val previous = math.round(ratePerSecond * math.max(0L, previousElapsed).toDouble / 1000.0)
    val next = math.round(ratePerSecond * math.max(0L, nextElapsed).toDouble / 1000.0)
    math.max(0L, next - previous).toInt
  }

  def decrementInt(value: Int, deltaMs: Long): Int =
    math.max(0, value - deltaMs.toInt)

  def decrementLong(value: Long, deltaMs: Long): Long =
    math.max(0L, value - deltaMs)
}
