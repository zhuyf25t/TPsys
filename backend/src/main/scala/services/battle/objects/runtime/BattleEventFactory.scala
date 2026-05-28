package services.battle.objects.runtime

import services.battle.objects.{BattleEventKind, WeaponKind}
import services.battle.objects.core.{BattleAggregateState, BattleEventId, ElapsedMillis}
import services.battle.objects.event.{BattleEventParticipant, BattleEventState}
import services.battle.objects.pickup.BattlePickupState
import services.battle.objects.player.BattlePlayerState

private[battle] object BattleEventFactory {
  /** 中文名：战斗事件（battleEvent）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环�?*/
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

  /** 中文名：拾取物事件标识（pickupEventId）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环�?*/
  def pickupEventId(
    eventKind: BattleEventKind,
    pickup: BattlePickupState,
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis
  ): BattleEventId = {
    val prefix = if eventKind == BattleEventKind.Heal then "heal" else "pickup"
    BattleEventId(s"$prefix-${elapsedMs.value}-${pickup.pickupId.value}-${player.playerId.value}")
  }

  /** 中文名：武器拾取物事件message（weaponPickupEventMessage）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环�?*/
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
