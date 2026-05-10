package slaydemo.backend.battle.routes

import slaydemo.backend.battle.objects.BattleResultRecord
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object BattleResultRouteJsonRenderer {
  def renderRecords(records: Vector[BattleResultRecord]): String =
    renderObject(Vector("results" -> records.map(renderRecord).mkString("[", ",", "]")))

  def renderRecord(record: BattleResultRecord): String =
    renderObject(
      Vector(
        "resultId" -> jsonString(record.resultId.value),
        "battleId" -> jsonString(record.battleId.value),
        "handle" -> jsonString(record.handle.value),
        "displayName" -> jsonString(record.displayName.value),
        "finishedAt" -> record.finishedAt.value.toString,
        "finishedAtLabel" -> jsonString(record.finishedAtLabel),
        "durationMs" -> record.durationMs.value.toString,
        "score" -> record.score.value.toString,
        "placement" -> record.placement.map(_.value.toString).getOrElse("null"),
        "aliveAtEnd" -> record.aliveAtEnd.toString,
        "ratingBefore" -> record.ratingBefore.value.toString,
        "ratingDelta" -> record.ratingDelta.value.toString,
        "ratingAfter" -> record.ratingAfter.value.toString,
        "resultLabel" -> jsonString(record.resultLabel.value),
        "modeLabel" -> jsonString(record.modeLabel.value),
        "mapLabel" -> jsonString(record.mapLabel.value),
        "highlightLine" -> jsonString(record.highlightLine.value),
        "playersLine" -> jsonString(record.playersLine.value),
        "timelineHint" -> jsonString(record.timelineHint.value),
        "currentLoadout" -> record.currentLoadout.map(jsonString).getOrElse("null")
      )
    )

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
