package services.battle.microservices.projections.services

import services.battle.database.results.BattleResultTable
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort}
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[battle] trait BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def write(plan: BattleFinishProjectionPlan): Unit
}

private[battle] final class BattleResultProjectionArtifactWriter(
  connectionSettings: PostgresConnectionSettings,
  mailPublisher: BattleMailPublisherPort
) extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.settlements.foreach { settlement =>
      val saved = PostgresSupport.withTransactionConnection(connectionSettings) { connection =>
        BattleResultTable.save(connection, settlement.result)
      }
      mailPublisher.publish(BattleFinishProjectionMailFactory.battleMail(saved))
      if saved.ratingDelta.value != 0 then
        mailPublisher.publish(BattleFinishProjectionMailFactory.ratingMail(saved))
    }
}

private[battle] object BattleResultProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def apply(
    connectionSettings: PostgresConnectionSettings,
    mailPublisher: BattleMailPublisherPort
  ): BattleResultProjectionArtifactWriter =
    new BattleResultProjectionArtifactWriter(connectionSettings, mailPublisher)
}

private[battle] final class BattleReplayProjectionArtifactWriter(replayWriter: BattleReplayWriterPort)
    extends BattleFinishProjectionArtifactWriter {
  /** 中文名：write（write）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  override def write(plan: BattleFinishProjectionPlan): Unit =
    plan.replay.foreach(replayWriter.saveReplay)
}

private[battle] object BattleReplayProjectionArtifactWriter {
  /** 中文名：应用（apply）。游戏职责：在后端结算域中管理战报、回放、排名和历史记录，形成对局结束后的权威结果�?*/
  def apply(replayWriter: BattleReplayWriterPort): BattleReplayProjectionArtifactWriter =
    new BattleReplayProjectionArtifactWriter(replayWriter)
}
