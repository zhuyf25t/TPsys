package services.battle.microservices.abilities.objects.skill

enum SkillOutcomeReason {
  case SkillNotOwned
  case Cooldown
  case MissingTarget
  case OutOfRange
  case InvalidTarget
  case NoDirection
  case Blocked
  case InsufficientStamina
}

object SkillOutcomeReason {
  def wireValue(value: SkillOutcomeReason): String =
    value match {
      case SkillOutcomeReason.SkillNotOwned       => "skill_not_owned"
      case SkillOutcomeReason.Cooldown            => "cooldown"
      case SkillOutcomeReason.MissingTarget       => "missing_target"
      case SkillOutcomeReason.OutOfRange          => "out_of_range"
      case SkillOutcomeReason.InvalidTarget       => "invalid_target"
      case SkillOutcomeReason.NoDirection         => "no_direction"
      case SkillOutcomeReason.Blocked             => "blocked"
      case SkillOutcomeReason.InsufficientStamina => "insufficient_stamina"
    }

  def fromWire(value: String): Option[SkillOutcomeReason] =
    value match {
      case "skill_not_owned"       => Some(SkillOutcomeReason.SkillNotOwned)
      case "cooldown"              => Some(SkillOutcomeReason.Cooldown)
      case "missing_target"        => Some(SkillOutcomeReason.MissingTarget)
      case "out_of_range"          => Some(SkillOutcomeReason.OutOfRange)
      case "invalid_target"        => Some(SkillOutcomeReason.InvalidTarget)
      case "no_direction"          => Some(SkillOutcomeReason.NoDirection)
      case "blocked"               => Some(SkillOutcomeReason.Blocked)
      case "insufficient_stamina"  => Some(SkillOutcomeReason.InsufficientStamina)
      case _                       => None
    }
}
