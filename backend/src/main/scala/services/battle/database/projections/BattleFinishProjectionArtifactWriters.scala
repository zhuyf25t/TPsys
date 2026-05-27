package services.battle.database.projections

import services.battle.database.results.BattleResultRepository
import services.battle.database.projections.{BattleMailPublisherPort, BattleReplayWriterPort}

private[services] trait BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def write(plan: BattleFinishProjectionPlan): Unit
}

private[services] final class BattleResultProjectionArtifactWriter(
  battleResultRepository: BattleResultRepository,
  mailPublisher: BattleMailPublisherPort
) extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.settlements.foreach { settlement =>
      val saved = battleResultRepository.save(settlement.result)
      mailPublisher.publish(BattleFinishProjectionMailFactory.battleMail(saved))
      if saved.ratingDelta.value != 0 then
        mailPublisher.publish(BattleFinishProjectionMailFactory.ratingMail(saved))
    }
}

private[services] object BattleResultProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def apply(
    battleResultRepository: BattleResultRepository,
    mailPublisher: BattleMailPublisherPort
  ): BattleResultProjectionArtifactWriter =
    new BattleResultProjectionArtifactWriter(battleResultRepository, mailPublisher)
}

private[services] final class BattleReplayProjectionArtifactWriter(replayWriter: BattleReplayWriterPort)
    extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.replay.foreach(replayWriter.saveReplay)
}

private[services] object BattleReplayProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果。 */
  def apply(replayWriter: BattleReplayWriterPort): BattleReplayProjectionArtifactWriter =
    new BattleReplayProjectionArtifactWriter(replayWriter)
}
