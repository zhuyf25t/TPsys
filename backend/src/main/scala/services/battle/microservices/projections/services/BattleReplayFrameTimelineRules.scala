package services.battle.microservices.projections.services

import services.battle.objects.core.{DurationMillis, ElapsedMillis}
import services.battle.objects.event.BattleEventState
import services.battle.objects.replay.BattleReplayFrameState

private[battle] final case class BattleReplayFallbackTimeline(
  frameElapsedMs: Vector[ElapsedMillis],
  finalElapsedMs: ElapsedMillis
)

private[battle] object BattleReplayFrameTimelineRules {
  /** 中文名：规范化回放frames（normalizeReplayFrames）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def normalizeReplayFrames(
    frames: Vector[BattleReplayFrameState],
    durationMs: DurationMillis
  ): Vector[BattleReplayFrameState] =
    frames
      .map(frame => frame.copy(elapsedMs = ElapsedMillis(clampElapsed(frame.elapsedMs.value, durationMs.value))))
      .sortBy(_.elapsedMs.value)
      .foldLeft(Vector.empty[BattleReplayFrameState]) {
        case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
          accumulator.dropRight(1) :+ frame
        case (accumulator, frame) =>
          accumulator :+ frame
      }

  /** 中文名：兜底时间线（fallbackTimeline）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def fallbackTimeline(
    events: Vector[BattleEventState],
    durationMs: DurationMillis
  ): BattleReplayFallbackTimeline = {
    val finalElapsedMs = durationMs.value
    val eventElapsedMs = events
      .map(event => clampElapsed(event.elapsedMs.value, finalElapsedMs))
      .distinct
      .sorted
      .takeRight(4)
    val initialElapsedMs =
      if eventElapsedMs.headOption.contains(0L) then Vector.empty[Long]
      else Vector(0L)
    val frameElapsedMs = (initialElapsedMs ++ eventElapsedMs :+ finalElapsedMs).distinct.sorted

    BattleReplayFallbackTimeline(
      frameElapsedMs = frameElapsedMs.map(ElapsedMillis.apply),
      finalElapsedMs = ElapsedMillis(finalElapsedMs)
    )
  }

  private def clampElapsed(value: Long, maxValue: Long): Long =
    math.max(0L, math.min(math.max(0L, maxValue), value))
}
