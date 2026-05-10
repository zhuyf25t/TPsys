package slaydemo.backend.battle.objects

enum BattlePickupAvailability {
  case Available
  case Respawning(remainingMs: DurationMillis)
}

object BattlePickupAvailability {
  def availableFlag(value: BattlePickupAvailability): Boolean =
    value == BattlePickupAvailability.Available

  def respawnMs(value: BattlePickupAvailability): DurationMillis =
    value match {
      case BattlePickupAvailability.Available =>
        DurationMillis(0L)
      case BattlePickupAvailability.Respawning(remainingMs) =>
        DurationMillis(math.max(0L, remainingMs.value))
    }

  def respawning(remainingMs: DurationMillis): BattlePickupAvailability =
    if remainingMs.value <= 0L then BattlePickupAvailability.Available
    else BattlePickupAvailability.Respawning(DurationMillis(remainingMs.value))

  def fromAvailableFlag(available: Boolean, respawnMs: DurationMillis): BattlePickupAvailability =
    if available then BattlePickupAvailability.Available
    else BattlePickupAvailability.Respawning(DurationMillis(math.max(0L, respawnMs.value)))
}
