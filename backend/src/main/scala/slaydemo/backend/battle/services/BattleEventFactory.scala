package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleEventFactory {
  def battleEvent(
    state: BattleAggregateState,
    eventKind: BattleEventKind,
    source: BattlePlayerState,
    target: BattlePlayerState,
    messageOverride: Option[String] = None,
    eventIdOverride: Option[BattleEventId] = None
  ): BattleEventState =
    BattleEventState(
      eventId = eventIdOverride.getOrElse(
        BattleEventId(s"event-${eventKind.toString.toLowerCase}-${source.playerId.value}-${target.playerId.value}-${state.elapsedMs.value}-${state.events.size}")
      ),
      eventKind = eventKind,
      elapsedMs = state.elapsedMs,
      message = messageOverride.getOrElse(eventMessage(eventKind, source, target)),
      source = eventParticipant(source),
      target = eventParticipant(target)
    )

  def pickupEventId(
    eventKind: BattleEventKind,
    pickup: BattlePickupState,
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis
  ): BattleEventId = {
    val prefix = if eventKind == BattleEventKind.Heal then "heal" else "pickup"
    BattleEventId(s"$prefix-${elapsedMs.value}-${pickup.pickupId.value}-${player.playerId.value}")
  }

  def weaponPickupEventMessage(player: BattlePlayerState, pickup: BattlePickupState): String = {
    val weaponLabel = pickup.weaponKind.map(WeaponKind.wireValue).getOrElse("Weapon")
    s"${player.displayName.value} picked up $weaponLabel"
  }

  private def eventParticipant(player: BattlePlayerState): BattleEventParticipant =
    BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    )

  private def eventMessage(
    eventKind: BattleEventKind,
    source: BattlePlayerState,
    target: BattlePlayerState
  ): String =
    eventKind match {
      case BattleEventKind.Kill    => s"${source.displayName.value} eliminated ${target.displayName.value}"
      case BattleEventKind.Heal    => s"${target.displayName.value} picked up a medkit"
      case BattleEventKind.Pickup  => s"${target.displayName.value} picked up supplies"
      case BattleEventKind.Respawn => s"${target.displayName.value} respawned"
    }
}
