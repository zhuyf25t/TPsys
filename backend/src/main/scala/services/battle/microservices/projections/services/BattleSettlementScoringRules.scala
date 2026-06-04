package services.battle.microservices.projections.services

import cats.effect.IO

import services.battle.microservices.actors.objects.player.{BattleSurvivalOutcome, Rating}
import services.battle.microservices.results.objects.result.{BattlePlacement, RatingDelta}

private[battle] object BattleSettlementScoringRules {
  val DefaultRating: Rating = Rating(1200)

  def placementScore(placement: Option[BattlePlacement], playerCount: Int): IO[Int] =
    IO.pure {
      placement match {
        case Some(value) =>
          val placementIndex = value.value - 1
          val maxPlayerIndex = math.max(playerCount - 1, 0)
          PlacementScores.lift(math.min(placementIndex, maxPlayerIndex)).getOrElse(0)
        case None =>
          0
      }
    }

  def ratingDelta(score: Int, placement: Option[BattlePlacement], survivalOutcome: BattleSurvivalOutcome): IO[RatingDelta] =
    IO.pure {
      val placementFactor = placement.fold(0)(value => math.max(-12, 16 - value.value * 4))
      val scoreFactor = math.min(6, math.floor(score.toDouble / 2.0).toInt)
      val aliveFactor =
        survivalOutcome match {
          case BattleSurvivalOutcome.Survived   => 2
          case BattleSurvivalOutcome.Eliminated => -1
        }
      RatingDelta(placementFactor + scoreFactor + aliveFactor)
    }

  private val PlacementScores: Vector[Int] = Vector(12, 9, 7, 5, 3, 1)
}
