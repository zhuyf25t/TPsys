package services.battle.microservices.projections.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.objects.core.{DurationMillis, ElapsedMillis}
import services.battle.microservices.runtime.objects.event.BattleEventState
import services.battle.microservices.projections.objects.replay.BattleReplayFrameState

private[battle] final case class BattleReplayFallbackTimeline(
  frameElapsedMs: Vector[ElapsedMillis],
  finalElapsedMs: ElapsedMillis
)

private[battle] object BattleReplayFrameTimelineRules {
  def normalizeReplayFrames(
    frames: Vector[BattleReplayFrameState],
    durationMs: DurationMillis
  ): IO[Vector[BattleReplayFrameState]] =
    frames
      .traverse(frame => clampElapsed(frame.elapsedMs.value, durationMs.value).map(value => frame.copy(elapsedMs = ElapsedMillis(value))))
      .map { normalizedFrames =>
        normalizedFrames
        .sortBy(_.elapsedMs.value)
        .foldLeft(Vector.empty[BattleReplayFrameState]) {
          case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
            accumulator.dropRight(1) :+ frame
          case (accumulator, frame) =>
            accumulator :+ frame
        }
      }

  def fallbackTimeline(
    events: Vector[BattleEventState],
    durationMs: DurationMillis
  ): IO[BattleReplayFallbackTimeline] =
    for
      eventElapsedMs <- events.traverse(event => clampElapsed(event.elapsedMs.value, durationMs.value))
      timeline <- IO.pure {
        val finalElapsedMs = durationMs.value
        val normalizedEventElapsedMs = eventElapsedMs
          .distinct
          .sorted
          .takeRight(4)
        val initialElapsedMs =
          if normalizedEventElapsedMs.headOption.contains(0L) then Vector.empty[Long]
          else Vector(0L)
        val frameElapsedMs = (initialElapsedMs ++ normalizedEventElapsedMs :+ finalElapsedMs).distinct.sorted

        BattleReplayFallbackTimeline(
          frameElapsedMs = frameElapsedMs.map(ElapsedMillis.apply),
          finalElapsedMs = ElapsedMillis(finalElapsedMs)
        )
      }
    yield timeline

  private def clampElapsed(value: Long, maxValue: Long): IO[Long] =
    IO.pure(math.max(0L, math.min(math.max(0L, maxValue), value)))
}
