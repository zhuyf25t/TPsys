package services.battle.database.runtime

import services.battle.objects.{DurationMillis, EpochMillis}

private[services] object BattleTimeRules {
  /** 中文名：计算已流逝时间（elapsedAt）。游戏职责：把服务端当前时间夹在战斗开始和战斗总时长之间。 */
  def elapsedAt(startedAt: EpochMillis, duration: DurationMillis, now: EpochMillis): Long =
    math.max(0L, math.min(duration.value, now.value - startedAt.value))

  /** 中文名：按时间差计算小数增量（elapsedRateDeltaDouble）。游戏职责：把每秒变化率换算成本 tick 的小数变化量。 */
  def elapsedRateDeltaDouble(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Double =
    ratePerSecond * math.max(0L, nextElapsed - previousElapsed).toDouble / 1000.0

  /** 中文名：按时间差计算整数增量（elapsedRateDelta）。游戏职责：把每秒变化率换算成本 tick 的整数变化量。 */
  def elapsedRateDelta(ratePerSecond: Double, previousElapsed: Long, nextElapsed: Long): Int = {
    val previous = math.round(ratePerSecond * math.max(0L, previousElapsed).toDouble / 1000.0)
    val next = math.round(ratePerSecond * math.max(0L, nextElapsed).toDouble / 1000.0)
    math.max(0L, next - previous).toInt
  }

  /** 中文名：递减整数计时（decrementInt）。游戏职责：按 deltaMs 递减冷却、装填等毫秒计时，最低为 0。 */
  def decrementInt(value: Int, deltaMs: Long): Int =
    math.max(0, value - deltaMs.toInt)

  /** 中文名：递减长整数计时（decrementLong）。游戏职责：按 deltaMs 递减长整型毫秒计时，最低为 0。 */
  def decrementLong(value: Long, deltaMs: Long): Long =
    math.max(0L, value - deltaMs)
}
