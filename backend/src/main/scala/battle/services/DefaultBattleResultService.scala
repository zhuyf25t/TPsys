package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.BattleResultSubmissionRequest
import slaydemo.backend.battle.database.BattleResultRepository
import slaydemo.backend.battle.objects.BattleResultRecord
import slaydemo.backend.battle.rules.BattleRules
import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.mails.services.MailService

final class DefaultBattleResultService(
  repository: BattleResultRepository,
  mailService: MailService
) extends BattleResultService {
  override def record(request: BattleResultSubmissionRequest): Either[String, BattleResultRecord] = {
    val handle = safeText(request.handle.value)
    val battleId = safeText(request.battleId.value)

    if (handle.isEmpty) {
      Left("invalid_handle")
    } else if (isVisitorHandle(handle)) {
      Left("visitor_not_allowed")
    } else if (battleId.isEmpty) {
      Left("invalid_battle_id")
    } else {
      val displayName = Option(safeText(request.displayName)).filter(_.nonEmpty).getOrElse(handle)
      val record = BattleResultRecord(
        battleId = slaydemo.backend.shared.objects.BattleId(battleId),
        handle = slaydemo.backend.shared.objects.UserId(handle),
        displayName = displayName,
        finishedAt = request.finishedAt,
        finishedAtLabel = safeText(request.finishedAtLabel),
        durationMs = request.durationMs,
        score = request.score,
        placement = request.placement,
        aliveAtEnd = request.aliveAtEnd,
        ratingBefore = request.ratingBefore,
        ratingDelta = request.ratingDelta,
        ratingAfter = request.ratingAfter,
        resultLabel = safeText(request.resultLabel),
        modeLabel = safeText(request.modeLabel),
        mapLabel = safeText(request.mapLabel),
        highlightLine = safeText(request.highlightLine),
        playersLine = safeText(request.playersLine),
        timelineHint = safeText(request.timelineHint),
        currentLoadout = request.currentLoadout.flatMap(value => Option(value).map(_.trim).filter(_.nonEmpty))
      )

      val saved = repository.save(record)
      createBattleResultMails(saved)
      Right(saved)
    }
  }

  override def list(handle: Option[String], battleId: Option[String], limit: Int): Seq[BattleResultRecord] = {
    val bounded = limit.max(1).min(200)
    val records = (handle.map(_.trim).filter(_.nonEmpty), battleId.map(_.trim).filter(_.nonEmpty)) match {
      case (Some(resolvedHandle), Some(resolvedBattleId)) =>
        repository.listByHandleAndBattleId(resolvedHandle, resolvedBattleId, bounded)
      case (Some(resolvedHandle), None) =>
        repository.listByHandle(resolvedHandle, bounded)
      case (None, Some(resolvedBattleId)) =>
        repository.listByBattleId(resolvedBattleId, bounded)
      case (None, None) =>
        repository.list(bounded)
    }
    records.filterNot(record => isVisitorHandle(record.handle.value))
  }

  private def createBattleResultMails(record: BattleResultRecord): Unit = {
    mailService.create(buildBattleMail(record))
  }

  private def buildBattleMail(record: BattleResultRecord): MailRecord = {
    MailRecord(
      id = s"mail-battle-${record.resultId}",
      ownerHandle = record.handle.value.trim,
      kind = "battle",
      subject = "\u6218\u6597\u7ed3\u7b97\u4e0e\u8bc4\u5206\u66f4\u65b0",
      excerpt =
        s"\u6218\u62a5\u548c\u56de\u653e\u5df2\u751f\u6210\uff0c\u672c\u5c40\u8bc4\u5206\u53d8\u52a8 ${formatDelta(record.ratingDelta)}\uff0c\u5f53\u524d\u8bc4\u5206 ${record.ratingAfter}\u3002",
      senderLabel = "\u6218\u6597\u8bb0\u5f55",
      unread = true,
      important = true,
      createdAt = mailCreatedAt(record)
    )
  }

  private def mailCreatedAt(record: BattleResultRecord): Long =
    if (record.finishedAt > 0) record.finishedAt else System.currentTimeMillis()

  private def formatDelta(delta: Int): String =
    if (delta > 0) s"+$delta" else delta.toString

  private def safeText(value: String): String =
    Option(value).map(_.trim).getOrElse("")

  private def isVisitorHandle(value: String): Boolean =
    BattleRules.isVisitorHandle(value)
}
