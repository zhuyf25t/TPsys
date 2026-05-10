package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.BattleEventFactory.*
import slaydemo.backend.battle.services.BattleGeometry.*
import slaydemo.backend.battle.services.BattleRetentionRules.*
import slaydemo.backend.battle.services.BattleTimeRules.*
import slaydemo.backend.battle.services.BattleWeaponRules.*

private[services] object BattlePickupRules {
  def collectPickups(state: BattleAggregateState): BattleAggregateState =
    state.pickups.filter(_.available).foldLeft(state) { (currentState, pickup) =>
      val contact = currentState.players
        .filter(player => player.alive && distanceBetween(player.position, pickup.position) <= BattlePickupCatalog.ContactRadius.value)
        .minByOption(player => distanceBetween(player.position, pickup.position))
      contact match {
        case None =>
          currentState
        case Some(player) =>
          val updatedPlayer = pickup.pickupKind match {
            case PickupKind.Medkit =>
              player.copy(hp = HitPoints(math.min(player.maxHp.value, player.hp.value + BattlePickupCatalog.MedkitHeal.value)))
            case PickupKind.Weapon =>
              pickup.weaponKind match {
                case Some(weaponKind) => equipOrRefillWeapon(player, weaponKind)
                case _                => player
              }
          }

          val consumedPickup = pickup.copy(
            pickupAvailability = BattlePickupAvailability.respawning(
              BattlePickupCatalog.RespawnDuration
            )
          )
          val eventKind =
            if pickup.pickupKind == PickupKind.Medkit then BattleEventKind.Heal else BattleEventKind.Pickup
          val eventMessage =
            pickup.pickupKind match {
              case PickupKind.Medkit =>
                None
              case PickupKind.Weapon =>
                Some(weaponPickupEventMessage(updatedPlayer, pickup))
            }

          currentState.copy(
            players = currentState.players.map(existing =>
              if existing.playerId == updatedPlayer.playerId then updatedPlayer else existing
            ),
            pickups = currentState.pickups.map(existing =>
              if existing.pickupId == consumedPickup.pickupId then consumedPickup else existing
            ),
            events = retainRecentEvents(currentState.events :+ battleEvent(
              currentState,
              eventKind,
              updatedPlayer,
              updatedPlayer,
              eventMessage,
              Some(pickupEventId(eventKind, pickup, updatedPlayer, currentState.elapsedMs))
            ))
          )
      }
    }

  def advancePickups(state: BattleAggregateState, deltaMs: Long): BattleAggregateState =
    state.copy(
      pickups = state.pickups.map { pickup =>
        if pickup.available then pickup
        else {
          val remaining = decrementLong(pickup.respawnMs.value, deltaMs)
          pickup.copy(pickupAvailability = BattlePickupAvailability.respawning(DurationMillis(remaining)))
        }
      }
    )
}
