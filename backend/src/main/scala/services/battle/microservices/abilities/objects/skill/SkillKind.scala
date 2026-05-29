package services.battle.microservices.abilities.objects.skill

enum SkillKind {
  case Blink
  case Dash
  case Freeze
}

object SkillKind {
  def wireValue(value: SkillKind): String =
    value match {
      case SkillKind.Blink  => "Blink"
      case SkillKind.Dash   => "Dash"
      case SkillKind.Freeze => "Freeze"
    }

  def fromWire(value: String): Option[SkillKind] =
    value match {
      case "Blink"  => Some(SkillKind.Blink)
      case "Dash"   => Some(SkillKind.Dash)
      case "Freeze" => Some(SkillKind.Freeze)
      case _        => None
    }
}
