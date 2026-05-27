package services.battle.database.projections

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import services.battle.objects.core.{
  BattleAggregateState,
  BattleHighlightLine,
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  BattlePlacement,
  BattlePlayersLine,
  BattleResultLabel,
  BattleTimelineHint,
  EpochMillis
}
import services.battle.objects.player.BattlePlayerState
import services.battle.objects.result.BattleResultRecord
import services.identity.objects.DisplayName
import services.replay.objects.ReplayTitle

private[services] object BattleFinishProjectionLabelRules {
  private val TimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

  val CoverLabel: String =
    "服务器战报"

  /** 中文名：已结束at标签（finishedAtLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def finishedAtLabel(timestamp: EpochMillis): String =
    TimestampFormatter.format(Instant.ofEpochMilli(timestamp.value))

  /** 中文名：模式标签（modeLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def modeLabel: BattleModeLabel =
    BattleModeLabel.fromWire("权威对战")

  /** 中文名：地图标签（mapLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def mapLabel: BattleMapLabel =
    BattleMapLabel.fromWire("权威竞技场")

  /** 中文名：结果标签（resultLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def resultLabel(player: BattlePlayerState, placement: BattlePlacement): BattleResultLabel =
    BattleResultLabel.fromWire(
      if placement.value == 1 then "胜者已决"
      else if player.alive then "存活结算"
      else "淘汰结算"
    )

  /** 中文名：高亮文本行（highlightLine）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def highlightLine(
    player: BattlePlayerState,
    placement: BattlePlacement,
    score: Int
  ): BattleHighlightLine =
    BattleHighlightLine.fromWire(
      s"${BattleFinishProjectionPlayerRules.safeDisplayName(player)} 最终排名第 ${placement.value} 名，结算得分 $score，击杀 ${player.kills.value}，剩余生命 ${math.max(0, player.hp.value)}。"
    )

  /** 中文名：时间线hint（timelineHint）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def timelineHint(player: BattlePlayerState): BattleTimelineHint =
    BattleTimelineHint.fromWire(
      if player.alive then s"${BattleFinishProjectionPlayerRules.safeDisplayName(player)} 存活到权威对战结束。"
      else
        player.eliminatedAtMs match {
          case Some(eliminatedAtMs) =>
            s"${BattleFinishProjectionPlayerRules.safeDisplayName(player)} 在 ${math.max(0L, eliminatedAtMs.value / 1000L)} 秒被淘汰。"
          case None =>
            s"${BattleFinishProjectionPlayerRules.safeDisplayName(player)} 在结束前被淘汰。"
        }
    )

  /** 中文名：players文本行（playersLine）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def playersLine(players: Vector[BattlePlayerState]): BattlePlayersLine = {
    val line = players
      .sortBy(_.seat.value)
      .map(BattleFinishProjectionPlayerRules.safeDisplayName)
      .filter(_.nonEmpty)
      .mkString(" | ")
    BattlePlayersLine.fromWire(if line.nonEmpty then line else "暂无参赛者")
  }

  /** 中文名：server展示name（serverDisplayName）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def serverDisplayName: DisplayName =
    DisplayName("服务器摘要")

  /** 中文名：server结果标签（serverResultLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def serverResultLabel: BattleResultLabel =
    BattleResultLabel.fromWire("对战结束")

  /** 中文名：server高亮文本行（serverHighlightLine）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def serverHighlightLine(battleId: BattleId): BattleHighlightLine =
    BattleHighlightLine.fromWire(s"权威对战 ${battleId.value} 已结束。")

  /** 中文名：server时间线hint（serverTimelineHint）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def serverTimelineHint: BattleTimelineHint =
    BattleTimelineHint.fromWire("服务器已生成权威结算摘要。")

  /** 中文名：回放title（replayTitle）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def replayTitle(result: BattleResultRecord): ReplayTitle =
    ReplayTitle.fromWire(
      if result.handle.key == "server" then "权威对战结束"
      else s"权威对战结束 - ${result.displayName.value}"
    )

  /** 中文名：回放结果标签（replayResultLabel）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def replayResultLabel(state: BattleAggregateState): String =
    state.winnerPlayerId
      .flatMap(winnerPlayerId => state.players.find(_.playerId == winnerPlayerId))
      .fold("对战结束")(_ => "胜者已决")
}
