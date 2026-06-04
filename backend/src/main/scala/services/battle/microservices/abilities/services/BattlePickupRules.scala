package services.battle.microservices.abilities.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.runtime.services.BattleEventFactory.*
import services.battle.microservices.world.services.BattleGeometry.*
import services.battle.microservices.runtime.services.BattleTimeRules.*
import services.battle.microservices.abilities.objects.abilities.BattlePickupRuleConfig
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.runtime.objects.event.{BattleEventKind, BattleEventState}
import services.battle.objects.core.{BattleAggregateState, DurationMillis}
import services.battle.microservices.abilities.objects.pickup.{BattlePickupAvailability, BattlePickupState, PickupKind}
import services.battle.microservices.actors.objects.player.{BattlePlayerState, HitPoints}

private[battle] object BattlePickupRules {
  /** collectPickups: applies player pickup contact, healing/refill, respawn timer, and pickup events. */
  def collectPickups(
    state: BattleAggregateState,
    config: BattlePickupRuleConfig,
    retainedBattleEventCount: Int,
    equipOrRefillWeapon: (BattlePlayerState, WeaponKind) => IO[BattlePlayerState]
  ): IO[BattleAggregateState] =
    state.pickups.filter(_.available).foldLeft(IO.pure(state)) { (currentStateIO, pickup) =>
      currentStateIO.flatMap { currentState =>
        for
          contact <- pickupContactPlayer(currentState, pickup, config)
          nextState <- contact match {
            case None =>
              IO.pure(currentState)
            case Some(player) =>
              for
                updatedPlayer <- updatedPickupPlayer(player, pickup, config, equipOrRefillWeapon)
                eventKind <- pickupEventKind(pickup)
                eventMessage <- pickupEventMessage(updatedPlayer, pickup)
                eventId <- pickupEventId(eventKind, pickup, updatedPlayer, currentState.elapsedMs)
                event <- battleEvent(
                  currentState,
                  eventKind,
                  updatedPlayer,
                  updatedPlayer,
                  eventMessage,
                  Some(eventId)
                )
                consumedPickup <- consumePickup(pickup, config)
                stateWithPlayer <- replacePlayer(currentState, updatedPlayer)
                stateWithPickup <- replacePickup(stateWithPlayer, consumedPickup)
                retainedEvents <- retainRecentEvents(stateWithPickup.events :+ event, retainedBattleEventCount)
              yield stateWithPickup.copy(events = retainedEvents)
          }
        yield nextState
      }
    }

  private def pickupContactPlayer(
    state: BattleAggregateState,
    pickup: BattlePickupState,
    config: BattlePickupRuleConfig
  ): IO[Option[BattlePlayerState]] =
    state.players
      .filter(player => player.alive && !player.isBot)
      .traverse(player => distanceBetween(player.position, pickup.position).map(distance => player -> distance))
      .map { distances =>
        distances
          .filter { case (_, distance) => distance <= config.contactRadius.value }
          .minByOption { case (_, distance) => distance }
          .map { case (player, _) => player }
      }

  private def updatedPickupPlayer(
    player: BattlePlayerState,
    pickup: BattlePickupState,
    config: BattlePickupRuleConfig,
    equipOrRefillWeapon: (BattlePlayerState, WeaponKind) => IO[BattlePlayerState]
  ): IO[BattlePlayerState] =
    pickup.pickupKind match {
      case PickupKind.Medkit =>
        IO.pure(player.copy(hp = HitPoints(math.min(player.maxHp.value, player.hp.value + config.medkitHeal.value))))
      case PickupKind.Weapon =>
        pickup.weaponKind match {
          case Some(weaponKind) => equipOrRefillWeapon(player, weaponKind)
          case _                => IO.pure(player)
        }
    }

  private def pickupEventKind(pickup: BattlePickupState): IO[BattleEventKind] =
    IO.pure(if pickup.pickupKind == PickupKind.Medkit then BattleEventKind.Heal else BattleEventKind.Pickup)

  private def pickupEventMessage(
    player: BattlePlayerState,
    pickup: BattlePickupState
  ): IO[Option[String]] =
    pickup.pickupKind match {
      case PickupKind.Medkit =>
        IO.pure(None)
      case PickupKind.Weapon =>
        weaponPickupEventMessage(player, pickup).map(Some(_))
    }

  private def consumePickup(
    pickup: BattlePickupState,
    config: BattlePickupRuleConfig
  ): IO[BattlePickupState] =
    IO.pure(
      pickup.copy(
        pickupAvailability = BattlePickupAvailability.respawning(
          config.respawnDuration
        )
      )
    )

  private def replacePlayer(
    state: BattleAggregateState,
    player: BattlePlayerState
  ): IO[BattleAggregateState] =
    IO.pure(
      state.copy(
        players = state.players.map(existing =>
          if existing.playerId == player.playerId then player else existing
        )
      )
    )

  private def replacePickup(
    state: BattleAggregateState,
    pickup: BattlePickupState
  ): IO[BattleAggregateState] =
    IO.pure(
      state.copy(
        pickups = state.pickups.map(existing =>
          if existing.pickupId == pickup.pickupId then pickup else existing
        )
      )
    )

  private def retainRecentEvents(
    events: Vector[BattleEventState],
    retainedBattleEventCount: Int
  ): IO[Vector[BattleEventState]] =
    IO.pure(events.takeRight(retainedBattleEventCount))

  /** advancePickups: decreases pickup respawn timers until they become available again. */
  def advancePickups(state: BattleAggregateState, deltaMs: Long): IO[BattleAggregateState] =
    state.pickups.traverse { pickup =>
      if pickup.available then IO.pure(pickup)
      else {
        decrementLong(pickup.respawnMs.value, deltaMs).map { remaining =>
          pickup.copy(pickupAvailability = BattlePickupAvailability.respawning(DurationMillis(remaining)))
        }
      }
    }.map(pickups => state.copy(pickups = pickups))
}
