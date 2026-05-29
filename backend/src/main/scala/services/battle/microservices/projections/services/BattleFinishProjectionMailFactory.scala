package services.battle.microservices.projections.services

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import services.battle.microservices.results.objects.result.BattleResultRecord
import services.mail.objects.{MailId, MailImportance, MailKind, MailReadState, MailRecord}

private[battle] object BattleFinishProjectionMailFactory {
  /** 中文名：战斗mail（battleMail）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def battleMail(result: BattleResultRecord): MailRecord =
    MailRecord(
      id = MailId(s"mail-battle-${result.resultId.value}"),
      ownerHandle = result.handle,
      kind = MailKind.Battle,
      subject = "Battle settlement ready",
      excerpt = s"${result.resultLabel.value}: score ${result.score.value}, placement #${result.placement.map(_.value).getOrElse(0)}.",
      senderLabel = "Battle archive",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = result.finishedAt,
      sourceBattleId = Some(result.battleId.value),
      sourcePath = Some(replaySourcePath(result)),
      sourceLabel = Some("View replay"),
      governanceMetadata = None,
      friendRequestMetadata = None
    )

  /** 中文名：积分mail（ratingMail）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def ratingMail(result: BattleResultRecord): MailRecord =
    MailRecord(
      id = MailId(s"mail-rating-${result.resultId.value}"),
      ownerHandle = result.handle,
      kind = MailKind.Reward,
      subject = "Rating updated",
      excerpt = s"Rating ${signed(result.ratingDelta.value)} to ${result.ratingAfter.value}.",
      senderLabel = "Rating service",
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = result.finishedAt,
      sourceBattleId = Some(result.battleId.value),
      sourcePath = Some(replaySourcePath(result)),
      sourceLabel = Some("View replay"),
      governanceMetadata = None,
      friendRequestMetadata = None
    )

  private def replaySourcePath(result: BattleResultRecord): String =
    s"/replay/${urlEncode(result.battleId.value)}?handle=${urlEncode(result.handle.value)}"

  private def urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def signed(value: Int): String =
    if value > 0 then s"+$value" else value.toString
}
