package services.battle.microservices.results.objects.result

import cats.effect.IO

import services.battle.objects.{BattleAggregateState, BattleArtifactStatus}

enum BattleFinishProjectionOutcome {
  case Projected
  case NotConfigured
  case ResultProjectedReplayFailed(message: String)
  case ResultFailedReplayProjected(message: String)
  case Failed(message: String)
}

object BattleFinishProjectionOutcome {
  def artifactStatus(value: BattleFinishProjectionOutcome): BattleArtifactStatus =
    value match {
      case BattleFinishProjectionOutcome.Projected                           => BattleArtifactStatus.Ready
      case BattleFinishProjectionOutcome.NotConfigured                       => BattleArtifactStatus.Pending
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(_)      => BattleArtifactStatus.ResultOnlyReady
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(_)      => BattleArtifactStatus.ReplayOnlyReady
      case BattleFinishProjectionOutcome.Failed(_)                           => BattleArtifactStatus.Pending
    }

  def failureMessage(value: BattleFinishProjectionOutcome): Option[String] =
    value match {
      case BattleFinishProjectionOutcome.ResultProjectedReplayFailed(message) => Some(message)
      case BattleFinishProjectionOutcome.ResultFailedReplayProjected(message) => Some(message)
      case BattleFinishProjectionOutcome.Failed(message)                      => Some(message)
      case BattleFinishProjectionOutcome.Projected | BattleFinishProjectionOutcome.NotConfigured =>
        None
    }
}

trait BattleFinishProjector {
  def project(state: BattleAggregateState): IO[BattleFinishProjectionOutcome]
}

object NoopBattleFinishProjector extends BattleFinishProjector {
  override def project(state: BattleAggregateState): IO[BattleFinishProjectionOutcome] =
    IO.pure(BattleFinishProjectionOutcome.NotConfigured)
}