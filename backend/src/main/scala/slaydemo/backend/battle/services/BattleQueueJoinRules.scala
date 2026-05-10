package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] final case class BattleQueueJoinDraft(
  room: QueueRoom,
  entry: QueueParticipantEntry,
  ticket: TicketRecord
)

private[services] object BattleQueueJoinRules {
  def normalizeCommand(command: BattleQueueJoinCommand): BattleQueueJoinCommand =
    command.copy(handle = BattleQueueParticipantRules.normalizeHandle(command.handle))

  def draft(
    command: BattleQueueJoinCommand,
    room: QueueRoom,
    ticketId: TicketId,
    playerId: PlayerId,
    now: EpochMillis
  ): BattleQueueJoinDraft = {
    val participant = BattleQueueParticipant(
      playerId = playerId,
      handle = command.handle,
      joinedAt = now,
      lastSeen = now,
      rating = command.rating,
      avatar = command.avatar.flatMap(BattleQueueParticipantRules.normalizeOptionalText),
      skin = command.skin.flatMap(BattleQueueParticipantRules.normalizeOptionalText)
    )
    val entry = QueueParticipantEntry(
      ticketId = ticketId,
      playerId = playerId,
      queueRequestId = command.queueRequestId,
      participant = participant
    )

    BattleQueueJoinDraft(
      room = room.copy(participants = room.participants :+ entry),
      entry = entry,
      ticket = TicketRecord(
        ticketId = ticketId,
        playerId = playerId,
        roomId = room.roomId,
        queueRequestId = command.queueRequestId
      )
    )
  }

  def queueRequestsAfterJoin(
    queueRequests: Map[QueueRequestId, TicketId],
    command: BattleQueueJoinCommand,
    ticketId: TicketId
  ): Map[QueueRequestId, TicketId] =
    command.queueRequestId match {
      case Some(id) => queueRequests.updated(id, ticketId)
      case None     => queueRequests
    }
}
