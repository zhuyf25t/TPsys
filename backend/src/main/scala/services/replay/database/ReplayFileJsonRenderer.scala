package services.replay.database

import services.replay.objects.{ReplayCommentRecord, ReplayId, ReplayRecord, ReplaySettlementRecord}
import services.replay.support.ReplayFramesJsonCodec

private[database] object ReplayFileJsonRenderer {
  def renderPayload(
    records: Vector[ReplayRecord],
    comments: Vector[ReplayCommentRecord],
    settlements: Vector[(ReplayId, ReplaySettlementRecord)]
  ): String = {
    val renderedRecords = records.map(renderReplay).mkString(",\n")
    val renderedComments = comments.map(renderComment).mkString(",\n")
    val renderedSettlements = settlements.map(renderSettlement).mkString(",\n")
    s"""{
       |  "schema": "slay-demo.replay-catalog.v2",
       |  "records": [
       |$renderedRecords
       |  ],
       |  "comments": [
       |$renderedComments
       |  ],
       |  "settlements": [
       |$renderedSettlements
       |  ]
       |}
       |""".stripMargin
  }

  private def renderReplay(record: ReplayRecord): String =
    s"""    {
       |      "replayId": "${escape(record.replayId.value)}",
       |      "battleId": "${escape(record.battleId.value)}",
       |      "handle": "${escape(record.handle.value)}",
       |      "displayName": "${escape(record.displayName.value)}",
       |      "finishedAt": ${record.finishedAt.value},
       |      "finishedAtLabel": "${escape(record.finishedAtLabel)}",
       |      "title": "${escape(record.title.value)}",
       |      "modeLabel": "${escape(record.modeLabel)}",
       |      "resultLabel": "${escape(record.resultLabel)}",
       |      "mapLabel": "${escape(record.mapLabel)}",
       |      "highlightLine": "${escape(record.highlightLine)}",
       |      "coverLabel": "${escape(record.coverLabel)}",
       |      "playersLine": "${escape(record.playersLine)}",
       |      "timelineHint": "${escape(record.timelineHint)}",
       |      "score": ${record.score.value},
       |      "placement": ${record.placement.map(_.value.toString).getOrElse("null")},
       |      "ratingBefore": ${record.ratingBefore.map(_.value.toString).getOrElse("null")},
       |      "ratingDelta": ${record.ratingDelta.map(_.value.toString).getOrElse("null")},
       |      "ratingAfter": ${record.ratingAfter.map(_.value.toString).getOrElse("null")},
       |      "durationMs": ${record.durationMs.value},
       |      "aliveAtEnd": ${record.aliveAtEnd},
       |      "thumbnailDataUrl": ${renderNullableString(record.thumbnailDataUrl)},
       |      "currentLoadout": ${renderNullableString(record.currentLoadout)},
       |      "frameCount": ${record.frameCount.value},
       |      "playbackAvailable": ${record.playbackAvailable},
       |      "framesJsonB64": "${escape(ReplayFramesJsonCodec.encode(record.framesJson.value))}"
       |    }""".stripMargin

  private def renderComment(record: ReplayCommentRecord): String =
    s"""    {
       |      "id": "${escape(record.id.value)}",
       |      "replayId": "${escape(record.replayId.value)}",
       |      "authorHandle": "${escape(record.authorHandle.value)}",
       |      "body": "${escape(record.body)}",
       |      "createdAt": ${record.createdAt.value}
       |    }""".stripMargin

  private def renderSettlement(item: (ReplayId, ReplaySettlementRecord)): String = {
    val (replayId, settlement) = item
    s"""    {
       |      "replayId": "${escape(replayId.value)}",
       |      "handle": "${escape(settlement.handle.value)}",
       |      "displayName": "${escape(settlement.displayName.value)}",
       |      "resultLabel": "${escape(settlement.resultLabel)}",
       |      "highlightLine": "${escape(settlement.highlightLine)}",
       |      "score": ${settlement.score.value},
       |      "placement": ${settlement.placement.map(_.value.toString).getOrElse("null")},
       |      "ratingBefore": ${settlement.ratingBefore.map(_.value.toString).getOrElse("null")},
       |      "ratingDelta": ${settlement.ratingDelta.map(_.value.toString).getOrElse("null")},
       |      "ratingAfter": ${settlement.ratingAfter.map(_.value.toString).getOrElse("null")},
       |      "aliveAtEnd": ${settlement.aliveAtEnd},
       |      "currentLoadout": ${renderNullableString(settlement.currentLoadout)}
       |    }""".stripMargin
  }

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
