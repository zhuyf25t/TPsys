package slaydemo.backend.battle.services

import slaydemo.backend.battle.database.BattleResultRepository
import slaydemo.backend.mail.database.MailRepository
import slaydemo.backend.replay.database.ReplayRepository

private[services] trait BattleFinishProjectionArtifactWriter {
  def write(plan: BattleFinishProjectionPlan): Unit
}

private[services] final class BattleResultProjectionArtifactWriter(
  battleResultRepository: BattleResultRepository,
  mailRepository: MailRepository
) extends BattleFinishProjectionArtifactWriter {
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.settlements.foreach { settlement =>
      val saved = battleResultRepository.save(settlement.result)
      mailRepository.save(BattleFinishProjectionMailFactory.battleMail(saved))
      if saved.ratingDelta.value != 0 then
        mailRepository.save(BattleFinishProjectionMailFactory.ratingMail(saved))
    }
}

private[services] object BattleResultProjectionArtifactWriter {
  def apply(
    battleResultRepository: BattleResultRepository,
    mailRepository: MailRepository
  ): BattleResultProjectionArtifactWriter =
    new BattleResultProjectionArtifactWriter(battleResultRepository, mailRepository)
}

private[services] final class BattleReplayProjectionArtifactWriter(replayRepository: ReplayRepository)
    extends BattleFinishProjectionArtifactWriter {
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.replay.foreach(replayRepository.saveReplay)
}

private[services] object BattleReplayProjectionArtifactWriter {
  def apply(replayRepository: ReplayRepository): BattleReplayProjectionArtifactWriter =
    new BattleReplayProjectionArtifactWriter(replayRepository)
}
