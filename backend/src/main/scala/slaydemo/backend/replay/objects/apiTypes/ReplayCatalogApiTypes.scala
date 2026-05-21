package slaydemo.backend.replay.objects.apiTypes

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.{ReplayRecord, ReplaySettlementRecord}

final case class ReplayCatalogItem(
  replayId: String,
  battleId: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  finishedAt: Long,
  finishedAtLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Int,
  placement: Option[Int],
  ratingBefore: Option[Int],
  ratingAfter: Option[Int],
  ratingDelta: Option[Int],
  durationMs: Long,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean
)

object ReplayCatalogItem {
  given Encoder[ReplayCatalogItem] = deriveEncoder

  def fromRecord(record: ReplayRecord, settlement: Option[ReplaySettlementRecord]): ReplayCatalogItem = {
    val resultLabel = settlement.map(_.resultLabel).getOrElse(record.resultLabel)
    ReplayCatalogItem(
      replayId = record.replayId.value,
      battleId = record.battleId.value,
      title = settlement.map(item => s"${item.resultLabel} - ${record.finishedAtLabel}").getOrElse(record.title.value),
      modeLabel = record.modeLabel,
      resultLabel = resultLabel,
      finishedAt = record.finishedAt.value,
      finishedAtLabel = record.finishedAtLabel,
      mapLabel = record.mapLabel,
      highlightLine = settlement.map(_.highlightLine).getOrElse(record.highlightLine),
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = settlement.map(_.score.value).getOrElse(record.score.value),
      placement = settlement.map(_.placement).getOrElse(record.placement).map(_.value),
      ratingBefore = settlement.map(_.ratingBefore).getOrElse(record.ratingBefore).map(_.value),
      ratingAfter = settlement.map(_.ratingAfter).getOrElse(record.ratingAfter).map(_.value),
      ratingDelta = settlement.map(_.ratingDelta).getOrElse(record.ratingDelta).map(_.value),
      durationMs = record.durationMs.value,
      aliveAtEnd = settlement.map(_.aliveAtEnd).getOrElse(record.aliveAtEnd),
      thumbnailDataUrl = record.thumbnailDataUrl.filter(_.trim.nonEmpty),
      frameCount = record.frameCount.value,
      playbackAvailable = record.playbackAvailable
    )
  }
}

final case class ReplayCatalogResponse(replays: Vector[ReplayCatalogItem])

object ReplayCatalogResponse {
  given Encoder[ReplayCatalogResponse] = deriveEncoder

  def fromRecords(records: Vector[ReplayRecord], selectedHandle: Option[PlayerHandle]): ReplayCatalogResponse =
    ReplayCatalogResponse(records.map(record => ReplayCatalogItem.fromRecord(record, selectedSettlement(record, selectedHandle))))

  private def selectedSettlement(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): Option[ReplaySettlementRecord] =
    selectedHandle.flatMap(record.settlementFor)
}
