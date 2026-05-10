package slaydemo.backend.replay.routes

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord, ReplaySettlementRecord}
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] object ReplayRouteJsonRenderer {
  def renderCatalog(records: Vector[ReplayRecord], selectedHandle: Option[PlayerHandle]): String =
    renderObject(Vector("replays" -> records.map(renderCatalogRecord(_, selectedHandle)).mkString("[", ",", "]")))

  def renderDetail(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String =
    renderObject(Vector("replay" -> renderDetailRecord(record, selectedHandle)))

  def renderComments(records: Vector[ReplayCommentRecord]): String =
    renderObject(Vector("comments" -> records.map(renderCommentRecord).mkString("[", ",", "]")))

  def renderComment(comment: ReplayCommentRecord): String =
    renderObject(Vector("comment" -> renderCommentRecord(comment)))

  private def renderCatalogRecord(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String =
    renderObject(catalogFields(record, selectedSettlement(record, selectedHandle)))

  private def renderDetailRecord(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): String = {
    val settlement = selectedSettlement(record, selectedHandle)
    renderObject(
      catalogFields(record, settlement) ++ Vector(
        "handle" -> jsonString(settlement.map(_.handle.value).getOrElse(record.handle.value)),
        "displayName" -> jsonString(settlement.map(_.displayName.value).getOrElse(record.displayName.value)),
        "currentLoadout" -> renderOptionalString(settlement.flatMap(_.currentLoadout).orElse(record.currentLoadout)),
        "frames" -> record.framesJson.value
      )
    )
  }

  private def catalogFields(record: ReplayRecord, settlement: Option[ReplaySettlementRecord]): Vector[(String, String)] = {
    val resultLabel = settlement.map(_.resultLabel).getOrElse(record.resultLabel)
    Vector(
      "replayId" -> jsonString(record.replayId.value),
      "battleId" -> jsonString(record.battleId.value),
      "title" -> jsonString(settlement.map(item => s"${item.resultLabel} - ${record.finishedAtLabel}").getOrElse(record.title.value)),
      "modeLabel" -> jsonString(record.modeLabel),
      "resultLabel" -> jsonString(resultLabel),
      "finishedAt" -> record.finishedAt.value.toString,
      "finishedAtLabel" -> jsonString(record.finishedAtLabel),
      "mapLabel" -> jsonString(record.mapLabel),
      "highlightLine" -> jsonString(settlement.map(_.highlightLine).getOrElse(record.highlightLine)),
      "coverLabel" -> jsonString(record.coverLabel),
      "playersLine" -> jsonString(record.playersLine),
      "timelineHint" -> jsonString(record.timelineHint),
      "score" -> settlement.map(_.score.value).getOrElse(record.score.value).toString,
      "placement" -> settlement.map(_.placement).getOrElse(record.placement).map(_.value.toString).getOrElse("null"),
      "ratingBefore" -> settlement.map(_.ratingBefore).getOrElse(record.ratingBefore).map(_.value.toString).getOrElse("null"),
      "ratingAfter" -> settlement.map(_.ratingAfter).getOrElse(record.ratingAfter).map(_.value.toString).getOrElse("null"),
      "ratingDelta" -> settlement.map(_.ratingDelta).getOrElse(record.ratingDelta).map(_.value.toString).getOrElse("null"),
      "durationMs" -> record.durationMs.value.toString,
      "aliveAtEnd" -> settlement.map(_.aliveAtEnd).getOrElse(record.aliveAtEnd).toString,
      "thumbnailDataUrl" -> renderOptionalString(record.thumbnailDataUrl),
      "frameCount" -> record.frameCount.value.toString,
      "playbackAvailable" -> record.playbackAvailable.toString
    )
  }

  private def renderCommentRecord(comment: ReplayCommentRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(comment.id.value),
        "replayId" -> jsonString(comment.replayId.value),
        "authorHandle" -> jsonString(comment.authorHandle.value),
        "body" -> jsonString(comment.body),
        "createdAt" -> comment.createdAt.value.toString
      )
    )

  private def selectedSettlement(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): Option[ReplaySettlementRecord] =
    selectedHandle.flatMap(record.settlementFor)

  private def renderOptionalString(value: Option[String]): String =
    value.filter(_.trim.nonEmpty).map(jsonString).getOrElse("null")

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
