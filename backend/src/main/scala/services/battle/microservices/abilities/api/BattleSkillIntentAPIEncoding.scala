package services.battle.microservices.abilities.api

import services.battle.microservices.abilities.objects.skill.BattleCommandSkillIntents

object BattleSkillIntentAPIEncoding {
  def fromLegacyFlags(
    castDash: Option[Boolean],
    castBlink: Option[Boolean],
    castFreeze: Option[Boolean],
    castCritical: Option[Boolean]
  ): BattleCommandSkillIntents =
    BattleCommandSkillIntents.fromLegacyFlags(
      castDash = castDash.getOrElse(false),
      castBlink = castBlink.getOrElse(false),
      castFreeze = castFreeze.getOrElse(false),
      castCritical = castCritical.getOrElse(false)
    )
}
