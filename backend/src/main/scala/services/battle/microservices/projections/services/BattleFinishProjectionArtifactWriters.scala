package services.battle.microservices.projections.services

import cats.effect.IO

import services.battle.microservices.results.database.BattleResultTable
import services.battle.microservices.projections.services.{BattleMailPublisherPort, BattleReplayWriterPort}
import system.database.PostgresSupport
import system.storage.PostgresConnectionSettings

private[battle] trait BattleFinishProjectionArtifactWriter {
  def write(plan: BattleFinishProjectionPlan): IO[Unit]
}

private[battle] final class BattleResultProjectionArtifactWriter(
  connectionSettings: PostgresConnectionSettings,
  mailPublisher: BattleMailPublisherPort
) extends BattleFinishProjectionArtifactWriter {
  override def write(plan: BattleFinishProjectionPlan): IO[Unit] =
    plan.settlements.toVector.flatMap { settlements =>
      settlements.foldLeft(IO.unit) { case (previous, settlement) =>
        for
          _ <- previous
          saved <- PostgresSupport.withConnectionIO(connectionSettings) { connection =>
            PostgresSupport.withTransactionIO(connection)(BattleResultTable.save(connection, settlement.result))
          }
          battleMail <- BattleFinishProjectionMailFactory.battleMail(saved)
          _ <- mailPublisher.publish(battleMail)
          _ <-
            if saved.ratingDelta.value != 0 then
              BattleFinishProjectionMailFactory.ratingMail(saved).flatMap(mailPublisher.publish)
            else IO.unit
        yield ()
      }
    }
}

private[battle] object BattleResultProjectionArtifactWriter {
  def apply(
    connectionSettings: PostgresConnectionSettings,
    mailPublisher: BattleMailPublisherPort
  ): BattleResultProjectionArtifactWriter =
    new BattleResultProjectionArtifactWriter(connectionSettings, mailPublisher)
}

private[battle] final class BattleReplayProjectionArtifactWriter(replayWriter: BattleReplayWriterPort)
    extends BattleFinishProjectionArtifactWriter {
  override def write(plan: BattleFinishProjectionPlan): IO[Unit] =
    plan.replay match {
      case Some(record) => replayWriter.saveReplay(record)
      case None         => IO.unit
    }
}

private[battle] object BattleReplayProjectionArtifactWriter {
  def apply(replayWriter: BattleReplayWriterPort): BattleReplayProjectionArtifactWriter =
    new BattleReplayProjectionArtifactWriter(replayWriter)
}
