package services.battle.database

import services.battle.objects.BattleResultRecord

private[database] object BattleResultFileJsonRenderer {
  def renderPayload(records: Vector[BattleResultRecord]): String = {
    val rendered = records.map(renderRecord).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.battle-results.v1",
       |  "results": [
       |$rendered
       |  ]
       |}
       |""".stripMargin
  }

  private def renderRecord(record: BattleResultRecord): String =
    s"""    {
       |      "battleId": "${escape(record.battleId.value)}",
       |      "resultId": "${escape(record.resultId.value)}",
       |      "handle": "${escape(record.handle.value)}",
       |      "displayName": "${escape(record.displayName.value)}",
       |      "finishedAt": ${record.finishedAt.value},
       |      "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |      "durationMs": ${record.durationMs.value},
       |      "score": ${record.score.value},
       |      "placement": ${record.placement.map(_.value.toString).getOrElse("null")},
       |      "aliveAtEnd": ${record.aliveAtEnd},
       |      "ratingBefore": ${record.ratingBefore.value},
       |      "ratingDelta": ${record.ratingDelta.value},
       |      "ratingAfter": ${record.ratingAfter.value},
       |      "resultLabel": "${escape(record.resultLabel.value)}",
       |      "modeLabel": "${escape(record.modeLabel.value)}",
       |      "mapLabel": "${escape(record.mapLabel.value)}",
       |      "highlightLine": "${escape(record.highlightLine.value)}",
       |      "playersLine": "${escape(record.playersLine.value)}",
       |      "timelineHint": "${escape(record.timelineHint.value)}",
       |      "currentLoadout": ${renderNullableString(record.currentLoadout)}
       |    }""".stripMargin

  private def renderNullableString(value: Option[String]): String =
    value.map(text => s""""${escape(text)}"""").getOrElse("null")

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
}
