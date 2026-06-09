package services.battle.microservices.queue.api.room

import io.circe.{Decoder, HCursor}

import services.battle.microservices.queue.objects.queue.{BattleRoomChatText, RealtimeRoomHeartbeatCommand, TicketId}
import services.battle.objects.core.RoomId
import services.identity.objects.PlayerHandle

object BattleRoomHeartbeatRequest {
  given Decoder[RealtimeRoomHeartbeatCommand] =
    Decoder.instance { cursor =>
      Right(
        RealtimeRoomHeartbeatCommand(
          roomId = optionalText(cursor, "roomId").map(RoomId.apply),
          ticketId = optionalText(cursor, "ticketId").map(TicketId.apply),
          handle = optionalText(cursor, "handle").flatMap(PlayerHandle.forLookup),
          startPaused = cursor.downField("startPaused").focus.flatMap(_.asBoolean),
          chatMessage = optionalText(cursor, "chatMessage").flatMap(BattleRoomChatText.fromWire)
        )
      )
    }

  private def optionalText(cursor: HCursor, field: String): Option[String] =
    cursor.downField(field).focus.flatMap(_.asString).flatMap(nonEmptyText)

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
