package services.battle.database.abilities

import services.battle.database.runtime.BattleEventFactory.*
import services.battle.database.world.BattleGeometry.*
import services.battle.database.runtime.BattleRetentionRules.*
import services.battle.database.runtime.BattleTimeRules.*
import services.battle.database.combat.BattleWeaponRules.*
import services.battle.objects.{BattleEventKind, PickupKind}
import services.battle.objects.core.{BattleAggregateState, DurationMillis, HitPoints}
import services.battle.objects.pickup.BattlePickupAvailability

private[services] object BattlePickupRules {
  /** 中文名：收集pickups（collectPickups）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互�?*/
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

  /** 中文名：推进pickups（advancePickups）。游戏职责：在后端能力域中管理技能、拾取物和减速场等玩法规则，驱动玩家战斗交互�?*/
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
