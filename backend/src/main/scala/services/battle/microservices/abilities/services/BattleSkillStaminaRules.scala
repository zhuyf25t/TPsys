package services.battle.microservices.abilities.services

import cats.effect.IO

import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.microservices.actors.objects.player.{BattlePlayerState, Stamina}

private[battle] object BattleSkillStaminaRules {
  enum SpendResult {
    case Spent(player: BattlePlayerState)
    case Insufficient(required: Stamina, available: Stamina)
  }

  private val NoCostRatio = 0.0
  private val StandardCostRatio = 0.2
  private val CriticalCostRatio = 0.4

  def spendIfAvailable(player: BattlePlayerState, skillKind: SkillKind): IO[SpendResult] =
    requiredStamina(skillKind, player.maxStamina).map { cost =>
      if player.stamina.value + 0.0001 >= cost.value then
        SpendResult.Spent(player.copy(stamina = Stamina(math.max(0.0, player.stamina.value - cost.value))))
      else
        SpendResult.Insufficient(cost, player.stamina)
    }

  def requiredStamina(skillKind: SkillKind, maxStamina: Stamina): IO[Stamina] =
    IO.pure(Stamina(math.max(0.0, maxStamina.value * staminaCostRatio(skillKind))))

  private def staminaCostRatio(skillKind: SkillKind): Double =
    skillKind match {
      case SkillKind.Dash     => StandardCostRatio
      case SkillKind.Freeze   => StandardCostRatio
      case SkillKind.Critical => CriticalCostRatio
      case SkillKind.Blink    => NoCostRatio
    }
}
