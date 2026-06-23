package services.battle.microservices.queue.api.queue

import io.circe.Encoder
import services.battle.objects.BattleMode
import services.battle.microservices.queue.api.shared.BattleQueueParticipantAPIEncoding.given
import services.battle.microservices.queue.api.shared.BattleRoomChatMessageAPIEncoding.given
import services.battle.microservices.queue.api.shared.BattleSessionDescriptorAPIEncoding.given
import services.battle.microservices.queue.objects.queue.{BattleQueueSnapshot, MatchmakingRoomPhase}

object BattleQueueSnapshotAPIEncoding {
  given Encoder[BattleQueueSnapshot] =
    Encoder
      .forProduct20(
        "ticketId",
        "playerId",
        "roomId",
        "modeId",
        "modeLabel",
        "mapId",
        "mapLabel",
        "createdAt",
        "startsAt",
        "deadline",
        "serverTime",
        "participants",
        "capacity",
        "durationMs",
        "phase",
        "startPaused",
        "pausedRemainingMs",
        "chatMessages",
        "finishedAt",
        "battleSession"
      )((value: BattleQueueSnapshot) =>
        (
          value.ticketId.value,
          value.playerId.value,
          value.roomId.value,
          BattleMode.wireValue(value.battleMode),
          BattleMode.modeLabel(value.battleMode).value,
          BattleMode.mapId(value.battleMode).value,
          BattleMode.mapLabel(value.battleMode).value,
          value.createdAt.value,
          value.startsAt.value,
          value.deadline.value,
          value.serverTime.value,
          value.participants,
          value.capacity.value,
          value.durationMs.value,
          MatchmakingRoomPhase.wireValue(value.phase),
          value.startPaused,
          value.pausedRemainingMs.map(_.value),
          value.chatMessages,
          value.finishedAt.map(_.value),
          value.battleSession
        )
      )
      .mapJson(_.dropNullValues)
}
