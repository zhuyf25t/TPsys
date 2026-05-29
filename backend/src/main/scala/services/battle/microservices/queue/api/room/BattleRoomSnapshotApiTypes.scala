package services.battle.microservices.queue.api.room

import io.circe.{Decoder, DecodingFailure, Encoder}

import services.battle.objects.BattleMode
import services.battle.microservices.queue.api.shared.BattleQueueParticipantResponse.given
import services.battle.microservices.queue.api.shared.BattleSessionDescriptorResponse.given
import services.battle.microservices.queue.objects.queue.{BattleRoomSnapshotQuery, MatchmakingRoomPhase, RealtimeRoomSnapshot}
import services.battle.objects.core.RoomId

object BattleRoomSnapshotRequest {
  given Decoder[BattleRoomSnapshotQuery] =
    Decoder.instance { cursor =>
      cursor.get[String]("roomId").flatMap { value =>
        Option(value).map(_.trim).filter(_.nonEmpty) match {
          case Some(roomId) =>
            Right(BattleRoomSnapshotQuery(RoomId(roomId)))
          case None =>
            Left(DecodingFailure("roomId is required.", cursor.history))
        }
      }
    }
}

object RealtimeRoomSnapshotResponse {
  given Encoder[RealtimeRoomSnapshot] =
    Encoder
      .forProduct11(
        "roomId",
        "modeId",
        "modeLabel",
        "mapId",
        "mapLabel",
        "serverTime",
        "participants",
        "capacity",
        "phase",
        "finishedAt",
        "battleSession"
      )((value: RealtimeRoomSnapshot) =>
        (
          value.roomId.value,
          BattleMode.wireValue(value.battleMode),
          BattleMode.modeLabel(value.battleMode).value,
          BattleMode.mapId(value.battleMode).value,
          BattleMode.mapLabel(value.battleMode).value,
          value.serverTime.value,
          value.participants,
          value.capacity.value,
          MatchmakingRoomPhase.wireValue(value.phase),
          value.finishedAt.map(_.value),
          value.battleSession
        )
      )
      .mapJson(_.dropNullValues)
}
