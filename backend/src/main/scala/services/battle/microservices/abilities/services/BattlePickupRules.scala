package services.battle.microservices.abilities.services

import services.battle.microservices.runtime.services.BattleEventFactory.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.microservices.abilities.objects.abilities.BattlePickupRuleConfig
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.runtime.objects.event.BattleEventKind
import services.battle.objects.core.{BattleAggregateState, DurationMillis}
import services.battle.microservices.abilities.objects.pickup.PickupKind
import services.battle.microservices.abilities.objects.pickup.BattlePickupAvailability
import services.battle.microservices.actors.objects.player.{BattlePlayerState, HitPoints}

private[battle] object BattlePickupRules {
  /** collectPickups: applies player pickup contact, healing/refill, respawn timer, and pickup events. */
  def collectPickups(
    state: BattleAggregateState,
    config: BattlePickupRuleConfig,
    retainedBattleEventCount: Int,
    equipOrRefillWeapon: (BattlePlayerState, WeaponKind) => BattlePlayerState
  ): BattleAggregateState =
    state.pickups.filter(_.available).foldLeft(state) { (currentState, pickup) =>
      val contact = currentState.players
        .filter(player => player.alive && distanceBetween(player.position, pickup.position) <= config.contactRadius.value)
        .minByOption(player => distanceBetween(player.position, pickup.position))
      contact match {
        case None =>
          currentState
        case Some(player) =>
          val updatedPlayer = pickup.pickupKind match {
            case PickupKind.Medkit =>
              player.copy(hp = HitPoints(math.min(player.maxHp.value, player.hp.value + config.medkitHeal.value)))
            case PickupKind.Weapon =>
              pickup.weaponKind match {
                case Some(weaponKind) => equipOrRefillWeapon(player, weaponKind)
                case _                => player
              }
          }

          val consumedPickup = pickup.copy(
            pickupAvailability = BattlePickupAvailability.respawning(
              config.respawnDuration
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
            events = (currentState.events :+ battleEvent(
              currentState,
              eventKind,
              updatedPlayer,
              updatedPlayer,
              eventMessage,
              Some(pickupEventId(eventKind, pickup, updatedPlayer, currentState.elapsedMs))
            )).takeRight(retainedBattleEventCount)
          )
      }
    }

  /** advancePickups: decreases pickup respawn timers until they become available again. */
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
