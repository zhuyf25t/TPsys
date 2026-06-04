package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.objects.{DurationMillis, EpochMillis}

private[battle] object BattleTimeRules {
  def elapsedAt(startedAt: EpochMillis, duration: DurationMillis, now: EpochMillis): IO[Long] =
    IO.pure(math.max(0L, math.min(duration.value, now.value - startedAt.value)))

  def elapsedRateDeltaDouble(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): IO[Double] =
    IO.pure(ratePerSecond * math.max(0L, nextElapsed - previousElapsed).toDouble / 1000.0)

  def elapsedRateDelta(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): IO[Int] =
    IO.pure {
      val previous = math.round(ratePerSecond * math.max(0L, previousElapsed).toDouble / 1000.0)
      val next = math.round(ratePerSecond * math.max(0L, nextElapsed).toDouble / 1000.0)
      math.max(0L, next - previous).toInt
    }

  def decrementInt(value: Int, deltaMs: Long): IO[Int] =
    IO.pure(math.max(0, value - deltaMs.toInt))

  def decrementLong(value: Long, deltaMs: Long): IO[Long] =
    IO.pure(math.max(0L, value - deltaMs))
}
