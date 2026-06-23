package services.battle.microservices.results.objects.result

import services.battle.objects.core.{
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  DurationMillis,
  EpochMillis
}
import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating, Score}
import services.identity.objects.{DisplayName, PlayerHandle}

final case class BattleResultRecord(
  battleId: BattleId,
  handle: PlayerHandle,
  displayName: DisplayName,
  finishedAt: EpochMillis,
  finishedAtLabel: BattleResultFinishedAtLabel,
  durationMs: DurationMillis,
  score: Score,
  placement: Option[BattlePlacement],
  survivalOutcome: BattleSurvivalOutcome,
  ratingBefore: Rating,
  ratingDelta: RatingDelta,
  ratingAfter: Rating,
  resultLabel: BattleResultLabel,
  modeLabel: BattleModeLabel,
  mapLabel: BattleMapLabel,
  highlightLine: BattleHighlightLine,
  playersLine: BattlePlayersLine,
  timelineHint: BattleTimelineHint,
  currentLoadout: Option[BattleResultLoadoutLabel]
) {
  /**
   * 中文名：结果标识（resultId）�?
   * 游戏视线：一场战斗会为每个玩家生成一条结算记录，这个 ID 用来稳定定位“某�?battle 下某个玩家的结算卡片”�?
   * 建模原因：`BattleResultId` 是结算结果的值对象，避免调用方手写拼接字符串造成前后端结果引用不一致�?
   */
  def resultId: BattleResultId =
    BattleResultRecord.resultId(battleId, handle)

  /**
   * 中文名：终局存活标记（aliveAtEnd）�?
   * 游戏视线：结算页需要显示玩家是幸存还是被淘汰，这里把领域枚举转换为前端/历史记录容易展示的布尔字段�?
   * 建模原因：真实语义保存在 `BattleSurvivalOutcome`，这个函数只是协议兼容投影，避免业务层直接依赖裸 Boolean�?
   */
  def aliveAtEnd: Boolean =
    BattleSurvivalOutcome.aliveAtEnd(survivalOutcome)
}

object BattleResultRecord {
  /**
   * 中文名：生成结果标识（resultId）�?
   * 游戏视线：把战斗 ID 和玩�?handle 组合成唯一结算记录 ID，保证同一场战斗内每个玩家都有稳定可查的结果入口�?
   * 建模原因：集中管�?ID 拼接规则，避�?route、service、frontend 各自拼接导致契约漂移�?
   */
  def resultId(battleId: BattleId, handle: PlayerHandle): BattleResultId =
    BattleResultId(s"${battleId.value.trim}:${handle.key}")
}
