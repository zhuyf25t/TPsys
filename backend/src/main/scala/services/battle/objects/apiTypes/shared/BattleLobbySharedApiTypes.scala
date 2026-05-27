package services.battle.objects.apiTypes.shared

import io.circe.Encoder
import services.battle.objects.BattleMode
import services.battle.objects.queue.{
  BattleQueueParticipant,
  BattleSessionBootstrap,
  BattleSessionBootstrapSeat,
  BattleSessionDescriptor,
  BattleSessionRosterEntry
}

object BattleQueueParticipantResponse {
  given Encoder[BattleQueueParticipant] =
    Encoder
      .forProduct7("playerId", "handle", "joinedAt", "lastSeen", "rating", "avatar", "skin")(
        (value: BattleQueueParticipant) =>
          (
            value.playerId.value,
            value.handle.value,
            value.joinedAt.value,
            value.lastSeen.value,
            value.rating.map(_.value),
            value.avatar.map(_.value),
            value.skin.map(_.value)
          )
      )
      .mapJson(_.dropNullValues)
}

object BattleSessionRosterEntryResponse {
  given Encoder[BattleSessionRosterEntry] =
    Encoder
      .forProduct7("seat", "playerId", "handle", "joinedAt", "rating", "avatar", "skin")(
        (value: BattleSessionRosterEntry) =>
          (
            value.seat.value,
            value.playerId.value,
            value.handle.value,
            value.joinedAt.value,
            value.rating.map(_.value),
            value.avatar.map(_.value),
            value.skin.map(_.value)
          )
      )
      .mapJson(_.dropNullValues)
}

object BattleSessionBootstrapSeatResponse {
  given Encoder[BattleSessionBootstrapSeat] =
    Encoder
      .forProduct11(
        "seat",
        "playerId",
        "heroId",
        "handle",
        "displayName",
        "joinedAt",
        "isBot",
        "spawnPointIndex",
        "rating",
        "avatar",
        "skin"
      )(
        (value: BattleSessionBootstrapSeat) =>
          (
            value.seat.value,
            value.playerId.value,
            value.heroId.value,
            value.handle.value,
            value.displayName.value,
            value.joinedAt.value,
            value.isBot,
            value.spawnPointIndex.value,
            value.rating.map(_.value),
            value.avatar.map(_.value),
            value.skin.map(_.value)
          )
      )
      .mapJson(_.dropNullValues)
}

object BattleSessionBootstrapResponse {
  import BattleSessionBootstrapSeatResponse.given

  given Encoder[BattleSessionBootstrap] =
    Encoder.forProduct1("seats")(_.seats)
}

object BattleSessionDescriptorResponse {
  import BattleSessionRosterEntryResponse.given
  import BattleSessionBootstrapResponse.given

  given Encoder[BattleSessionDescriptor] =
    Encoder
      .forProduct10(
        "battleId",
        "modeId",
        "modeLabel",
        "mapId",
        "mapLabel",
        "startedAt",
        "serverTime",
        "roster",
        "capacity",
        "bootstrap"
      )((value: BattleSessionDescriptor) =>
        (
          value.battleId.value,
          BattleMode.wireValue(value.battleMode),
          BattleMode.modeLabel(value.battleMode).value,
          BattleMode.mapId(value.battleMode).value,
          BattleMode.mapLabel(value.battleMode).value,
          value.startedAt.value,
          value.serverTime.value,
          value.roster,
          value.capacity.value,
          value.bootstrap
        )
      )
      .mapJson(_.dropNullValues)
}
