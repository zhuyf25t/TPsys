package services.battle.microservices.combat.services

import cats.effect.IO

import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.combat.Damage

private[battle] object BattleCriticalDamageRules {
  private val CriticalDamageMultiplier = 1.5

  def projectileDamage(baseDamage: Damage, owner: Option[BattlePlayerState]): IO[Damage] =
    IO.pure {
      if owner.exists(hasActiveCritical) then
        Damage(math.ceil(baseDamage.value.toDouble * CriticalDamageMultiplier).toInt)
      else baseDamage
    }

  private def hasActiveCritical(player: BattlePlayerState): Boolean =
    player.skills.exists(skill => skill.skillKind == SkillKind.Critical && skill.activeMs.value > 0L)
}
