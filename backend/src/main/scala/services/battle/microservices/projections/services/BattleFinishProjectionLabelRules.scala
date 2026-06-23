package services.battle.microservices.projections.services

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import cats.effect.IO

import services.battle.objects.core.{
  BattleAggregateState,
  BattleId,
  BattleMapLabel,
  BattleModeLabel,
  EpochMillis
}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.results.objects.result.{
  BattleHighlightLine,
  BattlePlacement,
  BattleResultFinishedAtLabel,
  BattlePlayersLine,
  BattleResultLabel,
  BattleResultRecord,
  BattleTimelineHint
}
import services.identity.objects.DisplayName
import services.replay.objects.ReplayTitle

private[battle] object BattleFinishProjectionLabelRules {
  private val TimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

  val CoverLabel: String =
    "\u670d\u52a1\u5668\u6218\u62a5"

  def finishedAtLabel(timestamp: EpochMillis): IO[BattleResultFinishedAtLabel] =
    IO.pure(BattleResultFinishedAtLabel.fromWire(TimestampFormatter.format(Instant.ofEpochMilli(timestamp.value))))

  def modeLabel: IO[BattleModeLabel] =
    IO.pure(BattleModeLabel.fromWire("\u6743\u5a01\u5bf9\u6218"))

  def mapLabel: IO[BattleMapLabel] =
    IO.pure(BattleMapLabel.fromWire("\u6743\u5a01\u7ade\u6280\u573a"))

  def resultLabel(player: BattlePlayerState, placement: BattlePlacement): IO[BattleResultLabel] =
    IO.pure(
      BattleResultLabel.fromWire(
        if placement.value == 1 then "\u80dc\u8005\u5df2\u51b3"
        else if player.alive then "\u5b58\u6d3b\u7ed3\u7b97"
        else "\u6dd8\u6c70\u7ed3\u7b97"
      )
    )

  def highlightLine(
    player: BattlePlayerState,
    placement: BattlePlacement,
    score: Int
  ): IO[BattleHighlightLine] =
    BattleFinishProjectionPlayerRules.safeDisplayName(player).map { displayName =>
      BattleHighlightLine.fromWire(
        s"$displayName \u6700\u7ec8\u6392\u540d\u7b2c ${placement.value} \u540d\uff0c\u7ed3\u7b97\u5f97\u5206 $score\uff0c\u51fb\u6740 ${player.kills.value}\uff0c\u5269\u4f59\u751f\u547d ${math.max(0, player.hp.value)}\u3002"
      )
    }

  def timelineHint(player: BattlePlayerState): IO[BattleTimelineHint] =
    BattleFinishProjectionPlayerRules.safeDisplayName(player).map { displayName =>
      BattleTimelineHint.fromWire(
        if player.alive then s"$displayName \u5b58\u6d3b\u5230\u6743\u5a01\u5bf9\u6218\u7ed3\u675f\u3002"
        else
          player.eliminatedAtMs match {
            case Some(eliminatedAtMs) =>
              s"$displayName \u5728 ${math.max(0L, eliminatedAtMs.value / 1000L)} \u79d2\u88ab\u6dd8\u6c70\u3002"
            case None =>
              s"$displayName \u5728\u7ed3\u675f\u524d\u88ab\u6dd8\u6c70\u3002"
          }
      )
    }

  def playersLine(players: Vector[BattlePlayerState]): IO[BattlePlayersLine] =
    players
      .sortBy(_.seat.value)
      .foldLeft(IO.pure(Vector.empty[String])) { case (previous, player) =>
        for
          names <- previous
          displayName <- BattleFinishProjectionPlayerRules.safeDisplayName(player)
        yield if displayName.nonEmpty then names :+ displayName else names
      }
      .map { names =>
        val line = names.mkString(" | ")
        BattlePlayersLine.fromWire(if line.nonEmpty then line else "\u6682\u65e0\u53c2\u8d5b\u8005")
      }

  def serverDisplayName: IO[DisplayName] =
    IO.pure(DisplayName("\u670d\u52a1\u5668\u6458\u8981"))

  def serverResultLabel: IO[BattleResultLabel] =
    IO.pure(BattleResultLabel.fromWire("\u5bf9\u6218\u7ed3\u675f"))

  def serverHighlightLine(battleId: BattleId): IO[BattleHighlightLine] =
    IO.pure(BattleHighlightLine.fromWire(s"\u6743\u5a01\u5bf9\u6218 ${battleId.value} \u5df2\u7ed3\u675f\u3002"))

  def serverTimelineHint: IO[BattleTimelineHint] =
    IO.pure(BattleTimelineHint.fromWire("\u670d\u52a1\u5668\u5df2\u751f\u6210\u6743\u5a01\u7ed3\u7b97\u6458\u8981\u3002"))

  def replayTitle(result: BattleResultRecord): IO[ReplayTitle] =
    IO.pure(
      ReplayTitle.fromWire(
        if result.handle.key == "server" then "\u6743\u5a01\u5bf9\u6218\u7ed3\u675f"
        else s"\u6743\u5a01\u5bf9\u6218\u7ed3\u675f - ${result.displayName.value}"
      )
    )

  def replayResultLabel(state: BattleAggregateState): IO[String] =
    IO.pure(
      state.winnerPlayerId
        .flatMap(winnerPlayerId => state.players.find(_.playerId == winnerPlayerId))
        .fold("\u5bf9\u6218\u7ed3\u675f")(_ => "\u80dc\u8005\u5df2\u51b3")
    )
}
