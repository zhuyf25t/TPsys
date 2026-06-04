package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.objects.core.{BattleAggregateState, ElapsedMillis}
import services.battle.microservices.runtime.objects.event.{BattleEventId, BattleEventKind, BattleEventParticipant, BattleEventState}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.actors.objects.player.BattlePlayerState

private[battle] object BattleEventFactory {
  def battleEvent(
    state: BattleAggregateState,
    eventKind: BattleEventKind,
    source: BattlePlayerState,
    target: BattlePlayerState,
    messageOverride: Option[String] = None,
    eventIdOverride: Option[BattleEventId] = None
  ): IO[BattleEventState] =
    for
      message <- messageOverride match {
        case Some(value) => IO.pure(value)
        case None        => eventMessage(eventKind, source, target)
      }
      sourceParticipant <- eventParticipant(source)
      targetParticipant <- eventParticipant(target)
    yield BattleEventState(
      eventId = eventIdOverride.getOrElse(
        BattleEventId(s"event-${eventKind.toString.toLowerCase}-${source.playerId.value}-${target.playerId.value}-${state.elapsedMs.value}-${state.events.size}")
      ),
      eventKind = eventKind,
      elapsedMs = state.elapsedMs,
      message = message,
      source = sourceParticipant,
      target = targetParticipant
    )

  def pickupEventId(
    eventKind: BattleEventKind,
    pickup: BattlePickupState,
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis
  ): IO[BattleEventId] =
    IO.pure {
      val prefix = if eventKind == BattleEventKind.Heal then "heal" else "pickup"
      BattleEventId(s"$prefix-${elapsedMs.value}-${pickup.pickupId.value}-${player.playerId.value}")
    }

  def weaponPickupEventMessage(player: BattlePlayerState, pickup: BattlePickupState): IO[String] =
    IO.pure {
      val weaponLabel = pickup.weaponKind.map(WeaponKind.wireValue).getOrElse("Weapon")
      s"${player.displayName.value} picked up $weaponLabel"
    }

  private def eventParticipant(player: BattlePlayerState): IO[BattleEventParticipant] =
    IO.pure(BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    ))

  private def eventMessage(
    eventKind: BattleEventKind,
    source: BattlePlayerState,
    target: BattlePlayerState
  ): IO[String] =
    IO.pure {
      eventKind match {
        case BattleEventKind.Kill    => s"${source.displayName.value} eliminated ${target.displayName.value}"
        case BattleEventKind.Heal    => s"${target.displayName.value} picked up a medkit"
        case BattleEventKind.Pickup  => s"${target.displayName.value} picked up supplies"
        case BattleEventKind.Respawn => s"${target.displayName.value} respawned"
      }
    }
}
