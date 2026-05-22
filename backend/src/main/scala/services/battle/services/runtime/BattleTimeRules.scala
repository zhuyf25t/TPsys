package services.battle.services.runtime

import services.battle.services.*

import services.battle.objects.{DurationMillis, EpochMillis}

private[services] object BattleTimeRules {
  /** 中文名：已流逝at（elapsedAt）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def elapsedAt(startedAt: EpochMillis, duration: DurationMillis, now: EpochMillis): Long =
    math.max(0L, math.min(duration.value, now.value - startedAt.value))

  /** 中文名：已流逝ratedeltadouble（elapsedRateDeltaDouble）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def elapsedRateDeltaDouble(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Double =
    ratePerSecond * math.max(0L, nextElapsed - previousElapsed).toDouble / 1000.0

  /** 中文名：已流逝ratedelta（elapsedRateDelta）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def elapsedRateDelta(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Int = {
    val previous = math.round(ratePerSecond * math.max(0L, previousElapsed).toDouble / 1000.0)
    val next = math.round(ratePerSecond * math.max(0L, nextElapsed).toDouble / 1000.0)
    math.max(0L, next - previous).toInt
  }

  /** 中文名：递减int（decrementInt）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def decrementInt(value: Int, deltaMs: Long): Int =
    math.max(0, value - deltaMs.toInt)

  /** 中文名：递减long（decrementLong）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def decrementLong(value: Long, deltaMs: Long): Long =
    math.max(0L, value - deltaMs)
}
