package services.battle.services.results

import services.battle.services.*

import services.battle.database.BattleResultRepository
import services.mail.database.MailRepository
import services.replay.database.ReplayRepository

private[services] trait BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def write(plan: BattleFinishProjectionPlan): Unit
}

private[services] final class BattleResultProjectionArtifactWriter(
  battleResultRepository: BattleResultRepository,
  mailRepository: MailRepository
) extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.settlements.foreach { settlement =>
      val saved = battleResultRepository.save(settlement.result)
      mailRepository.save(BattleFinishProjectionMailFactory.battleMail(saved))
      if saved.ratingDelta.value != 0 then
        mailRepository.save(BattleFinishProjectionMailFactory.ratingMail(saved))
    }
}

private[services] object BattleResultProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def apply(
    battleResultRepository: BattleResultRepository,
    mailRepository: MailRepository
  ): BattleResultProjectionArtifactWriter =
    new BattleResultProjectionArtifactWriter(battleResultRepository, mailRepository)
}

private[services] final class BattleReplayProjectionArtifactWriter(replayRepository: ReplayRepository)
    extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.replay.foreach(replayRepository.saveReplay)
}

private[services] object BattleReplayProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def apply(replayRepository: ReplayRepository): BattleReplayProjectionArtifactWriter =
    new BattleReplayProjectionArtifactWriter(replayRepository)
}
