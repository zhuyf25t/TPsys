package services.battle.objects.apiTypes.queue

import io.circe.{Decoder, DecodingFailure, Encoder}

import services.battle.objects.{BattleMode, BattleQueueStatusQuery, MatchmakingRoomPhase}
import services.battle.objects.apiTypes.shared.BattleQueueParticipantResponse.given
import services.battle.objects.apiTypes.shared.BattleSessionDescriptorResponse.given
import services.battle.objects.core.{
  TicketId
}
import services.battle.objects.queue.BattleQueueSnapshot

object BattleQueueStatusRequest {
  given Decoder[BattleQueueStatusQuery] =
    Decoder.instance { cursor =>
      cursor.get[String]("ticketId").flatMap { value =>
        Option(value).map(_.trim).filter(_.nonEmpty) match {
          case Some(ticketId) =>
            Right(BattleQueueStatusQuery(TicketId(ticketId)))
          case None =>
            Left(DecodingFailure("ticketId is required.", cursor.history))
        }
      }
    }
}

object BattleQueueSnapshotResponse {
  given Encoder[BattleQueueSnapshot] =
    Encoder
      .forProduct17(
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
          value.finishedAt.map(_.value),
          value.battleSession
        )
      )
      .mapJson(_.dropNullValues)
}
