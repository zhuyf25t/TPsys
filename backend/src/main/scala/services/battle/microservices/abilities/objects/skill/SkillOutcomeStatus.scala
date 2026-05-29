package services.battle.microservices.abilities.objects.skill

enum SkillOutcomeStatus {
  case Applied
  case Noop
}

object SkillOutcomeStatus {
  def wireValue(value: SkillOutcomeStatus): String =
    value match {
      case SkillOutcomeStatus.Applied => "applied"
      case SkillOutcomeStatus.Noop    => "noop"
    }

  def fromWire(value: String): Option[SkillOutcomeStatus] =
    value match {
      case "applied" => Some(SkillOutcomeStatus.Applied)
      case "noop"    => Some(SkillOutcomeStatus.Noop)
      case _         => None
    }
}
