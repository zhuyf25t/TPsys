package services.battle.microservices.abilities.objects.skill

enum SkillKind {
  case Blink
  case Dash
  case Freeze
  case Critical
}

object SkillKind {
  def wireValue(value: SkillKind): String =
    value match {
      case SkillKind.Blink    => "Blink"
      case SkillKind.Dash     => "Dash"
      case SkillKind.Freeze   => "Freeze"
      case SkillKind.Critical => "Critical"
    }

  def fromWire(value: String): Option[SkillKind] =
    value match {
      case "Blink"    => Some(SkillKind.Blink)
      case "Dash"     => Some(SkillKind.Dash)
      case "Freeze"   => Some(SkillKind.Freeze)
      case "Critical" => Some(SkillKind.Critical)
      case _          => None
    }
}
